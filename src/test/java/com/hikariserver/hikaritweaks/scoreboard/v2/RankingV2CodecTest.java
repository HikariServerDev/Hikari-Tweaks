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

// docs/ranking-v2-protocol.md §2（ワイヤフォーマット）と §5.3（壊れたパケット）のテスト。
class RankingV2CodecTest {

    private static final UUID ID_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ID_B = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    // ── 往復 ─────────────────────────────────────────────────

    @Test
    @DisplayName("HIDE は 1 バイトちょうどで往復する")
    void hideRoundTrip() {
        byte[] bytes = RankingV2Codec.encode(RankingV2Message.hide());
        // §2: HIDE は 1 バイトで完結する。§3.9: 末尾に余分なバイトを書かない。
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
        // §3.5: serverTotal < 0 は「Total 行を出すな」のセンチネル。符号を潰さないこと。
        assertEquals(-1L, decoded.serverTotal());
        assertEquals(original.rows(), decoded.rows());
        assertEquals(List.of(ID_B), decoded.removals());
    }

    @Test
    @DisplayName("値 0 の行も 負の serverTotal も往復で保存される")
    void zeroAndSentinelSurviveRoundTrip() {
        // §3.7: 値 0 は PING / HEALTH / PLAYER_LEVEL では正当な値。
        // コーデックが「0 だから省く」ような最適化をしていないことを確かめる。
        RankingV2Message original = RankingV2Message.snapshot(
                0, "Ping", -1L, List.of(new RankingRow(ID_A, "Alice", 0L)));
        RankingV2Message decoded = RankingV2Codec.decode(RankingV2Codec.encode(original));
        assertEquals(1, decoded.rows().size());
        assertEquals(0L, decoded.rows().get(0).value());
        assertEquals(-1L, decoded.serverTotal());
    }

    // ── バイト配置の固定 ─────────────────────────────────────

    @Test
    @DisplayName("SNAPSHOT のバイト配置が §2 のとおりである")
    void snapshotByteLayoutIsExact() {
        // 相手（HikariScoreBoard）は別実装なので、往復テストだけでは
        // 「両側が同じ間違いをしている」ケースを検出できない。
        // ここで期待バイト列を直に書いて配置を固定する。
        byte[] actual = RankingV2Codec.encode(RankingV2Message.snapshot(
                1, "T", 2L, List.of(new RankingRow(ID_A, "Ab", 3L))));

        List<Byte> expected = new ArrayList<>();
        expected.add((byte) 0x01);                 // type = SNAPSHOT
        expected.add((byte) 0x01);                 // varint boardId = 1
        expected.add((byte) 0x01);                 // string 長 = 1 バイト
        for (byte b : "T".getBytes(StandardCharsets.UTF_8)) expected.add(b);
        addLong(expected, 2L);                     // serverTotal
        expected.add((byte) 0x01);                 // varint count = 1
        addLong(expected, ID_A.getMostSignificantBits());   // uuid は msb -> lsb の 16 バイト
        addLong(expected, ID_A.getLeastSignificantBits());
        expected.add((byte) 0x02);                 // string 長 = 2 バイト
        for (byte b : "Ab".getBytes(StandardCharsets.UTF_8)) expected.add(b);
        addLong(expected, 3L);                     // value

        assertArrayEquals(toArray(expected), actual);
    }

    @Test
    @DisplayName("varint は MC と同じ 7bit/バイトの可変長で書かれる")
    void varIntMatchesMinecraftEncoding() {
        // boardId = 300 -> 0xAC 0x02
        byte[] bytes = RankingV2Codec.encode(
                RankingV2Message.delta(300, 0L, List.of(), List.of()));
        assertEquals((byte) 0x02, bytes[0]);       // type = DELTA
        assertEquals((byte) 0xAC, bytes[1]);
        assertEquals((byte) 0x02, bytes[2]);
        assertEquals(300, RankingV2Codec.decode(bytes).boardId());
    }

    @Test
    @DisplayName("マルチバイト文字の name / title が UTF-8 バイト長で書かれる")
    void utf8StringsRoundTrip() {
        String name = "あいう";  // UTF-8 で 9 バイト
        RankingV2Message decoded = RankingV2Codec.decode(RankingV2Codec.encode(
                RankingV2Message.snapshot(0, "タイトル", 0L,
                        List.of(new RankingRow(ID_A, name, 1L)))));
        assertEquals("タイトル", decoded.title());
        assertEquals(name, decoded.rows().get(0).name());
    }

    // ── 壊れたパケット（§5.3）─────────────────────────────────

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
            // 受信側（RankingV2Client）と同じ形の try/catch で包む。
            // ここで何も投げなければ netty スレッドで切断されることは無い。
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
        // SNAPSHOT / boardId=0 / title="" / serverTotal=0 / count = 0x7FFFFFFF
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x01);
        bytes.add((byte) 0x00);                    // boardId
        bytes.add((byte) 0x00);                    // title 長 0
        addLong(bytes, 0L);                        // serverTotal
        // varint 0x7FFFFFFF
        bytes.add((byte) 0xFF); bytes.add((byte) 0xFF);
        bytes.add((byte) 0xFF); bytes.add((byte) 0xFF); bytes.add((byte) 0x07);

        // OOM でも例外漏れでもなく、単に「打ち切り」として捨てられること
        assertThrows(RankingV2Codec.MalformedPacketException.class,
                () -> RankingV2Codec.decode(toArray(bytes)));
    }

    @Test
    @DisplayName("末尾に余分なバイトがあっても読めるところまで読む")
    void trailingBytesAreIgnored() {
        // §3.9 は「書く側が余分なバイトを足すな」という規約。
        // 読む側が弾くと、将来サーバーがフィールドを足したときに
        // 旧クライアントがパケットごと落としてしまう。
        byte[] full = RankingV2Codec.encode(RankingV2Message.hide());
        byte[] padded = new byte[] { full[0], 0x11, 0x22, 0x33 };
        assertEquals(RankingV2Message.Type.HIDE, RankingV2Codec.decode(padded).type());
    }

    // ── ヘルパー ─────────────────────────────────────────────

    // RankingV2Client の受信ハンドラと同じ形（例外を握り潰す）を再現する
    private static void receiveLikeClient(byte[] payload) {
        try {
            RankingV2Codec.decode(payload);
        } catch (Exception ignored) {
            // §5.3: ログに落として捨てるだけ
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
