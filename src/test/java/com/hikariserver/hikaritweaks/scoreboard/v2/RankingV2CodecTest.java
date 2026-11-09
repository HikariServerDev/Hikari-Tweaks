package com.hikariserver.hikaritweaks.scoreboard.v2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingV2CodecTest {
    private static final UUID ID_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ID_B = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Test
    @DisplayName("HIDE は 1 バイトちょうどで往復する")
    void hideRoundTrip() {
        byte[] bytes = RankingV2Codec.encode(RankingV2Message.hide());
        assertArrayEquals(new byte[] { 0x00 }, bytes);

        RankingV2Message decoded = RankingV2Codec.decode(bytes);
        assertEquals(RankingV2Message.Type.HIDE, decoded.type());
        assertTrue(decoded.rows().isEmpty());
        assertTrue(decoded.removals().isEmpty());
    }

    @Test
    @DisplayName("SNAPSHOT が往復する")
    void snapshotRoundTrip() {
        RankingV2Message original = RankingV2Message.snapshot(
                7, "Blocks Mined", 123456789L,
                List.of(new RankingRow(ID_A, "Alice", 100L),
                        new RankingRow(ID_B, "Bob", 0L)));

        RankingV2Message decoded = RankingV2Codec.decode(RankingV2Codec.encode(original));

        assertEquals(RankingV2Message.Type.SNAPSHOT, decoded.type());
        assertEquals(7, decoded.boardId());
        assertEquals("Blocks Mined", decoded.title());
        assertEquals(123456789L, decoded.serverTotal());
        assertEquals(original.rows(), decoded.rows());
    }

    @Test
    @DisplayName("DELTA が往復する（upsert と remove の両方）")
    void deltaRoundTrip() {
        RankingV2Message original = RankingV2Message.delta(
                42, -1L,
                List.of(new RankingRow(ID_A, "Alice", 5L)),
                List.of(ID_B));

        RankingV2Message decoded = RankingV2Codec.decode(RankingV2Codec.encode(original));

        assertEquals(RankingV2Message.Type.DELTA, decoded.type());
        assertEquals(42, decoded.boardId());
        assertEquals(-1L, decoded.serverTotal());
        assertEquals(original.rows(), decoded.rows());
        assertEquals(List.of(ID_B), decoded.removals());
    }

    @Test
    @DisplayName("値 0 の行も 負の serverTotal も往復で保存される")
    void zeroAndSentinelSurviveRoundTrip() {
        RankingV2Message original = RankingV2Message.snapshot(
                0, "Ping", -1L, List.of(new RankingRow(ID_A, "Alice", 0L)));
        RankingV2Message decoded = RankingV2Codec.decode(RankingV2Codec.encode(original));
        assertEquals(1, decoded.rows().size());
        assertEquals(0L, decoded.rows().get(0).value());
        assertEquals(-1L, decoded.serverTotal());
    }

    @Test
    @DisplayName("SNAPSHOT のバイト配置が §2 のとおりである")
    void snapshotByteLayoutIsExact() {
        byte[] actual = RankingV2Codec.encode(RankingV2Message.snapshot(
                1, "T", 2L, List.of(new RankingRow(ID_A, "Ab", 3L))));

        List<Byte> expected = new ArrayList<>();
        expected.add((byte) 0x01);
        expected.add((byte) 0x01);
        expected.add((byte) 0x01);
        for (byte b : "T".getBytes(StandardCharsets.UTF_8)) expected.add(b);
        addLong(expected, 2L);
        expected.add((byte) 0x01);
        addLong(expected, ID_A.getMostSignificantBits());
        addLong(expected, ID_A.getLeastSignificantBits());
        expected.add((byte) 0x02);
        for (byte b : "Ab".getBytes(StandardCharsets.UTF_8)) expected.add(b);
        addLong(expected, 3L);
        expected.add((byte) 0x00);

        assertArrayEquals(toArray(expected), actual);
    }

    @Test
    @DisplayName("varint は MC と同じ 7bit/バイトの可変長で書かれる")
    void varIntMatchesMinecraftEncoding() {
        byte[] bytes = RankingV2Codec.encode(
                RankingV2Message.delta(300, 0L, List.of(), List.of()));
        assertEquals((byte) 0x02, bytes[0]);
        assertEquals((byte) 0xAC, bytes[1]);
        assertEquals((byte) 0x02, bytes[2]);
        assertEquals(300, RankingV2Codec.decode(bytes).boardId());
    }

    @Test
    @DisplayName("マルチバイト文字の name / title が UTF-8 バイト長で書かれる")
    void utf8StringsRoundTrip() {
        String name = "あいう";
        RankingV2Message decoded = RankingV2Codec.decode(RankingV2Codec.encode(
                RankingV2Message.snapshot(0, "タイトル", 0L,
                        List.of(new RankingRow(ID_A, name, 1L)))));
        assertEquals("タイトル", decoded.title());
        assertEquals(name, decoded.rows().get(0).name());
    }

    @Test
    @DisplayName("空のペイロードは MalformedPacketException になる")
    void emptyPayloadIsMalformed() {
        assertThrows(RankingV2Codec.MalformedPacketException.class,
                () -> RankingV2Codec.decode(new byte[0]));
    }

    @Test
    @DisplayName("知らない種別バイトは MalformedPacketException になる")
    void unknownTypeIsMalformed() {
        assertThrows(RankingV2Codec.MalformedPacketException.class,
                () -> RankingV2Codec.decode(new byte[] { 0x7F }));
    }

    @Test
    @DisplayName("どこで切られても受信側へ例外が漏れない")
    void everyTruncationIsCaught() {
        byte[] full = RankingV2Codec.encode(RankingV2Message.delta(
                3, 10L,
                List.of(new RankingRow(ID_A, "Alice", 1L), new RankingRow(ID_B, "Bob", 2L)),
                List.of(ID_A)));

        for (int len = 0; len < full.length; len++) {
            byte[] cut = new byte[len];
            System.arraycopy(full, 0, cut, 0, len);
            int at = len;
            assertDoesNotThrow(() -> receiveLikeClient(cut), "truncated at " + at);
        }
    }

    @Test
    @DisplayName("ランダムなゴミバイトでも受信側へ例外が漏れない")
    void randomGarbageIsCaught() {
        Random random = new Random(20240831L);
        for (int i = 0; i < 5000; i++) {
            byte[] garbage = new byte[random.nextInt(64)];
            random.nextBytes(garbage);
            assertDoesNotThrow(() -> receiveLikeClient(garbage));
        }
    }

    @Test
    @DisplayName("巨大な count を名乗るパケットは確保せず打ち切られる")
    void hugeCountDoesNotAllocate() {
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x01);
        bytes.add((byte) 0x00);
        bytes.add((byte) 0x00);
        addLong(bytes, 0L);
        bytes.add((byte) 0xFF); bytes.add((byte) 0xFF);
        bytes.add((byte) 0xFF); bytes.add((byte) 0xFF); bytes.add((byte) 0x07);

        assertThrows(RankingV2Codec.MalformedPacketException.class,
                () -> RankingV2Codec.decode(toArray(bytes)));
    }

    @Test
    @DisplayName("末尾に余分なバイトがあっても読めるところまで読む")
    void trailingBytesAreIgnored() {
        byte[] full = RankingV2Codec.encode(RankingV2Message.hide());
        byte[] padded = new byte[] { full[0], 0x11, 0x22, 0x33 };
        assertEquals(RankingV2Message.Type.HIDE, RankingV2Codec.decode(padded).type());
    }

    private static void receiveLikeClient(byte[] payload) {
        try {
            RankingV2Codec.decode(payload);
        } catch (Exception ignored) {
        }
    }

    private static void addLong(List<Byte> out, long value) {
        for (int i = 7; i >= 0; i--) out.add((byte) (value >>> (i * 8)));
    }

    private static byte[] toArray(List<Byte> list) {
        byte[] out = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i);
        return out;
    }
}
