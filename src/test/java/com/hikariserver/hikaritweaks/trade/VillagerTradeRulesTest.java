package com.hikariserver.hikaritweaks.trade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerTradeRulesTest {
    private static final int SLOTS = 36;

    @Test
    @DisplayName("一番目の取引（番号 0）を選んでいるときは全取引から探す状態")
    void firstTradeScansAllOffers() {
        assertTrue(VillagerTradeRules.scansAllOffers(0, 5));
    }

    @Test
    @DisplayName("二番目以降の取引を選んでいるときは、その取引だけを見る")
    void laterTradesAreExact() {
        assertFalse(VillagerTradeRules.scansAllOffers(1, 5));
        assertFalse(VillagerTradeRules.scansAllOffers(4, 5));
    }

    @Test
    @DisplayName("範囲外の番号もバニラと同じく全取引から探す状態とみなす")
    void outOfRangeScansAllOffers() {
        assertTrue(VillagerTradeRules.scansAllOffers(5, 5));
        assertTrue(VillagerTradeRules.scansAllOffers(-1, 5));
    }

    private static int[] emptyCounts() {
        return new int[SLOTS];
    }

    private static boolean[] noneSame() {
        return new boolean[SLOTS];
    }

    @Test
    @DisplayName("重ねられない道具（エンチャントの本など）は後ろの空き枠へ 1 回で置く")
    void unstackableGoesToLastEmptySlot() {
        assertArrayEquals(new int[] { 35 },
                VillagerTradeRules.depositPlan(emptyCounts(), noneSame(), 1, 1));
    }

    @Test
    @DisplayName("同じアイテムの枠があれば、空き枠より先にそこへ足す")
    void mergesBeforeUsingEmptySlot() {
        int[] counts = emptyCounts();
        boolean[] same = noneSame();
        counts[5] = 10;
        same[5] = true;
        assertArrayEquals(new int[] { 5 },
                VillagerTradeRules.depositPlan(counts, same, 64, 4));
    }

    @Test
    @DisplayName("足しきれない分は次の同じアイテムの枠、それでも余れば空き枠へ")
    void splitsAcrossStacksThenEmptySlot() {
        int[] counts = emptyCounts();
        boolean[] same = noneSame();
        counts[30] = 62; same[30] = true;
        counts[10] = 63; same[10] = true;
        assertArrayEquals(new int[] { 30, 10, 35 },
                VillagerTradeRules.depositPlan(counts, same, 64, 4));
    }

    @Test
    @DisplayName("満杯の同じアイテムの枠と、別のアイテムの枠は使わない")
    void skipsFullAndDifferentStacks() {
        int[] counts = emptyCounts();
        boolean[] same = noneSame();
        counts[35] = 64; same[35] = true;
        counts[34] = 5;  same[34] = false;
        assertArrayEquals(new int[] { 33 },
                VillagerTradeRules.depositPlan(counts, same, 64, 3));
    }

    @Test
    @DisplayName("インベントリに入りきらないときは null（取引しない）")
    void returnsNullWhenInventoryIsFull() {
        int[] counts = new int[SLOTS];
        boolean[] same = noneSame();
        for (int i = 0; i < SLOTS; i++) counts[i] = 1;
        assertNull(VillagerTradeRules.depositPlan(counts, same, 1, 1));
    }

    @Test
    @DisplayName("一部しか入らないときも null（取り出すとカーソルに残ってしまう）")
    void returnsNullWhenOnlyPartlyFits() {
        int[] counts = new int[SLOTS];
        boolean[] same = noneSame();
        for (int i = 0; i < SLOTS; i++) counts[i] = 1;
        counts[0] = 63; same[0] = true;
        assertNull(VillagerTradeRules.depositPlan(counts, same, 64, 4));
    }

    @Test
    @DisplayName("個数やスタック上限が 0 以下なら null")
    void rejectsNonPositiveAmounts() {
        assertNull(VillagerTradeRules.depositPlan(emptyCounts(), noneSame(), 64, 0));
        assertNull(VillagerTradeRules.depositPlan(emptyCounts(), noneSame(), 0, 1));
    }
}
