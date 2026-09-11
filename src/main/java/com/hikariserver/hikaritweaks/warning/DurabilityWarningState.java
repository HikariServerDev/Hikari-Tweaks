package com.hikariserver.hikaritweaks.warning;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// 耐久値警告の状態機械。
//
// ★ MC 非依存。ItemStack を持ち込まず「スロット番号 + 同一性キー + ダメージ値」だけで動かし、
//   Minecraft を起動しないユニットテストに載せている。
//   同一性キーの作り方（＝「同じアイテム」の定義）は DurabilityWarningHandler 側の責務。
//
// ── いまの規則 ────────────────────────────────────────────────────────
// 「警告状態（残り耐久 <= 閾値）のあいだ、そのアイテムの耐久が**減るたび**に警告する。
//   耐久が**戻った**（修繕）ときは警告しないが、記録は新しい値へ更新する。」
// 警告状態から出る（修理されて閾値を超えた／インベントリから無くなった）と記録ごと捨てる。
// どちらも「そのスロットが今 tick の走査に現れない」ことで検出できるので、
// 毎 tick 「今回見えたスロット」を集めて、見えなかったスロットを捨てる形にしている。
// これは ValueInterpolator.endFrame() と同じ作りである。
//
// ── ★ v1.1.x の不具合との違い（消さないこと）──────────────────────────
// 「最後の 1% を削るあいだ毎 tick 鳴る」は v1.1.x では**不具合**として修正された挙動で、
// v1.2.3 で**仕様として**復活させたものである。何が違うのか:
//   ・v1.1.x: キーに**ダメージ値そのもの**が入っていた。署名が 1 ダメージごとに変わるので
//     「初めて見た署名か」でしか判定できず、修繕で耐久が**戻る**ときも通っていない署名が
//     次々できて、経験値を拾うたびに警告が飛んでいた。
//   ・いま: ダメージを**前回値と比較**し、**増えたときだけ**鳴らす。減少では鳴らない。
// つまり「1 ダメージごとに鳴る」こと自体は意図した挙動であり、直してはいけない。
// 直してよいのは「修繕で鳴る」ほうだけである。
//
// ── ★ スロット番号をキーにしている理由（消さないこと）──────────────────
// v1.1.x〜v1.2.2 は「登録 ID + 表示名」だけをキーにしていた。持ち替えで鳴り直さない利点が
// あったが、無名の同種の道具を 2 本持つと 1 本しか追跡できず、**もう 1 本を使っても鳴らない**。
// 「使うたびに鳴らす」が前提になった以上こちらの実害が大きいので、スロット番号をキーにした。
// 代償として、道具を別スロットへ移すと 1 回だけ余分に鳴る（意図した代償）。
//
// 同一性キーを文字列に連結せず「スロットをキー・同一性キーを値」にしているのは、
// スロットの中身が別個体に入れ替わったのを検出するため。連結方式だと、
// スロット 0 の記録が damage 1550 のときに damage 1548 の別のつるはしをそこへ入れると
// 1548 > 1550 が偽になり、**一度も鳴らないまま壊れる**。
public final class DurabilityWarningState {

    // 1 スロット分の記録
    private static final class Entry {
        final String identity;
        final int damage;

        Entry(String identity, int damage) {
            this.identity = identity;
            this.damage = damage;
        }
    }

    // 追跡中のスロット。警告状態から出た（＝今 tick 現れなかった）ら捨てる。
    private final Map<Integer, Entry> tracked = new HashMap<>();
    // 今 tick の走査で警告状態だったスロット
    private final Set<Integer> seenThisTick = new HashSet<>();

    // インベントリ走査を始める前に呼ぶ
    public void beginTick() {
        seenThisTick.clear();
    }

    // 警告状態のアイテムを見つけるたびに呼ぶ。
    //
    // @param slot     インベントリのスロット番号
    // @param identity 「同じアイテム」を表すキー（DurabilityWarningHandler.identity()）
    // @param damage   そのアイテムの現在のダメージ値（大きいほど壊れている）
    // @return 警告を出すべきなら true。
    //         ・そのスロットを新しく追跡し始めた（＝警告状態に入った）
    //         ・スロットの中身が別のアイテムに入れ替わった
    //         ・ダメージが増えた（＝使われた）
    //         のいずれか。ダメージが減った（修繕）／変わっていないときは false。
    public boolean offer(int slot, String identity, int damage) {
        // 同じ tick に同じスロットを 2 回渡されても 1 回しか警告しない。
        // 走査は 1 スロット 1 回なので本来起きないが、記録が二重更新されると
        // 「増えた」の判定が壊れるため防御的に弾いている。
        if (!seenThisTick.add(slot)) return false;

        Entry prev = tracked.put(slot, new Entry(identity, damage));
        if (prev == null) return true;                      // 新しく警告状態へ入った
        if (!prev.identity.equals(identity)) return true;   // 別の個体に入れ替わった
        return damage > prev.damage;                        // 増えた＝使われた
    }

    // インベントリ走査を終えたら呼ぶ。
    // 今 tick に現れなかったスロット（修理された・無くなった）の記録を捨てる。
    // 呼ばないと tracked が単調増加し、古いダメージ値が残って警告が出なくなる。
    public void endTick() {
        tracked.keySet().retainAll(seenThisTick);
    }

    // 全状態を捨てる（機能を無効化したとき・サーバーから切断したとき）
    public void clear() {
        tracked.clear();
        seenThisTick.clear();
    }

    // そのスロットを追跡中かどうか（テスト用）
    public boolean isTracked(int slot) {
        return tracked.containsKey(slot);
    }

    // 追跡中のスロット数（テスト用）
    public int trackedCount() {
        return tracked.size();
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
