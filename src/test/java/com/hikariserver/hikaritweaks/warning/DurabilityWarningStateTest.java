package com.hikariserver.hikaritweaks.warning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 耐久値警告の状態機械のテスト。
//
// 直した不具合は 2 つ。
//   (1) キーにダメージ値が入っていたため、1 ダメージごとに新しい署名になり
//       残り 1% を削り切るあいだ警告が毎回出ていた。
//       修繕でダメージが**減る**ときはさらに悪く、経験値を拾うたびに鳴っていた。
//   (2) キーがスロット番号だったため、同じ道具を別スロットへ移すだけで
//       記録が引き継がれず警告が出ていた。
// ここではキーを外から渡す形にして、両方を tick 単位で再現している。
class DurabilityWarningStateTest {

    private static final String PICKAXE = "minecraft:diamond_pickaxe|ダイヤのつるはし";
    private static final String AXE     = "minecraft:diamond_axe|ダイヤの斧";

    // 1 tick 分（警告状態のキーを 0 個以上渡す）を回して、警告が出たキーの数を返す
    private static int tick(DurabilityWarningState state, String... warningStateKeys) {
        state.beginTick();
        int warned = 0;
        for (String key : warningStateKeys) {
            if (state.offer(key)) warned++;
        }
        state.endTick();
        return warned;
    }

    // ── 閾値 ─────────────────────────────────────────────────

    @Test
    @DisplayName("閾値は最大耐久の 1%（切り上げ・最小 1）")
    void thresholdIsOnePercent() {
        assertEquals(16, DurabilityWarningState.threshold(1561)); // ダイヤのつるはし
        assertEquals(4,  DurabilityWarningState.threshold(384));  // 金のつるはし相当
        assertEquals(1,  DurabilityWarningState.threshold(1));    // 最小 1
        assertEquals(1,  DurabilityWarningState.threshold(0));    // 0 除算にしない
    }

    @Test
    @DisplayName("残り耐久が閾値以下なら警告状態")
    void warningStateBoundary() {
        // ダイヤのつるはし: 最大 1561 / 閾値 16
        assertFalse(DurabilityWarningState.inWarningState(1561, 1561 - 17)); // 残り 17
        assertTrue(DurabilityWarningState.inWarningState(1561, 1561 - 16));  // 残り 16
        assertTrue(DurabilityWarningState.inWarningState(1561, 1561));       // 残り 0
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

    // ── 「1 アイテム 1 回」────────────────────────────────────

    @Test
    @DisplayName("警告状態に入った最初の tick だけ警告する")
    void warnsOnceOnEntry() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, PICKAXE));
        assertEquals(0, tick(state, PICKAXE));
        assertEquals(0, tick(state, PICKAXE));
    }

    @Test
    @DisplayName("最後の 1% を削り切るあいだ警告は 1 回しか出ない")
    void doesNotRefirePerPointOfDamage() {
        // これが本丸。修正前はダメージ値がキーに入っていたため
        // 残り 16 → 0 の 17 tick すべてで警告とサウンドが出ていた。
        DurabilityWarningState state = new DurabilityWarningState();
        int warned = 0;
        for (int remaining = 16; remaining >= 0; remaining--) {
            warned += tick(state, PICKAXE);
        }
        assertEquals(1, warned);
    }

    @Test
    @DisplayName("修繕で耐久が戻っても警告状態のうちは鳴り直さない")
    void mendingInsideWarningStateDoesNotRefire() {
        // 修正前は「通っていないダメージ値」が次々できるため、
        // 経験値を拾って耐久が**戻る**たびに警告が飛んでいた。
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, PICKAXE));   // 残り 2 で警告
        // 残り 3 → 4 → ... と戻る（いずれも閾値 16 以下なので警告状態のまま）
        for (int i = 0; i < 10; i++) {
            assertEquals(0, tick(state, PICKAXE));
        }
    }

    // ── 再武装 ───────────────────────────────────────────────

    @Test
    @DisplayName("警告状態から出たら再武装され、また入ったときに警告する")
    void rearmsAfterLeavingWarningState() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, PICKAXE));
        // 修理されて閾値を超えた tick（キーが渡ってこない）
        assertEquals(0, tick(state));
        assertFalse(state.isWarned(PICKAXE));
        // 使い込んで再び警告状態へ
        assertEquals(1, tick(state, PICKAXE));
    }

    @Test
    @DisplayName("アイテムが無くなったら再武装される")
    void rearmsWhenItemDisappears() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, PICKAXE));
        // 壊れた / チェストへ預けた
        assertEquals(0, tick(state));
        assertEquals(0, state.warnedCount());
        // 予備を取り出して装備した
        assertEquals(1, tick(state, PICKAXE));
    }

    // ── 「同じアイテム」の定義 ────────────────────────────────

    @Test
    @DisplayName("スロットを移し替えても同じアイテムとして扱う")
    void slotChangeDoesNotRefire() {
        // キーにスロット番号を入れていない以上、状態機械から見れば
        // 「同じキーが次の tick も来た」だけになる。
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, PICKAXE));
        // ホットバー 3 → 5 へ移動しただけの tick
        assertEquals(0, tick(state, PICKAXE));
    }

    @Test
    @DisplayName("2 本の道具を持ち替えても入れ替わりで鳴り直さない")
    void swappingTwoToolsDoesNotRefire() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(2, tick(state, PICKAXE, AXE));   // 初回はそれぞれ 1 回
        assertEquals(0, tick(state, AXE, PICKAXE));   // 並びが入れ替わっただけ
        assertEquals(0, tick(state, PICKAXE, AXE));
    }

    @Test
    @DisplayName("種類が違えば別々に警告する")
    void differentItemsWarnSeparately() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, PICKAXE));
        assertEquals(1, tick(state, PICKAXE, AXE));   // 斧だけ新規
    }

    @Test
    @DisplayName("同じキーを同じ tick に 2 回渡しても警告は 1 回")
    void duplicateKeyInSameTickWarnsOnce() {
        // 無名の同種の道具を 2 本持っているケース。割り切りとして 1 回にする。
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, PICKAXE, PICKAXE));
    }

    @Test
    @DisplayName("片方だけ警告状態から出ても、もう片方は鳴り直さない")
    void partialRearmKeepsTheOther() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(2, tick(state, PICKAXE, AXE));
        assertEquals(0, tick(state, PICKAXE));        // 斧を修理した
        assertFalse(state.isWarned(AXE));
        assertTrue(state.isWarned(PICKAXE));
        assertEquals(1, tick(state, PICKAXE, AXE));   // 斧だけ鳴り直す
    }

    // ── 全消去 ───────────────────────────────────────────────

    @Test
    @DisplayName("clear() 後は再び警告する")
    void clearRearmsEverything() {
        // 設定で機能を切って入れ直したとき用
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, PICKAXE));
        state.clear();
        assertEquals(0, state.warnedCount());
        assertEquals(1, tick(state, PICKAXE));
    }

    @Test
    @DisplayName("endTick() を挟まずに offer() しても同じ tick 内では 1 回だけ")
    void offerIsIdempotentWithinTick() {
        DurabilityWarningState state = new DurabilityWarningState();
        state.beginTick();
        assertTrue(state.offer(PICKAXE));
        assertFalse(state.offer(PICKAXE));
        assertFalse(state.offer(PICKAXE));
        state.endTick();
        assertTrue(state.isWarned(PICKAXE));
    }
}
