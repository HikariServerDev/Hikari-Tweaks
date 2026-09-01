package com.hikariserver.hikaritweaks.restock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// restock パッケージの純ロジック（Minecraft の型に触らない部分）のテスト。
// ハンドラ本体はクライアントが無いと動かせないので、
// 「何個動かせるか」と「そのコンテナを信用してよいか」の判断だけを検証する。
class RestockRulesTest {

    // ── plannedMoveCount ──────────────────────────────────────

    @Test
    @DisplayName("空き容量とソースの少ない方だけ動かす")
    void movesTheSmallerOfRoomAndSource() {
        // 空き 61、ソース 40 → 40
        assertEquals(40, RestockRules.plannedMoveCount(3, 64, 40));
        // 空き 5、ソース 40 → 5
        assertEquals(5, RestockRules.plannedMoveCount(59, 64, 40));
    }

    @Test
    @DisplayName("満杯のホットバースロットへは 1 個も動かせない")
    void fullHotbarSlotMovesNothing() {
        assertEquals(0, RestockRules.plannedMoveCount(64, 64, 40));
    }

    @Test
    @DisplayName("最大数を超えて持っている場合でも負の個数は返さない")
    void overfilledSlotDoesNotReturnNegative() {
        // オーバースタックしたアイテムを掴んでいるときに負値が漏れると
        // クリック回数の for ループが壊れる
        assertEquals(0, RestockRules.plannedMoveCount(70, 64, 40));
    }

    @Test
    @DisplayName("ソースが空なら 1 個も動かせない")
    void emptySourceMovesNothing() {
        assertEquals(0, RestockRules.plannedMoveCount(3, 64, 0));
        assertEquals(0, RestockRules.plannedMoveCount(3, 64, -1));
    }

    @Test
    @DisplayName("0 は no-op を意味する（呼び出し側は補充成功と扱ってはいけない）")
    void zeroMeansNoOp() {
        // ここが 0 なのにハンドラが true を返すと、
        // 補充インターバルがリセットされて同じ no-op を延々繰り返す
        assertFalse(RestockRules.plannedMoveCount(64, 64, 64) > 0);
        assertTrue(RestockRules.plannedMoveCount(63, 64, 64) > 0);
    }

    @Test
    @DisplayName("スタック上限 1 のアイテムでも成立する")
    void worksForNonStackableItems() {
        assertEquals(0, RestockRules.plannedMoveCount(1, 1, 5));
        assertEquals(1, RestockRules.plannedMoveCount(0, 1, 5));
    }

    // ── matchesContainerSize ──────────────────────────────────

    @Test
    @DisplayName("スロット数がブロックの中身のサイズと一致すれば信用する")
    void exactSizeMatches() {
        // 単チェスト / 樽 / シュルカーボックス
        assertTrue(RestockRules.matchesContainerSize(27, 27, false));
        // ホッパー
        assertTrue(RestockRules.matchesContainerSize(5, 5, false));
        // ディスペンサー
        assertTrue(RestockRules.matchesContainerSize(9, 9, false));
    }

    @Test
    @DisplayName("チェストだけはダブルチェストぶんの 2 倍サイズも許す")
    void chestAllowsDoubleSize() {
        assertTrue(RestockRules.matchesContainerSize(54, 27, true));
        // チェスト以外（樽やシュルカー）は 2 倍にならないので許さない
        assertFalse(RestockRules.matchesContainerSize(54, 27, false));
    }

    @Test
    @DisplayName("サイズが食い違う画面は信用しない（単チェストに 54 スロットの GUI など）")
    void mismatchedSizeIsRejected() {
        assertFalse(RestockRules.matchesContainerSize(45, 27, true));
        assertFalse(RestockRules.matchesContainerSize(9, 27, true));
        // 3 倍は許さない
        assertFalse(RestockRules.matchesContainerSize(81, 27, true));
    }

    @Test
    @DisplayName("サイズが 0 以下なら信用しない（fail closed）")
    void nonPositiveSizesAreRejected() {
        assertFalse(RestockRules.matchesContainerSize(0, 27, true));
        assertFalse(RestockRules.matchesContainerSize(27, 0, true));
        assertFalse(RestockRules.matchesContainerSize(-1, -1, true));
    }
}
