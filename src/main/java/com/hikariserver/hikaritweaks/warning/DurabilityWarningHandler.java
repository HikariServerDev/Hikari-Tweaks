package com.hikariserver.hikaritweaks.warning;

import com.hikariserver.hikaritweaks.compat.RegistryCompat;
import com.hikariserver.hikaritweaks.compat.SoundCompat;
import com.hikariserver.hikaritweaks.compat.TextCompat;
import com.hikariserver.hikaritweaks.config.TweaksOptions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;

// 耐久値 1% 警告ハンドラ。
// Mixin を使わず ClientTickEvents から呼ぶことで refMap 問題を回避。
//
// 警告は「アイテムが警告状態（残り耐久 <= 1%）に**入った瞬間**」に 1 回だけ出し、
// 「警告状態から**出た**とき」だけ再武装する。判定そのものは MC 非依存の
// DurabilityWarningState が持っている（ユニットテスト付き）。
public final class DurabilityWarningHandler {

    // 「1 回だけ」を担保する状態機械
    private static final DurabilityWarningState STATE = new DurabilityWarningState();

    // インスタンス化を禁止するプライベートコンストラクタ
    private DurabilityWarningHandler() {}

    // 毎 tick 呼ばれるエントリーポイント
    public static void tick(MinecraftClient mc) {
        // 機能が無効な場合は警告記録をクリアして早期リターンする
        if (!TweaksOptions.DURABILITY_WARNING_ENABLED.getBooleanValue()) {
            STATE.clear();
            return;
        }
        ClientPlayerEntity player = mc.player;
        // プレイヤーまたはワールドが存在しない場合は早期リターンする
        if (player == null || mc.world == null) return;

        STATE.beginTick();

        // インベントリの全スロットを走査して耐久値を確認する
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);

            // 空スロットまたは耐久なしアイテムは警告対象外
            if (stack.isEmpty() || !stack.isDamageable()) continue;

            int maxDamage = stack.getMaxDamage();
            int remaining = maxDamage - stack.getDamage();

            // 警告状態でなければ何もしない。
            // このアイテムのキーは今 tick の「見えたキー」に入らないので、
            // 直後の endTick() で再武装される（＝修理されたら次にまた警告が出る）。
            if (!DurabilityWarningState.inWarningState(maxDamage, stack.getDamage())) continue;

            // すでに警告済みなら出さない。
            // ★ キーにダメージ値を入れてはいけない。1 ダメージごとに別のキーになり、
            //   最後の 1% を削り切るあいだ毎回警告が出る（v1.1.x の不具合）。
            //   修繕でダメージが**減る**ときも通っていない値が次々できるので、
            //   経験値を拾うたびに警告とサウンドが重なって鳴っていた。
            if (!STATE.offer(identity(stack))) continue;

            // 警告メッセージとサウンドを出す
            int percent = DurabilityWarningState.remainingPercent(remaining, maxDamage);
            player.sendMessage(
                    TextCompat.literal(
                            "§c[HikariTweaks]§f 耐久値警告: §e"
                                    + stack.getName().getString()
                                    + "§f 残り §c" + remaining
                                    + "§f (" + percent + "%)"
                    ),
                    false
            );
            // FIX⑤: ClientPlayerEntity.playSound() は MC 1.18.2 では SoundCategory 引数を取らない。
            //        world.playSound() を使ってプレイヤー位置でサウンドを再生する。
            mc.world.playSound(
                    player,
                    player.getBlockPos(),
                    SoundCompat.noteBlockPling(),
                    SoundCategory.MASTER,
                    1.0F,
                    1.2F
            );
        }

        // 今 tick に警告状態で現れなかったキーを再武装する
        STATE.endTick();
    }

    // 「同じアイテム」を表すキー。
    //
    // ★ スロット番号を入れてはいけない。以前はスロットごとに記録していたため、
    //   ほぼ壊れた道具をホットバー内で持ち替えたり並べ替えたりするだけで
    //   移動先のスロットには記録が無く、そのたびに警告が出ていた。
    // ★ ダメージ値も入れてはいけない（上の説明のとおり）。
    //
    // 採ったのは「登録 ID + 表示名」。持ち替え・並べ替えでは変わらず、
    // 耐久値の増減でも変わらない。名前を付けた道具は別物として扱える。
    //
    // 割り切り: 名前を付けていない同種の道具（無名のダイヤのつるはし 2 本）が
    // 同時に警告状態へ入ったときは 1 回しか警告しない。バニラのアイテムには
    // 個体を識別する ID が無く、全 17 ターゲットで安定して読める代替も無い。
    // 「持ち替えで鳴り直す」「1 ダメージごとに鳴る」ほうが実害が大きいと判断した。
    private static String identity(ItemStack stack) {
        return RegistryCompat.itemId(stack.getItem()) + "|" + stack.getName().getString();
    }
}
