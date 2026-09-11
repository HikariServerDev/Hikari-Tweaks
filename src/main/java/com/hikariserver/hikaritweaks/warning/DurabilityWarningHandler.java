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
// 警告は「アイテムが警告状態（残り耐久 <= 1%）にあるあいだ、耐久が**減るたび**」に出す。
// 修繕で耐久が**戻った**ときは出さない。判定そのものは MC 非依存の
// DurabilityWarningState が持っている（ユニットテスト付き）。
public final class DurabilityWarningHandler {

    // 「どのスロットの、どのアイテムが、前回いくつ削れていたか」を覚える状態機械
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

        // ★ サウンドは 1 tick に最大 1 回。チャットは全件出す。
        //   防具は 1 回の被弾で 4 部位すべてに耐久ダメージが入るため、ほぼ壊れたフルセットで
        //   殴られると同じ座標・同じピッチの pling が 4 重なり、位相が揃って音量が加算される。
        //   溶岩や炎ならそれが毎秒続く。どのアイテムが警告されたかはチャットで分かるので、
        //   音は「この tick に何か警告が出た」ことだけを伝えれば足りる。
        boolean playedSound = false;

        // インベントリの全スロットを走査して耐久値を確認する
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);

            // 空スロットまたは耐久なしアイテムは警告対象外
            if (stack.isEmpty() || !stack.isDamageable()) continue;

            int maxDamage = stack.getMaxDamage();
            int damage = stack.getDamage();
            int remaining = maxDamage - damage;

            // 警告状態でなければ何もしない。
            // このスロットは今 tick の「見えたスロット」に入らないので、
            // 直後の endTick() で記録ごと捨てられる（＝修理されたら次にまた警告が出る）。
            if (!DurabilityWarningState.inWarningState(maxDamage, damage)) continue;

            // 前回より削れていなければ出さない。
            // ★ ここが「使うたびに鳴らす」の本体。ダメージが**増えた**ときだけ true になる。
            //   修繕で耐久が戻ったときは false（記録だけ新しい値へ更新される）。
            //   v1.1.x はキーにダメージ値そのものを入れていたため修繕でも鳴っていた。
            //   詳しい経緯は DurabilityWarningState のクラスコメントにある。
            if (!STATE.offer(slot, identity(stack), damage)) continue;

            // 警告メッセージを出す（全件）
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

            // サウンドは 1 tick 1 回だけ
            if (playedSound) continue;
            playedSound = true;
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

        // 今 tick に警告状態で現れなかったスロットの記録を捨てる
        STATE.endTick();
    }

    // 記録を全部捨てる。サーバーから切断したときに HikariTweaksClient から呼ぶ。
    //
    // ★ これを呼ばないと記録がサーバーを跨いで残る。別のサーバーの同じスロットに、
    //   記録より耐久の多い同種の道具があると「増えていない」と判定されて黙ってしまう。
    //   tick() の player == null 分岐ではなく切断イベントで捨てているのは、
    //   ディメンション移動やロード画面で一瞬 null になるたびに鳴り直すのを避けるため。
    public static void reset() {
        STATE.clear();
    }

    // 「同じアイテム」を表すキー。スロット番号は含めない（状態機械側がスロットをキーにしている）。
    //
    // ★ ダメージ値を入れてはいけない。1 ダメージごとに別のキーになり、
    //   「別のアイテムに入れ替わった」と誤判定して修繕のたびに鳴る（v1.1.x の不具合）。
    //   耐久の増減は DurabilityWarningState 側が damage の比較で扱う。
    //
    // 採ったのは「登録 ID + 表示名」。耐久値の増減では変わらず、名前を付けた道具は別物として
    // 扱える。同じスロットの中身が別のアイテムに差し替わったことの検出にも使う。
    private static String identity(ItemStack stack) {
        return RegistryCompat.itemId(stack.getItem()) + "|" + stack.getName().getString();
    }
}
