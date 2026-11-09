package com.hikariserver.hikaritweaks.scoreboard.v1;

import com.hikariserver.hikaritweaks.scoreboard.PlayerListEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardV1CodecTest {
    @Test
    @DisplayName("非表示指示は 1 バイトちょうどで往復する")
    void hiddenRoundTrip() {
        byte[] bytes = ScoreboardV1Codec.encodeRanking(RankingV1Data.hide());
        assertArrayEquals(new byte[] { 0x01 }, bytes);

        RankingV1Data decoded = ScoreboardV1Codec.decodeRanking(bytes);
        assertTrue(decoded.hidden());
    }

    @Test
    @DisplayName("ranking_data が往復する（top と full が別のリスト）")
    void rankingRoundTrip() {
        RankingV1Data original = RankingV1Data.of(
                "Blocks Mined",
                List.of(new RankingV1Entry("Alice", 100L)),
                List.of(new RankingV1Entry("Alice", 100L), new RankingV1Entry("Bob", 0L)),
                123456789L, 2, 42L, "Bob");

        RankingV1Data decoded = ScoreboardV1Codec.decodeRanking(
                ScoreboardV1Codec.encodeRanking(original));

        assertFalse(decoded.hidden());
        assertEquals("Blocks Mined", decoded.title());
        assertEquals(original.top(), decoded.top());
        assertEquals(original.full(), decoded.full());
        assertEquals(123456789L, decoded.serverTotal());
        assertEquals(2, decoded.selfRank());
        assertEquals(42L, decoded.selfValue());
        assertEquals("Bob", decoded.selfName());
    }

    @Test
    @DisplayName("値 0 の行も 負の serverTotal も往復で保存される")
    void zeroAndSentinelSurviveRoundTrip() {
        RankingV1Data original = RankingV1Data.of(
                "Ping",
                List.of(new RankingV1Entry("Alice", 0L)),
                List.of(new RankingV1Entry("Alice", 0L)),
                -1L, 1, 0L, "Alice");

        RankingV1Data decoded = ScoreboardV1Codec.decodeRanking(
                ScoreboardV1Codec.encodeRanking(original));

        assertEquals(0L, decoded.full().get(0).value());
        assertEquals(-1L, decoded.serverTotal());
        assertEquals(0L, decoded.selfValue());
    }

    @Test
    @DisplayName("マルチバイト文字の title / name が UTF-8 バイト長で往復する")
    void utf8StringsRoundTrip() {
        RankingV1Data decoded = ScoreboardV1Codec.decodeRanking(
                ScoreboardV1Codec.encodeRanking(RankingV1Data.of(
                        "タイトル",
                        List.of(new RankingV1Entry("あいう", 1L)),
                        List.of(new RankingV1Entry("あいう", 1L)),
                        0L, 1, 1L, "あいう")));

        assertEquals("タイトル", decoded.title());
        assertEquals("あいう", decoded.full().get(0).name());
    }

    @Test
    @DisplayName("player_list_response が往復する")
    void playerListRoundTrip() {
        List<PlayerListEntry> original = List.of(
                new PlayerListEntry("00000000-0000-0000-0000-000000000001", "Alice", false, false),
                new PlayerListEntry("00000000-0000-0000-0000-000000000002", "BotBob", true, true));

        List<PlayerListEntry> decoded = ScoreboardV1Codec.decodePlayerList(
                ScoreboardV1Codec.encodePlayerList(original));

        assertEquals(original, decoded);
    }

    @Test
    @DisplayName("ranking_data のバイト配置が仕様どおりである")
    void rankingByteLayoutIsExact() {
        byte[] actual = ScoreboardV1Codec.encodeRanking(RankingV1Data.of(
                "T",
                List.of(new RankingV1Entry("Ab", 3L)),
                List.of(new RankingV1Entry("Ab", 3L)),
                2L, 1, 3L, "Ab"));

        List<Byte> expected = new ArrayList<>();
        expected.add((byte) 0x00);
        expected.add((byte) 0x01);
        addUtf8(expected, "T");
        expected.add((byte) 0x01);
        expected.add((byte) 0x02);
        addUtf8(expected, "Ab");
        addLong(expected, 3L);
        addLong(expected, 2L);
        expected.add((byte) 0x01);
        addLong(expected, 3L);
        expected.add((byte) 0x02);
        addUtf8(expected, "Ab");
        expected.add((byte) 0x01);
        expected.add((byte) 0x02);
        addUtf8(expected, "Ab");
        addLong(expected, 3L);

        assertArrayEquals(toArray(expected), actual);
    }

    @Test
    @DisplayName("varint は MC と同じ 7bit/バイトの可変長で書かれる")
    void varIntMatchesMinecraftEncoding() {
        byte[] bytes = ScoreboardV1Codec.encodeRanking(RankingV1Data.of(
                "", List.of(), List.of(), 0L, 300, 0L, ""));

        assertEquals((byte) 0xAC, bytes[11]);
        assertEquals((byte) 0x02, bytes[12]);
        assertEquals(300, ScoreboardV1Codec.decodeRanking(bytes).selfRank());
    }

    @Test
    @DisplayName("full リストを送ってこない旧サーバーでは full = top になる")
    void missingFullListFallsBackToTop() {
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x00);
        bytes.add((byte) 0x01);
        addUtf8(bytes, "T");
        bytes.add((byte) 0x01);
        bytes.add((byte) 0x02);
        addUtf8(bytes, "Ab");
        addLong(bytes, 7L);
        addLong(bytes, 9L);
        bytes.add((byte) 0x01);
        addLong(bytes, 7L);
        bytes.add((byte) 0x02);
        addUtf8(bytes, "Ab");

        RankingV1Data decoded = ScoreboardV1Codec.decodeRanking(toArray(bytes));
        assertEquals(decoded.top(), decoded.full());
        assertEquals(1, decoded.full().size());
        assertEquals(7L, decoded.full().get(0).value());
    }

    @Test
    @DisplayName("full リストの後ろに余分なバイトがあっても読めるところまで読む")
    void trailingBytesAreIgnored() {
        byte[] full = ScoreboardV1Codec.encodeRanking(RankingV1Data.of(
                "T", List.of(), List.of(), 1L, 1, 1L, "Me"));
        byte[] padded = new byte[full.length + 3];
        System.arraycopy(full, 0, padded, 0, full.length);
        padded[full.length]     = 0x11;
        padded[full.length + 1] = 0x22;
        padded[full.length + 2] = 0x33;

        RankingV1Data decoded = ScoreboardV1Codec.decodeRanking(padded);
        assertEquals("T", decoded.title());
        assertEquals("Me", decoded.selfName());
    }

    @Test
    @DisplayName("空のペイロードは MalformedPacketException になる")
    void emptyPayloadIsMalformed() {
        assertThrows(ScoreboardV1Codec.MalformedPacketException.class,
                () -> ScoreboardV1Codec.decodeRanking(new byte[0]));
        assertThrows(ScoreboardV1Codec.MalformedPacketException.class,
                () -> ScoreboardV1Codec.decodePlayerList(new byte[0]));
    }

    @Test
    @DisplayName("ranking_data がどこで切られても受信側へ例外が漏れない")
    void everyRankingTruncationIsCaught() {
        byte[] full = ScoreboardV1Codec.encodeRanking(RankingV1Data.of(
                "Blocks Mined",
                List.of(new RankingV1Entry("Alice", 1L)),
                List.of(new RankingV1Entry("Alice", 1L), new RankingV1Entry("Bob", 2L)),
                10L, 1, 1L, "Alice"));

        for (int len = 0; len < full.length; len++) {
            byte[] cut = new byte[len];
            System.arraycopy(full, 0, cut, 0, len);
            int at = len;
            assertDoesNotThrow(() -> receiveLikeClient(cut, true), "truncated at " + at);
        }
    }

    @Test
    @DisplayName("player_list_response がどこで切られても受信側へ例外が漏れない")
    void everyPlayerListTruncationIsCaught() {
        byte[] full = ScoreboardV1Codec.encodePlayerList(List.of(
                new PlayerListEntry("00000000-0000-0000-0000-000000000001", "Alice", false, false),
                new PlayerListEntry("00000000-0000-0000-0000-000000000002", "Bob", true, true)));

        for (int len = 0; len < full.length; len++) {
            byte[] cut = new byte[len];
            System.arraycopy(full, 0, cut, 0, len);
            int at = len;
            assertDoesNotThrow(() -> receiveLikeClient(cut, false), "truncated at " + at);
        }
    }

    @Test
    @DisplayName("ランダムなゴミバイトでも受信側へ例外が漏れない")
    void randomGarbageIsCaught() {
        Random random = new Random(20260901L);
        for (int i = 0; i < 5000; i++) {
            byte[] garbage = new byte[random.nextInt(64)];
            random.nextBytes(garbage);
            assertDoesNotThrow(() -> receiveLikeClient(garbage, true));
            assertDoesNotThrow(() -> receiveLikeClient(garbage, false));
        }
    }

    @Test
    @DisplayName("巨大な count を名乗る ranking_data は確保せず打ち切られる")
    void hugeRankingCountDoesNotAllocate() {
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x00);
        bytes.add((byte) 0x00);
        addVarInt(bytes, 1_900_000_000);
        assertThrows(ScoreboardV1Codec.MalformedPacketException.class,
                () -> ScoreboardV1Codec.decodeRanking(toArray(bytes)));
    }

    @Test
    @DisplayName("巨大な fullCount を名乗る ranking_data は確保せず打ち切られる")
    void hugeFullCountDoesNotAllocate() {
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x00);
        bytes.add((byte) 0x00);
        bytes.add((byte) 0x00);
        addLong(bytes, 0L);
        bytes.add((byte) 0x00);
        addLong(bytes, 0L);
        bytes.add((byte) 0x00);
        addVarInt(bytes, 1_900_000_000);
        assertThrows(ScoreboardV1Codec.MalformedPacketException.class,
                () -> ScoreboardV1Codec.decodeRanking(toArray(bytes)));
    }

    @Test
    @DisplayName("巨大な count を名乗る player_list_response は確保せず打ち切られる")
    void hugePlayerCountDoesNotAllocate() {
        List<Byte> bytes = new ArrayList<>();
        addVarInt(bytes, 1_900_000_000);
        assertThrows(ScoreboardV1Codec.MalformedPacketException.class,
                () -> ScoreboardV1Codec.decodePlayerList(toArray(bytes)));
    }

    @Test
    @DisplayName("上限を超える長さを名乗る string は MalformedPacketException になる")
    void oversizedStringIsMalformed() {
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x00);
        addVarInt(bytes, 2000);
        assertThrows(ScoreboardV1Codec.MalformedPacketException.class,
                () -> ScoreboardV1Codec.decodeRanking(toArray(bytes)));
    }

    @Test
    @DisplayName("6 バイト以上の varint は MalformedPacketException になる")
    void oversizedVarIntIsMalformed() {
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x00);
        bytes.add((byte) 0x00);
        for (int i = 0; i < 6; i++) bytes.add((byte) 0xFF);
        assertThrows(ScoreboardV1Codec.MalformedPacketException.class,
                () -> ScoreboardV1Codec.decodeRanking(toArray(bytes)));
    }

    @Test
    @DisplayName("full リストは top と別インスタンスとして読まれる")
    void fullListIsIndependentWhenSent() {
        RankingV1Data decoded = ScoreboardV1Codec.decodeRanking(
                ScoreboardV1Codec.encodeRanking(RankingV1Data.of(
                        "T",
                        List.of(new RankingV1Entry("Alice", 1L)),
                        List.of(new RankingV1Entry("Alice", 1L), new RankingV1Entry("Bob", 2L)),
                        0L, 1, 1L, "Alice")));

        assertEquals(1, decoded.top().size());
        assertEquals(2, decoded.full().size());
        assertNotSame(decoded.top(), decoded.full());
    }

    private static void receiveLikeClient(byte[] payload, boolean ranking) {
        try {
            if (ranking) {
                ScoreboardV1Codec.decodeRanking(payload);
            } else {
                ScoreboardV1Codec.decodePlayerList(payload);
            }
        } catch (Exception ignored) {
        }
    }

    private static void addUtf8(List<Byte> out, String s) {
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) out.add(b);
    }

    private static void addLong(List<Byte> out, long value) {
        for (int i = 7; i >= 0; i--) out.add((byte) (value >>> (i * 8)));
    }

    private static void addVarInt(List<Byte> out, int value) {
        while ((value & ~0x7F) != 0) {
            out.add((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.add((byte) value);
    }

    private static byte[] toArray(List<Byte> list) {
        byte[] out = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i);
        return out;
    }
}
