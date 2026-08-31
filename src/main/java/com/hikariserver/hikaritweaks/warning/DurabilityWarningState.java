package com.hikariserver.hikaritweaks.warning;

import java.util.HashSet;
import java.util.Set;

// 耐久値警告の「1 回だけ出す」状態機械。
//
// ★ MC 非依存。ItemStack を持ち込まず String のキーだけで動かすことで
//   Minecraft を起動しないユニットテストに載せている。
//   キーの作り方（＝「同じアイテム」の定義）は DurabilityWarningHandler 側の責務。
//
// ── なぜ状態機械が要るのか ────────────────────────────────────────────
// 以前の実装はスロット番号 → "アイテム|ダメージ値" の署名を持ち、
// 署名が変わるたびに警告していた。ダメージ値が署名に入っているせいで
//   ・ダイヤのつるはし（最大 1561・閾値 16）は最後の 16 回の使用で 16 回警告する
//   ・修繕（Mending）で耐久が**戻る**と、通り過ぎていない新しい署名が次々できるので
//     経験値を拾うたびに警告が飛ぶ
// という壊れ方をしていた。「1 スロット 1 署名につき 1 回」という当時のコメントは
// 実際の挙動と逆のことを書いていた。
//
// ── いまの規則 ────────────────────────────────────────────────────────
// 「警告状態（残り耐久 <= 閾値）に**入った瞬間**に 1 回だけ警告する。
//   警告状態から**本当に出た**ときだけ再武装する。」
// 警告状態から出るのは次の 2 通りだけ。
//   ・修理されて残り耐久が閾値を超えた
//   ・そのアイテムがインベントリから無くなった（壊れた・預けた・捨てた）
// どちらも「そのキーが今 tick の走査に現れない」ことで検出できるので、
// 毎 tick 「今回見えたキー」を集めて、見えなかったキーを捨てる形にしている。
// これは ValueInterpolator.endFrame() と同じ作りである。
public final class DurabilityWarningState {

    // 警告済みのキー。次に警告状態へ入り直すまで再警告しない。
    private final Set<String> warned = new HashSet<>();
    // 今 tick の走査で警告状態だったキー
    private final Set<String> seenThisTick = new HashSet<>();

    // インベントリ走査を始める前に呼ぶ
    public void beginTick() {
        seenThisTick.clear();
    }

    // 警告状態のアイテムを見つけるたびに呼ぶ。
    //
    // @return 今回はじめて警告状態に入ったキーなら true（＝警告を出す）。
    //         すでに警告済みなら false。
    //         同じ tick 内で同じキーを 2 回渡しても警告は 1 回だけになる
    //         （同種のほぼ壊れた道具を 2 本持っているケース）。
    public boolean offer(String key) {
        seenThisTick.add(key);
        return warned.add(key);
    }

    // インベントリ走査を終えたら呼ぶ。
    // 今 tick に現れなかったキー（修理された・無くなった）を再武装する。
    // 呼ばないと warned が単調増加して二度と警告が出なくなる。
    public void endTick() {
        warned.retainAll(seenThisTick);
    }

    // 全状態を捨てる（機能を無効化したときなど）
    public void clear() {
        warned.clear();
        seenThisTick.clear();
    }

    // そのキーが警告済みかどうか（テスト用）
    public boolean isWarned(String key) {
        return warned.contains(key);
    }

    // 警告済みキーの数（テスト用）
    public int warnedCount() {
        return warned.size();
    }

    // ── 閾値まわりの純関数 ────────────────────────────────────────────

    // 最大耐久から「残りこれ以下で警告」の閾値を返す（1% 以下・最小 1）
    public static int threshold(int maxDamage) {
        return Math.max(1, (int) Math.ceil(maxDamage * 0.01));
    }

    // 残り耐久が警告状態かどうかを返す
    public static boolean inWarningState(int maxDamage, int damage) {
        return (maxDamage - damage) <= threshold(maxDamage);
    }

    // 表示用の残り耐久パーセント（切り上げ・負にはしない）
    public static int remainingPercent(int remaining, int maxDamage) {
        if (maxDamage <= 0) return 0;
        return Math.max(0, (int) Math.ceil((remaining * 100.0) / maxDamage));
    }
}
