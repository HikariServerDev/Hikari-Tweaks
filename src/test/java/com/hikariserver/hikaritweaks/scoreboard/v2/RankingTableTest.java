package com.hikariserver.hikaritweaks.scoreboard.v2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// docs/ranking-v2-protocol.md §3（不変条件）と §5.1（クライアント側の状態）のテスト。
class RankingTableTest {

    // 文字列の辞書順と UUID.compareTo の結果が食い違うペア。
    //   文字列順      : ID_LOW  < ID_HIGH
    //   UUID.compareTo: ID_HIGH < ID_LOW（most significant bits の符号付き比較のため）
    private static final UUID ID_LOW  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ID_HIGH = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    private static final UUID ID_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    // ── 並び順（§3.3 / §3.4）───────────────────────────────

    @Test
    @DisplayName("前提: 選んだ UUID で文字列順と UUID.compareTo が食い違う")
    void chosenUuidsActuallyDisagree() {
        assertTrue(ID_LOW.toString().compareTo(ID_HIGH.toString()) < 0);
        // ここが逆になっていたらテストの意味が無い
        assertTrue(ID_LOW.compareTo(ID_HIGH) > 0);
    }

    @Test
    @DisplayName("同値の並びは uuid 文字列の昇順（UUID.compareTo ではない）")
    void tiesBreakOnUuidStringOrder() {
        RankingTable table = new RankingTable();
        table.apply(RankingV2Message.snapshot(1, "T", 0L, List.of(
                new RankingRow(ID_HIGH, "High", 50L),
                new RankingRow(ID_LOW, "Low", 50L))));

        List<RankingRow> sorted = table.sorted();
        assertEquals(ID_LOW, sorted.get(0).playerId());
        assertEquals(ID_HIGH, sorted.get(1).playerId());

        // UUID.compareTo で並べると逆になることを明示しておく
        List<RankingRow> wrong = new ArrayList<>(sorted);
        wrong.sort((a, b) -> a.playerId().compareTo(b.playerId()));
        assertNotEquals(sorted.get(0).playerId(), wrong.get(0).playerId());
    }

    @Test
    @DisplayName("値は降順に並ぶ")
    void sortsByValueDescending() {
        RankingTable table = new RankingTable();
        table.apply(RankingV2Message.snapshot(1, "T", 0L, List.of(
                new RankingRow(ID_1, "One", 10L),
                new RankingRow(ID_2, "Two", 30L),
                new RankingRow(ID_LOW, "Low", 20L))));

        List<RankingRow> sorted = table.sorted();
        assertEquals(30L, sorted.get(0).value());
        assertEquals(20L, sorted.get(1).value());
        assertEquals(10L, sorted.get(2).value());
    }

    @Test
    @DisplayName("値 0 の行を落とさない（§3.7：クライアントは値でフィルタしない）")
    void keepsZeroValuedRows() {
        RankingTable table = new RankingTable();
        table.apply(RankingV2Message.snapshot(1, "Ping", -1L, List.of(
                new RankingRow(ID_1, "One", 0L),
                new RankingRow(ID_2, "Two", 0L))));
        assertEquals(2, table.size());
        assertEquals(2, table.sorted().size());
    }

    // ── SNAPSHOT（§3.1）─────────────────────────────────────

    @Test
    @DisplayName("SNAPSHOT はテーブルを丸ごと置き換える（マージしない）")
    void snapshotReplacesWholeTable() {
        RankingTable table = new RankingTable();
        table.apply(RankingV2Message.snapshot(1, "T", 0L,
                List.of(new RankingRow(ID_1, "One", 1L))));
        table.apply(RankingV2Message.snapshot(1, "T", 0L,
                List.of(new RankingRow(ID_2, "Two", 2L))));

        assertEquals(1, table.size());
        assertEquals(ID_2, table.sorted().get(0).playerId());
    }

    @Test
    @DisplayName("boardId が変わっていない SNAPSHOT も必ず適用する")
    void snapshotAtSameBoardIdStillApplies() {
        // ここが今回いちばん壊しやすい箇所。
        // PING / HEALTH / PLAYER_LEVEL はサーバー側に版が無いので
        // 同じ boardId の SNAPSHOT が 250ms ごとに届く（§4.3）。
        // 「既知の boardId なら無視」と最適化すると表示が固まる。
        RankingTable table = new RankingTable();
        table.apply(RankingV2Message.snapshot(9, "Health", -1L,
                List.of(new RankingRow(ID_1, "One", 20L))));
        int generationAfterFirst = table.generation();

        table.apply(RankingV2Message.snapshot(9, "Health", -1L,
                List.of(new RankingRow(ID_1, "One", 6L))));

        assertEquals(6L, table.sorted().get(0).value());
        // 同じボードなので世代は増えない（＝表示側の補間状態を捨てない）
        assertEquals(generationAfterFirst, table.generation());
    }

    @Test
    @DisplayName("boardId が変わる SNAPSHOT は世代を進める")
    void snapshotWithNewBoardIdBumpsGeneration() {
        RankingTable table = new RankingTable();
        table.apply(RankingV2Message.snapshot(1, "A", 0L, List.of()));
        int before = table.generation();
        table.apply(RankingV2Message.snapshot(2, "B", 0L, List.of()));
        assertNotEquals(before, table.generation());
    }

    @Test
    @DisplayName("SNAPSHOT は title と serverTotal を更新する")
    void snapshotUpdatesHeaderFields() {
        RankingTable table = new RankingTable();
        table.apply(RankingV2Message.snapshot(1, "Blocks Mined", 999L, List.of()));
        assertEquals("Blocks Mined", table.title());
        assertEquals(999L, table.serverTotal());
    }

    // ── DELTA（§3.1 / §2）───────────────────────────────────

    @Test
    @DisplayName("boardId が一致しない DELTA は捨てる")
    void deltaWithWrongBoardIdIsDropped() {
        RankingTable table = new RankingTable();
        table.apply(RankingV2Message.snapshot(5, "T", 0L,
                List.of(new RankingRow(ID_1, "One", 1L))));

        RankingTable.ApplyResult result = table.apply(RankingV2Message.delta(
                6, 0L, List.of(new RankingRow(ID_2, "Two", 2L)), List.of()));

        assertEquals(RankingTable.ApplyResult.DROPPED_BOARD_MISMATCH, result);
        assertEquals(1, table.size());
        assertEquals(ID_1, table.sorted().get(0).playerId());
    }

    @Test
    @DisplayName("SNAPSHOT を受ける前の DELTA は捨てる")
    void deltaBeforeAnySnapshotIsDropped() {
        RankingTable table = new RankingTable();
        RankingTable.ApplyResult result = table.apply(RankingV2Message.delta(
                0, 0L, List.of(new RankingRow(ID_1, "One", 1L)), List.of()));
        assertEquals(RankingTable.ApplyResult.DROPPED_BOARD_MISMATCH, result);
        assertEquals(0, table.size());
    }

    @Test
    @DisplayName("DELTA の upsert は追加と上書きの両方を行う")
    void deltaUpsertsAddAndOverwrite() {
        RankingTable table = new RankingTable();
        table.apply(RankingV2Message.snapshot(1, "T", 0L,
                List.of(new RankingRow(ID_1, "One", 1L))));

        table.apply(RankingV2Message.delta(1, 0L, List.of(
                new RankingRow(ID_1, "OneRenamed", 100L),   // 上書き
                new RankingRow(ID_2, "Two", 50L)),          // 追加
                List.of()));

        assertEquals(2, table.size());
        assertEquals("OneRenamed", table.sorted().get(0).name());
        assertEquals(100L, table.sorted().get(0).value());
        assertEquals(50L, table.sorted().get(1).value());
    }

    @Test
    @DisplayName("DELTA の remove は行を消す。未知の uuid の remove は無害")
    void deltaRemovesRows() {
        RankingTable table = new RankingTable();
        table.apply(RankingV2Message.snapshot(1, "T", 0L, List.of(
                new RankingRow(ID_1, "One", 1L),
                new RankingRow(ID_2, "Two", 2L))));

        table.apply(RankingV2Message.delta(1, 0L, List.of(), List.of(ID_1, ID_LOW)));

        assertEquals(1, table.size());
        assertEquals(ID_2, table.sorted().get(0).playerId());
    }

    @Test
    @DisplayName("DELTA は title を変えず serverTotal だけ更新する")
    void deltaKeepsTitleAndUpdatesTotal() {
        RankingTable table = new RankingTable();
        table.apply(RankingV2Message.snapshot(1, "Blocks Mined", 10L, List.of()));
        table.apply(RankingV2Message.delta(1, 20L, List.of(), List.of()));
        assertEquals("Blocks Mined", table.title());
        assertEquals(20L, table.serverTotal());
    }

    // ── HIDE（§2 / §3.2）────────────────────────────────────

    @Test
    @DisplayName("HIDE はテーブルを捨て、以後の DELTA も捨てる")
    void hideDropsTableAndSubsequentDeltas() {
        RankingTable table = new RankingTable();
        table.apply(RankingV2Message.snapshot(1, "T", 0L,
                List.of(new RankingRow(ID_1, "One", 1L))));

        table.apply(RankingV2Message.hide());
        assertTrue(table.isHidden());
        assertFalse(table.hasBoard());
        assertEquals(0, table.size());

        // §3.2: HIDE 直後の DELTA を空のテーブルへマージしてはいけない
        RankingTable.ApplyResult result = table.apply(RankingV2Message.delta(
                1, 0L, List.of(new RankingRow(ID_2, "Two", 2L)), List.of()));
        assertEquals(RankingTable.ApplyResult.DROPPED_BOARD_MISMATCH, result);
        assertEquals(0, table.size());
    }

    @Test
    @DisplayName("HIDE のあとの SNAPSHOT で表示が戻る")
    void snapshotAfterHideRestoresBoard() {
        RankingTable table = new RankingTable();
        table.apply(RankingV2Message.hide());
        table.apply(RankingV2Message.snapshot(2, "T", 0L,
                List.of(new RankingRow(ID_1, "One", 1L))));
        assertFalse(table.isHidden());
        assertTrue(table.hasBoard());
        assertEquals(1, table.size());
    }

    // ── メモリガード（§3.8）─────────────────────────────────

    @Test
    @DisplayName("512 件を超えたら警告して上位 512 件へ切り詰める")
    void trimsAtFiveHundredTwelveWithWarning() {
        RankingTable table = new RankingTable();
        List<String> warnings = new ArrayList<>();
        table.setWarningSink(warnings::add);

        List<RankingRow> rows = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            rows.add(new RankingRow(new UUID(0L, i), "p" + i, i));
        }
        table.apply(RankingV2Message.snapshot(1, "T", 0L, rows));

        assertEquals(RankingTable.MAX_ROWS, table.size());
        assertEquals(1, warnings.size());
        // 切り詰めるのは順位下位から。最上位（値 599）は残る。
        assertEquals(599L, table.sorted().get(0).value());
        // 200（CLIENT_FULL_LIMIT）で切ってはいけない
        assertTrue(table.size() > 200);
    }

    @Test
    @DisplayName("512 件以下なら警告しない")
    void doesNotWarnBelowGuard() {
        RankingTable table = new RankingTable();
        List<String> warnings = new ArrayList<>();
        table.setWarningSink(warnings::add);

        List<RankingRow> rows = new ArrayList<>();
        for (int i = 0; i < RankingTable.MAX_ROWS; i++) {
            rows.add(new RankingRow(new UUID(0L, i), "p" + i, i));
        }
        table.apply(RankingV2Message.snapshot(1, "T", 0L, rows));

        assertEquals(RankingTable.MAX_ROWS, table.size());
        assertTrue(warnings.isEmpty());
    }

    @Test
    @DisplayName("超過状態が続いても警告は 1 回だけ")
    void warnsOnlyOnceWhileOverflowing() {
        RankingTable table = new RankingTable();
        List<String> warnings = new ArrayList<>();
        table.setWarningSink(warnings::add);

        List<RankingRow> rows = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            rows.add(new RankingRow(new UUID(0L, i), "p" + i, i));
        }
        // PING 系は 250ms ごとに SNAPSHOT が届く。毎回警告するとログが埋まる。
        for (int i = 0; i < 5; i++) {
            table.apply(RankingV2Message.snapshot(1, "T", 0L, rows));
        }
        assertEquals(1, warnings.size());
    }

    // ── リセット ─────────────────────────────────────────────

    @Test
    @DisplayName("reset() で状態が消える")
    void resetClearsEverything() {
        RankingTable table = new RankingTable();
        table.apply(RankingV2Message.snapshot(1, "T", 5L,
                List.of(new RankingRow(ID_1, "One", 1L))));
        table.reset();
        assertFalse(table.hasBoard());
        assertFalse(table.isHidden());
        assertEquals(0, table.size());
        assertEquals(-1L, table.serverTotal());
    }
}
