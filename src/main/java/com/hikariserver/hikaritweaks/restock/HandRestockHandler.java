package com.hikariserver.hikaritweaks.restock;

import com.hikariserver.hikaritweaks.compat.ItemCompat;
import com.hikariserver.hikaritweaks.compat.RegistryCompat;
import com.hikariserver.hikaritweaks.config.TweaksOptions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.List;

// Tweakeroo の handrestock 相当機能。
// 指定アイテムがホットバーで 5 個以下になったとき、インベントリから自動補充する。
// 対象アイテムは HAND_RESTOCK_LIST で管理。毎 tick 監視して足りなければ即補充。
public final class HandRestockHandler {

    // 補充を行う閾値（この個数以下になったとき補充を試みる）
    private static final int RESTOCK_THRESHOLD = 5;

    // ScreenHandler 上のオフハンドのスロット ID
    private static final int OFFHAND_SCREEN_SLOT = 45;

    // 連続補充でサーバーに負荷をかけないための最小インターバル（tick）
    private static final int RESTOCK_INTERVAL_TICKS = 5;

    // 前回補充からの経過 tick 数
    private static int ticksSinceLastRestock = 0;

    // インスタンス化を禁止するプライベートコンストラクタ
    private HandRestockHandler() {}

    // 毎 tick 呼ばれるエントリーポイント
    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        // プレイヤーまたはワールドが存在しない場合は何もしない
        if (player == null || client.world == null) return;
        // 機能が無効なら何もしない
        if (!TweaksOptions.HAND_RESTOCK.getBooleanValue()) return;

        // インターバル管理：頻繁なスロット操作でサーバーを詰まらせない
        ticksSinceLastRestock++;
        if (ticksSinceLastRestock < RESTOCK_INTERVAL_TICKS) return;

        // 補充対象アイテム ID リストを取得する
        List<String> targetIds = TweaksOptions.HAND_RESTOCK_LIST.getStrings();
        if (targetIds.isEmpty()) return;

        // ホットバー（スロット 0〜8）を走査して補充が必要なスロットを探す
        PlayerInventory inventory = player.getInventory();
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            ItemStack stack = inventory.getStack(hotbarSlot);
            // 空スロットはスキップする
            if (stack.isEmpty()) continue;

            String itemId = RegistryCompat.itemId(stack.getItem());
            // 補充対象リストにないアイテムはスキップする
            if (!targetIds.contains(itemId)) continue;

            // 閾値以下なら補充を試みる
            if (stack.getCount() <= RESTOCK_THRESHOLD) {
                boolean restocked = tryRestock(client, player, hotbarSlot);
                if (restocked) {
                    // 補充成功したらインターバルをリセットして今 tick は 1 スロットのみ処理する
                    ticksSinceLastRestock = 0;
                    return; // 1 tick に 1 スロットずつ処理して安全に
                }
            }
        }
    }

    // 指定ホットバースロットに対してインベントリから補充を行う。
    // 実際に 1 個以上動かせた場合だけ true を返す。
    // ここで no-op なのに true を返すと ticksSinceLastRestock がリセットされ、
    // 同じ操作を 5 tick ごとに永久に投げ続けることになる。
    private static boolean tryRestock(
            MinecraftClient client,
            ClientPlayerEntity player,
            int hotbarSlot
    ) {
        ClientPlayerInteractionManager im = client.interactionManager;
        if (im == null) return false;

        // 「画面を何も開いていない」状態でのみ操作する。
        //
        // currentScreenHandler instanceof PlayerScreenHandler は
        //   ・画面を何も開いていないとき
        //   ・自分のインベントリ画面（InventoryScreen）を開いているとき
        // の両方で true になるので、これだけでは後者を弾けない。
        //
        // 後者で clickSlot を割り込ませると ScreenHandler.internalOnSlotClick の
        // 「actionType != QUICK_CRAFT かつ quickCraftStage != 0 なら endQuickCraft()」
        // という分岐に落ちる。つまりドラッグ分配（左ドラッグでのスタック分け）の最中に
        // PICKUP が 1 発入るだけでドラッグが黙って中断され、そのクリック自体も捨てられる。
        // さらにカーソルに何か持っている状態なら、最初の PICKUP がその中身を
        // ソーススロットへ置く／入れ替えてしまう。
        // どちらもプレイヤーの手動操作を壊すので、画面が開いていたら何もしない。
        if (client.currentScreen != null) return false;

        // PlayerScreenHandler が開いているときのみ操作可能
        if (!(player.currentScreenHandler instanceof PlayerScreenHandler)) return false;

        int syncId = player.currentScreenHandler.syncId;
        int hotbarScreenSlot = 36 + hotbarSlot; // ScreenHandler 上のスロット ID

        PlayerInventory inventory = player.getInventory();
        ItemStack hotbarStack = inventory.getStack(hotbarSlot);
        if (hotbarStack.isEmpty()) return false;

        // インベントリ（スロット 9〜35）を逆順に走査して同じアイテムを探す
        for (int invSlot = 35; invSlot >= 9; invSlot--) {
            ItemStack source = inventory.getStack(invSlot);
            // 空スロットはスキップする
            if (source.isEmpty()) continue;

            // アイテム ID だけでなく付随データ（NBT / component）まで一致するものだけ使う。
            //
            // ID だけで判定すると、たとえば「花火（飛行時間 3）」のホットバーへ
            // 「花火（飛行時間 1）」を右クリックで入れようとしてしまう。
            // ScreenHandler.internalOnSlotClick はカーソルとスロットが canCombine でないとき
            // 「入れる」のではなく「入れ替える」（ClickType.RIGHT でも同じ挙動）ため、
            // 個数ぶんのクリックはただのスワップの往復になる。
            // 偶数回なら見た目は元通りなのに true を返して 5 tick ごとに全パケットを投げ直し、
            // 奇数回なら手に持っているアイテムが別物に化ける。
            if (!ItemCompat.canCombine(source, hotbarStack)) continue;

            // ピックアップ → ホットバーへ右クリック分配 → 残りを戻す の 3 ステップ
            int take = RestockRules.plannedMoveCount(
                    hotbarStack.getCount(), hotbarStack.getMaxCount(), source.getCount());
            // 満杯なら他のスロットを見ても入らないので打ち切る
            if (take <= 0) break;

            int invScreenSlot = invSlot; // ScreenHandler 上のインベントリスロット ID はそのまま

            // 1. ソーススロットをピックアップする
            im.clickSlot(syncId, invScreenSlot, 0, SlotActionType.PICKUP, player);
            // 2. 必要な個数だけホットバーへ右クリック（1 個ずつ）する
            for (int i = 0; i < take; i++) {
                im.clickSlot(syncId, hotbarScreenSlot, 1, SlotActionType.PICKUP, player);
            }
            // 3. カーソルに残ったアイテムをソーススロットへ戻す
            im.clickSlot(syncId, invScreenSlot, 0, SlotActionType.PICKUP, player);

            return true;
        }

        return false;
    }
}
