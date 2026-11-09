package com.hikariserver.hikaritweaks.warning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurabilityWarningStateTest {
    private static final String PICKAXE = "minecraft:diamond_pickaxe|ダイヤのつるはし";
    private static final String AXE     = "minecraft:diamond_axe|ダイヤの斧";

    private record Sample(int slot, String identity, int damage) {}

    private static Sample at(int slot, String identity, int damage) {
        return new Sample(slot, identity, damage);
    }

    private static int tick(DurabilityWarningState state, Sample... warningStateSlots) {
        state.beginTick();
        int warned = 0;
        for (Sample s : warningStateSlots) {
            if (state.offer(s.slot(), s.identity(), s.damage())) warned++;
        }
        state.endTick();
        return warned;
    }

    @Test
    @DisplayName("閾値は最大耐久の 1%（切り上げ・最小 1）")
    void thresholdIsOnePercent() {
        assertEquals(16, DurabilityWarningState.threshold(1561));
        assertEquals(4,  DurabilityWarningState.threshold(384));
        assertEquals(1,  DurabilityWarningState.threshold(1));
        assertEquals(1,  DurabilityWarningState.threshold(0));
    }

    @Test
    @DisplayName("残り耐久が閾値以下なら警告状態")
    void warningStateBoundary() {
        assertFalse(DurabilityWarningState.inWarningState(1561, 1561 - 17));
        assertTrue(DurabilityWarningState.inWarningState(1561, 1561 - 16));
        assertTrue(DurabilityWarningState.inWarningState(1561, 1561));
    }

    @Test
    @DisplayName("残り耐久パーセントは切り上げで負にならない")
    void remainingPercentIsCeiled() {
        assertEquals(1,   DurabilityWarningState.remainingPercent(1, 1561));
        assertEquals(100, DurabilityWarningState.remainingPercent(1561, 1561));
        assertEquals(0,   DurabilityWarningState.remainingPercent(0, 1561));
        assertEquals(0,   DurabilityWarningState.remainingPercent(-5, 1561));
        assertEquals(0,   DurabilityWarningState.remainingPercent(1, 0));
    }

    @Test
    @DisplayName("最後の 1% を削り切るあいだ、1 ダメージごとに毎回警告する")
    void warnsOnEveryPointOfDamage() {
        DurabilityWarningState state = new DurabilityWarningState();
        int warned = 0;
        for (int remaining = 16; remaining >= 0; remaining--) {
            warned += tick(state, at(0, PICKAXE, 1561 - remaining));
        }
        assertEquals(17, warned);
    }

    @Test
    @DisplayName("警告状態に入った最初の tick で警告し、使わなければ黙る")
    void warnsOnEntryAndStaysSilentWhileUnused() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1545)));
        assertEquals(0, tick(state, at(0, PICKAXE, 1545)));
        assertEquals(0, tick(state, at(0, PICKAXE, 1545)));
    }

    @Test
    @DisplayName("インベントリに置いたままの道具は鳴らない")
    void sameDamageAcrossTicksIsSilent() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(9, PICKAXE, 1550)));
        for (int i = 0; i < 20; i++) {
            assertEquals(0, tick(state, at(9, PICKAXE, 1550)));
        }
    }

    @Test
    @DisplayName("修繕で耐久が戻っても警告状態のうちは鳴り直さない")
    void mendingInsideWarningStateDoesNotRefire() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1559)));
        for (int damage = 1558; damage >= 1549; damage--) {
            assertEquals(0, tick(state, at(0, PICKAXE, damage)));
        }
    }

    @Test
    @DisplayName("修繕しても記録は更新され、そこから削れば再び鳴る")
    void mendingUpdatesRecordWithoutWarning() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1550)));
        assertEquals(0, tick(state, at(0, PICKAXE, 1548)));
        assertEquals(1, tick(state, at(0, PICKAXE, 1549)));
    }

    @Test
    @DisplayName("無名の同種の道具 2 本をスロットごとに独立して追跡する")
    void sameItemInTwoSlotsTrackedIndependently() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(2, tick(state, at(0, PICKAXE, 1550), at(4, PICKAXE, 1546)));
        assertEquals(1, tick(state, at(0, PICKAXE, 1550), at(4, PICKAXE, 1547)));
        assertEquals(1, tick(state, at(0, PICKAXE, 1551), at(4, PICKAXE, 1547)));
        assertEquals(2, tick(state, at(0, PICKAXE, 1552), at(4, PICKAXE, 1548)));
        assertEquals(0, tick(state, at(0, PICKAXE, 1552), at(4, PICKAXE, 1548)));
    }

    @Test
    @DisplayName("同じスロットの中身が別のアイテムに替われば、耐久が多くても鳴る")
    void differentItemInSameSlotWarns() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1550)));
        assertEquals(1, tick(state, at(0, AXE, 1540)));
        assertEquals(1, tick(state, at(0, PICKAXE, 1530)));
    }

    @Test
    @DisplayName("警告状態から出たら再武装され、また入ったときに警告する")
    void rearmsAfterLeavingWarningState() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1550)));
        assertEquals(0, tick(state));
        assertFalse(state.isTracked(0));
        assertEquals(1, tick(state, at(0, PICKAXE, 1546)));
    }

    @Test
    @DisplayName("アイテムが無くなったら再武装される")
    void rearmsWhenItemDisappears() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1550)));
        assertEquals(0, tick(state));
        assertEquals(0, state.trackedCount());
        assertEquals(1, tick(state, at(0, PICKAXE, 1548)));
    }

    @Test
    @DisplayName("片方だけ警告状態から出ても、もう片方は鳴り直さない")
    void partialRearmKeepsTheOther() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(2, tick(state, at(0, PICKAXE, 1550), at(1, AXE, 1550)));
        assertEquals(0, tick(state, at(0, PICKAXE, 1550)));
        assertFalse(state.isTracked(1));
        assertTrue(state.isTracked(0));
        assertEquals(1, tick(state, at(0, PICKAXE, 1550), at(1, AXE, 1552)));
    }

    @Test
    @DisplayName("追跡しているのは今 tick に警告状態だったスロットだけ")
    void trackedCountCountsOnlyWarningStateSlots() {
        DurabilityWarningState state = new DurabilityWarningState();
        tick(state, at(0, PICKAXE, 1550), at(1, AXE, 1550), at(2, PICKAXE, 1550));
        assertEquals(3, state.trackedCount());
        tick(state, at(1, AXE, 1550));
        assertEquals(1, state.trackedCount());
        assertTrue(state.isTracked(1));
        assertFalse(state.isTracked(0));
        assertFalse(state.isTracked(2));
    }

    @Test
    @DisplayName("道具を別スロットへ移すと 1 回だけ余分に鳴る（意図した代償）")
    void slotMoveWarnsOnceAsAcceptedCost() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1550)));
        assertEquals(1, tick(state, at(5, PICKAXE, 1550)));
        assertEquals(0, tick(state, at(5, PICKAXE, 1550)));
        assertFalse(state.isTracked(0));
    }

    @Test
    @DisplayName("2 本の道具を入れ替えると、耐久が違うほうだけ鳴る")
    void swappingTwoToolsWarnsForTheIncreasedSlot() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(2, tick(state, at(0, PICKAXE, 1550), at(1, PICKAXE, 1546)));
        assertEquals(1, tick(state, at(0, PICKAXE, 1546), at(1, PICKAXE, 1550)));
    }

    @Test
    @DisplayName("同 tick 内で offer の順序が変わっても結果は変わらない")
    void offerOrderWithinTickDoesNotMatter() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(2, tick(state, at(0, PICKAXE, 1550), at(1, AXE, 1550)));
        assertEquals(0, tick(state, at(1, AXE, 1550), at(0, PICKAXE, 1550)));
        assertEquals(0, tick(state, at(0, PICKAXE, 1550), at(1, AXE, 1550)));
    }

    @Test
    @DisplayName("種類が違えば別々に警告する")
    void differentItemsWarnSeparately() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1550)));
        assertEquals(1, tick(state, at(0, PICKAXE, 1550), at(1, AXE, 1550)));
    }

    @Test
    @DisplayName("同じスロットを同じ tick に 2 回渡しても警告は 1 回で、記録も二重更新しない")
    void duplicateSlotInSameTickWarnsOnce() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1550), at(0, PICKAXE, 1551)));
        assertEquals(1, tick(state, at(0, PICKAXE, 1551)));
    }

    @Test
    @DisplayName("endTick() を挟まずに offer() しても同じ tick 内では 1 回だけ")
    void offerIsIdempotentWithinTick() {
        DurabilityWarningState state = new DurabilityWarningState();
        state.beginTick();
        assertTrue(state.offer(0, PICKAXE, 1550));
        assertFalse(state.offer(0, PICKAXE, 1551));
        assertFalse(state.offer(0, PICKAXE, 1552));
        state.endTick();
        assertTrue(state.isTracked(0));
    }

    @Test
    @DisplayName("clear() 後は再び警告する")
    void clearRearmsEverything() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1550)));
        state.clear();
        assertEquals(0, state.trackedCount());
        assertEquals(1, tick(state, at(0, PICKAXE, 1550)));
    }
}
