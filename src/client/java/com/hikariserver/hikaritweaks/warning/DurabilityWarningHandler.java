package com.hikariserver.hikaritweaks.warning;

import com.hikariserver.hikaritweaks.config.TweaksOptions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;

import java.util.HashMap;
import java.util.Map;

// 耐久値 1% 警告ハンドラ。
// Mixin を使わず ClientTickEvents から呼ぶことで refMap 問題を回避。
// スロット+署名ごとに1回だけ警告を出す。
public final class DurabilityWarningHandler {

    // 警告済みスロットの追跡マップ（スロット番号 → "アイテムID|ダメージ値" の署名）
    private static final Map<Integer, String> warnedSignatures = new HashMap<>();

    // インスタンス化を禁止するプライベートコンストラクタ
    private DurabilityWarningHandler() {}

    // 毎 tick 呼ばれるエントリーポイント
    public static void tick(MinecraftClient mc) {
        // 機能が無効な場合は警告記録をクリアして早期リターンする
        if (!TweaksOptions.DURABILITY_WARNING_ENABLED.getBooleanValue()) {
            warnedSignatures.clear();
            return;
        }
        ClientPlayerEntity player = mc.player;
        // プレイヤーまたはワールドが存在しない場合は早期リターンする
        if (player == null || mc.world == null) return;

        // インベントリの全スロットを走査して耐久値を確認する
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);

            // 空スロットまたは耐久なしアイテムは警告不要なので記録を削除する
            if (stack.isEmpty() || !stack.isDamageable()) {
                warnedSignatures.remove(slot);
                continue;
            }

            int maxDamage = stack.getMaxDamage();
            int remaining  = maxDamage - stack.getDamage();
            // 1% 以下になる閾値を計算する（最小 1）
            int threshold  = Math.max(1, (int) Math.ceil(maxDamage * 0.01));

            // 閾値より多い耐久が残っている場合は警告不要なので記録を削除する
            if (remaining > threshold) {
                warnedSignatures.remove(slot);
                continue;
            }

            // 同じアイテム・同じダメージ値の警告は重複して出さない
            String sig = stack.getItem().toString() + "|" + stack.getDamage();
            if (sig.equals(warnedSignatures.get(slot))) continue;

            // 警告記録を更新して警告メッセージとサウンドを出す
            warnedSignatures.put(slot, sig);
            int percent = Math.max(0, (int) Math.ceil((remaining * 100.0) / maxDamage));
            player.sendMessage(
                    new LiteralText(
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
                    SoundEvents.BLOCK_NOTE_BLOCK_PLING,
                    SoundCategory.MASTER,
                    1.0F,
                    1.2F
            );
        }
    }
}
