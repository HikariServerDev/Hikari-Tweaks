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

// v1 チャンネル（ranking_data / player_list_response）のワイヤ仕様と
// 「壊れたパケットで切断されないこと」のテスト。
//
// v1 の受信経路は netty のイベントループ（MC 1.20.5 未満）または
// クライアントスレッド（1.20.5 以降）で走るので、パースから例外が漏れると
// パケット 1 通でセッションが終わる。ここでは
//   ・どこで切られても
//   ・完全なゴミでも
//   ・巨大な count を名乗られても
// デコーダが「打ち切り」として例外を投げるだけで済むことを確かめる。
class ScoreboardV1CodecTest {

    // ── 往復 ─────────────────────────────────────────────────

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
        // serverTotal < 0 は「Total 行を出すな」のセンチネル。符号を潰さないこと。
        // 値 0 は PING / HEALTH では正当な値なので省いてはいけない。
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

    // ── バイト配置の固定 ─────────────────────────────────────

    @Test
    @DisplayName("ranking_data のバイト配置が仕様どおりである")
    void rankingByteLayoutIsExact() {
        // 相手（HikariScoreBoard）は別実装なので、往復テストだけでは
        // 「両側が同じ間違いをしている」ケースを検出できない。
        // ここで期待バイト列を直に書いて配置を固定する。
        byte[] actual = ScoreboardV1Codec.encodeRanking(RankingV1Data.of(
                "T",
                List.of(new RankingV1Entry("Ab", 3L)),
                List.of(new RankingV1Entry("Ab", 3L)),
                2L, 1, 3L, "Ab"));

        List<Byte> expected = new ArrayList<>();
        expected.add((byte) 0x00);                 // hidden = false
        expected.add((byte) 0x01);                 // title 長 = 1 バイト
        addUtf8(expected, "T");
        expected.add((byte) 0x01);                 // varint count = 1
        expected.add((byte) 0x02);                 // name 長 = 2 バイト
        addUtf8(expected, "Ab");
        addLong(expected, 3L);                     // value
        addLong(expected, 2L);                     // serverTotal
        expected.add((byte) 0x01);                 // varint selfRank = 1
        addLong(expected, 3L);                     // selfValue
        expected.add((byte) 0x02);                 // selfName 長 = 2 バイト
        addUtf8(expected, "Ab");
        expected.add((byte) 0x01);                 // varint fullCount = 1
        expected.add((byte) 0x02);                 // name 長 = 2 バイト
        addUtf8(expected, "Ab");
        addLong(expected, 3L);                     // value

        assertArrayEquals(toArray(expected), actual);
    }

    @Test
    @DisplayName("varint は MC と同じ 7bit/バイトの可変長で書かれる")
    void varIntMatchesMinecraftEncoding() {
        // selfRank = 300 -> 0xAC 0x02
        byte[] bytes = ScoreboardV1Codec.encodeRanking(RankingV1Data.of(
                "", List.of(), List.of(), 0L, 300, 0L, ""));

        // hidden(1) + title 長(1) + count(1) + serverTotal(8) の後ろが selfRank
        assertEquals((byte) 0xAC, bytes[11]);
        assertEquals((byte) 0x02, bytes[12]);
        assertEquals(300, ScoreboardV1Codec.decodeRanking(bytes).selfRank());
    }

    // ── 旧サーバー互換 ───────────────────────────────────────

    @Test
    @DisplayName("full リストを送ってこない旧サーバーでは full = top になる")
    void missingFullListFallsBackToTop() {
        // 末尾の fullCount を書かない（HikariScoreBoard 旧版のレイアウト）
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x00);                    // hidden = false
        bytes.add((byte) 0x01);                    // title 長
        addUtf8(bytes, "T");
        bytes.add((byte) 0x01);                    // count = 1
        bytes.add((byte) 0x02);                    // name 長
        addUtf8(bytes, "Ab");
        addLong(bytes, 7L);                        // value
        addLong(bytes, 9L);                        // serverTotal
        bytes.add((byte) 0x01);                    // selfRank
        addLong(bytes, 7L);                        // selfValue
        bytes.add((byte) 0x02);                    // selfName 長
        addUtf8(bytes, "Ab");

        RankingV1Data decoded = ScoreboardV1Codec.decodeRanking(toArray(bytes));
        assertEquals(decoded.top(), decoded.full());
        assertEquals(1, decoded.full().size());
        assertEquals(7L, decoded.full().get(0).value());
    }

    @Test
    @DisplayName("full リストの後ろに余分なバイトがあっても読めるところまで読む")
    void trailingBytesAreIgnored() {
        // 読む側が余分なバイトを弾くと、将来サーバーがフィールドを足したときに
        // 旧クライアントがパケットごと落としてしまう。
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

    // ── 壊れたパケット ───────────────────────────────────────

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
            // 受信側（ScoreboardPacketClient）と同じ形の try/catch で包む。
            // ここで何も投げなければ netty スレッドで切断されることは無い。
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
        // 修正前は new ArrayList<>(count) がその場で Object[1_900_000_000]
        //（約 7.6GB）を確保しようとして OutOfMemoryError になっていた。
        // OOM は Error なので assertThrows(MalformedPacketException) では受からず、
        // このテストは退行したら必ず落ちる。
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x00);                    // hidden = false
        bytes.add((byte) 0x00);                    // title 長 0
        addVarInt(bytes, 1_900_000_000);           // count
        assertThrows(ScoreboardV1Codec.MalformedPacketException.class,
                () -> ScoreboardV1Codec.decodeRanking(toArray(bytes)));
    }

    @Test
    @DisplayName("巨大な fullCount を名乗る ranking_data は確保せず打ち切られる")
    void hugeFullCountDoesNotAllocate() {
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x00);                    // hidden = false
        bytes.add((byte) 0x00);                    // title 長 0
        bytes.add((byte) 0x00);                    // count = 0
        addLong(bytes, 0L);                        // serverTotal
        bytes.add((byte) 0x00);                    // selfRank = 0
        addLong(bytes, 0L);                        // selfValue
        bytes.add((byte) 0x00);                    // selfName 長 0
        addVarInt(bytes, 1_900_000_000);           // fullCount
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
        // title は string(256) なので読み込み上限は 256*4 = 1024 バイト
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x00);                    // hidden = false
        addVarInt(bytes, 2000);                    // title 長 = 2000 バイト
        assertThrows(ScoreboardV1Codec.MalformedPacketException.class,
                () -> ScoreboardV1Codec.decodeRanking(toArray(bytes)));
    }

    @Test
    @DisplayName("6 バイト以上の varint は MalformedPacketException になる")
    void oversizedVarIntIsMalformed() {
        List<Byte> bytes = new ArrayList<>();
        bytes.add((byte) 0x00);                    // hidden = false
        bytes.add((byte) 0x00);                    // title 長 0
        for (int i = 0; i < 6; i++) bytes.add((byte) 0xFF);
        assertThrows(ScoreboardV1Codec.MalformedPacketException.class,
                () -> ScoreboardV1Codec.decodeRanking(toArray(bytes)));
    }

    @Test
    @DisplayName("full リストは top と別インスタンスとして読まれる")
    void fullListIsIndependentWhenSent() {
        // 描画側は full だけを見る。top をそのまま使い回してしまうと
        // 「top は 10 件・full は全件」というサーバーの意図が壊れる。
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

    // ── ヘルパー ─────────────────────────────────────────────

    // ScoreboardPacketClient の受信ハンドラと同じ形（例外を握り潰す）を再現する
    private static void receiveLikeClient(byte[] payload, boolean ranking) {
        try {
            if (ranking) {
                ScoreboardV1Codec.decodeRanking(payload);
            } else {
                ScoreboardV1Codec.decodePlayerList(payload);
            }
        } catch (Exception ignored) {
            // ログに落として捨てるだけ
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
