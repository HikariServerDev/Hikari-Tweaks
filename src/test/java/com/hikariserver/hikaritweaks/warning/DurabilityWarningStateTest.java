package com.hikariserver.hikaritweaks.warning;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 耐久値警告の状態機械のテスト。
//
// ── この状態機械が満たすべきこと ──────────────────────────────────────
//   (1) 警告状態のあいだ、耐久が**減るたび**に警告する（＝道具を使うたびに鳴る）
//   (2) 修繕で耐久が**戻った**ときは鳴らない
//   (3) 無名の同種の道具を 2 本持っていても、スロットごとに独立して追跡する
//   (4) 警告状態から出た（修理された・無くなった）ら記録を捨てて再武装する
//
// ★ (1) は v1.1.x では**不具合**として修正された挙動で、v1.2.3 で**仕様として**戻したもの。
//   当時はキーにダメージ値そのものが入っていて「初めて見た署名か」でしか判定できず、
//   修繕で耐久が戻るときも通っていない署名が次々できて経験値を拾うたびに鳴っていた。
//   いまは damage を前回値と比較して**増えたときだけ**鳴らすので (2) と両立する。
//   このテストが (1) と (2) を同時に固定していることが、その区別の担保になっている。
class DurabilityWarningStateTest {

    private static final String PICKAXE = "minecraft:diamond_pickaxe|ダイヤのつるはし";
    private static final String AXE     = "minecraft:diamond_axe|ダイヤの斧";

    // 1 tick 分に渡す 1 スロット分の観測値
    private record Sample(int slot, String identity, int damage) {}

    private static Sample at(int slot, String identity, int damage) {
        return new Sample(slot, identity, damage);
    }

    // 1 tick 分（警告状態のスロットを 0 個以上渡す）を回して、警告が出た数を返す
    private static int tick(DurabilityWarningState state, Sample... warningStateSlots) {
        state.beginTick();
        int warned = 0;
        for (Sample s : warningStateSlots) {
            if (state.offer(s.slot(), s.identity(), s.damage())) warned++;
        }
        state.endTick();
        return warned;
    }

    // ── 閾値（純関数・今回の変更では触っていない）────────────────

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

    // ── 「使うたびに鳴る」────────────────────────────────────

    @Test
    @DisplayName("最後の 1% を削り切るあいだ、1 ダメージごとに毎回警告する")
    void warnsOnEveryPointOfDamage() {
        // ★ これが本丸。ダイヤのつるはし（最大 1561・閾値 16）が
        //   残り 16 → 0 になるまでの 17 tick すべてで警告が出ること。
        //   v1.2.2 まではここが 1 回しか出ず、「2% のときだけ鳴る」と報告された。
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
        assertEquals(1, tick(state, at(9, PICKAXE, 1550)));   // 初回だけ
        for (int i = 0; i < 20; i++) {
            assertEquals(0, tick(state, at(9, PICKAXE, 1550)));
        }
    }

    // ── 「修繕では鳴らない」──────────────────────────────────

    @Test
    @DisplayName("修繕で耐久が戻っても警告状態のうちは鳴り直さない")
    void mendingInsideWarningStateDoesNotRefire() {
        // ★ ここが「1 ダメージごとに鳴る」と両立させたい要件。
        //   v1.1.x はキーにダメージ値が入っていたため、耐久が**戻る**たびに
        //   通っていない署名ができて経験値を拾うたびに鳴っていた。
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1559)));   // 残り 2 で警告
        // 修繕で 1558 → 1557 → ... と戻る（いずれも閾値 16 以下なので警告状態のまま）
        for (int damage = 1558; damage >= 1549; damage--) {
            assertEquals(0, tick(state, at(0, PICKAXE, damage)));
        }
    }

    @Test
    @DisplayName("修繕しても記録は更新され、そこから削れば再び鳴る")
    void mendingUpdatesRecordWithoutWarning() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1550)));   // 警告（記録 1550）
        assertEquals(0, tick(state, at(0, PICKAXE, 1548)));   // 修繕（記録 1548 へ更新・無音）
        // 記録が 1550 のままなら 1549 は「増えていない」ので鳴らないはず。
        // 鳴るということは記録が 1548 に更新されている証拠。
        assertEquals(1, tick(state, at(0, PICKAXE, 1549)));
    }

    // ── 同種の道具 2 本をスロットごとに独立追跡 ──────────────

    @Test
    @DisplayName("無名の同種の道具 2 本をスロットごとに独立して追跡する")
    void sameItemInTwoSlotsTrackedIndependently() {
        // ★ この変更の目的そのもの。v1.2.2 まではキーが「登録 ID + 表示名」だけだったため
        //   2 本持っていても 1 本しか追跡されず、もう 1 本を使っても鳴らなかった。
        DurabilityWarningState state = new DurabilityWarningState();
        // 両方とも警告状態に入った tick
        assertEquals(2, tick(state, at(0, PICKAXE, 1550), at(4, PICKAXE, 1546)));
        // スロット 4 のほうだけ使った
        assertEquals(1, tick(state, at(0, PICKAXE, 1550), at(4, PICKAXE, 1547)));
        // スロット 0 のほうだけ使った
        assertEquals(1, tick(state, at(0, PICKAXE, 1551), at(4, PICKAXE, 1547)));
        // 両方使った
        assertEquals(2, tick(state, at(0, PICKAXE, 1552), at(4, PICKAXE, 1548)));
        // どちらも使っていない
        assertEquals(0, tick(state, at(0, PICKAXE, 1552), at(4, PICKAXE, 1548)));
    }

    @Test
    @DisplayName("同じスロットの中身が別のアイテムに替われば、耐久が多くても鳴る")
    void differentItemInSameSlotWarns() {
        // 状態機械がスロットをキーにしている以上、これを見ないと
        // 「記録より耐久の多い別個体」が一度も鳴らないまま壊れる。
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1550)));
        // 同じスロットに、より耐久の多い（＝ダメージの小さい）斧を入れた
        assertEquals(1, tick(state, at(0, AXE, 1540)));
        // 同じスロットに、より耐久の多い別のつるはしを入れた
        assertEquals(1, tick(state, at(0, PICKAXE, 1530)));
    }

    // ── 再武装 ───────────────────────────────────────────────

    @Test
    @DisplayName("警告状態から出たら再武装され、また入ったときに警告する")
    void rearmsAfterLeavingWarningState() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1550)));
        // 修理されて閾値を超えた tick（このスロットが渡ってこない）
        assertEquals(0, tick(state));
        assertFalse(state.isTracked(0));
        // 使い込んで再び警告状態へ。記録は捨てられているので入った瞬間に鳴る
        assertEquals(1, tick(state, at(0, PICKAXE, 1546)));
    }

    @Test
    @DisplayName("アイテムが無くなったら再武装される")
    void rearmsWhenItemDisappears() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1550)));
        // 壊れた / チェストへ預けた
        assertEquals(0, tick(state));
        assertEquals(0, state.trackedCount());
        // 予備を取り出して装備した
        assertEquals(1, tick(state, at(0, PICKAXE, 1548)));
    }

    @Test
    @DisplayName("片方だけ警告状態から出ても、もう片方は鳴り直さない")
    void partialRearmKeepsTheOther() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(2, tick(state, at(0, PICKAXE, 1550), at(1, AXE, 1550)));
        assertEquals(0, tick(state, at(0, PICKAXE, 1550)));        // 斧を修理した
        assertFalse(state.isTracked(1));
        assertTrue(state.isTracked(0));
        assertEquals(1, tick(state, at(0, PICKAXE, 1550), at(1, AXE, 1552)));  // 斧だけ鳴り直す
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

    // ── スロット移動（意図した代償）──────────────────────────

    @Test
    @DisplayName("道具を別スロットへ移すと 1 回だけ余分に鳴る（意図した代償）")
    void slotMoveWarnsOnceAsAcceptedCost() {
        // ★ これは不具合ではなく、同種 2 本の独立追跡と引き換えに受け入れた代償。
        //   スロットを個体の識別に使う以上、移動は「別スロットに現れた新しいアイテム」に見える。
        //   警告が「使うたびに鳴る」前提になったので、移動時の 1 回は相対的に小さいと判断した。
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1550)));
        // ホットバー 0 → 5 へ移動しただけの tick（耐久は変わっていない）
        assertEquals(1, tick(state, at(5, PICKAXE, 1550)));
        // 移動先で落ち着けば、また使うまで黙る
        assertEquals(0, tick(state, at(5, PICKAXE, 1550)));
        assertFalse(state.isTracked(0));
    }

    @Test
    @DisplayName("2 本の道具を入れ替えると、耐久が違うほうだけ鳴る")
    void swappingTwoToolsWarnsForTheIncreasedSlot() {
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(2, tick(state, at(0, PICKAXE, 1550), at(1, PICKAXE, 1546)));
        // スロット 0 と 1 の中身を入れ替えた。
        // スロット 0 は 1550 → 1546 で減少（無音）、スロット 1 は 1546 → 1550 で増加（鳴る）。
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
        assertEquals(1, tick(state, at(0, PICKAXE, 1550), at(1, AXE, 1550)));  // 斧だけ新規
    }

    // ── 防御的な不変条件 ─────────────────────────────────────

    @Test
    @DisplayName("同じスロットを同じ tick に 2 回渡しても警告は 1 回で、記録も二重更新しない")
    void duplicateSlotInSameTickWarnsOnce() {
        // 走査は 1 スロット 1 回なので本来起きない。防御的な不変条件として固定する。
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1550), at(0, PICKAXE, 1551)));
        // 2 回目の 1551 が記録されていたら、次の tick の 1551 は「増えていない」で黙るはず。
        // 鳴るということは 1550 のまま記録されている＝二重更新していない証拠。
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

    // ── 全消去 ───────────────────────────────────────────────

    @Test
    @DisplayName("clear() 後は再び警告する")
    void clearRearmsEverything() {
        // 設定で機能を切って入れ直したとき・サーバーから切断したとき用
        DurabilityWarningState state = new DurabilityWarningState();
        assertEquals(1, tick(state, at(0, PICKAXE, 1550)));
        state.clear();
        assertEquals(0, state.trackedCount());
        // 記録が消えているので、耐久が増えていなくても入った瞬間として鳴る
        assertEquals(1, tick(state, at(0, PICKAXE, 1550)));
    }
}
