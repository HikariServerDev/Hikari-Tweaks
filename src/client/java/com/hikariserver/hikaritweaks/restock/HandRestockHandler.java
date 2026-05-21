package com.hikariserver.hikaritweaks.restock;

import com.hikariserver.hikaritweaks.config.TweaksOptions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.registry.Registry;

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

            String itemId = Registry.ITEM.getId(stack.getItem()).toString();
            // 補充対象リストにないアイテムはスキップする
            if (!targetIds.contains(itemId)) continue;

            // 閾値以下なら補充を試みる
            if (stack.getCount() <= RESTOCK_THRESHOLD) {
                boolean restocked = tryRestock(client, player, hotbarSlot, itemId, stack.getMaxCount());
                if (restocked) {
                    // 補充成功したらインターバルをリセットして今 tick は 1 スロットのみ処理する
                    ticksSinceLastRestock = 0;
                    return; // 1 tick に 1 スロットずつ処理して安全に
                }
            }
        }
    }

    // 指定ホットバースロットに対してインベントリから補充を行う。
    // 補充できた場合は true を返す。
    private static boolean tryRestock(
            MinecraftClient client,
            ClientPlayerEntity player,
            int hotbarSlot,
            String itemId,
            int maxCount
    ) {
        ClientPlayerInteractionManager im = client.interactionManager;
        if (im == null) return false;

        // PlayerScreenHandler が開いているときのみ操作可能
        if (!(player.currentScreenHandler instanceof PlayerScreenHandler)) return false;

        int syncId = player.currentScreenHandler.syncId;
        int hotbarScreenSlot = 36 + hotbarSlot; // ScreenHandler 上のスロット ID

        // インベントリ（スロット 9〜35）を逆順に走査して同じアイテムを探す
        PlayerInventory inventory = player.getInventory();
        for (int invSlot = 35; invSlot >= 9; invSlot--) {
            ItemStack source = inventory.getStack(invSlot);
            // 空スロットはスキップする
            if (source.isEmpty()) continue;

            String sourceId = Registry.ITEM.getId(source.getItem()).toString();
            // アイテム ID が一致するものだけ使う
            if (!sourceId.equals(itemId)) continue;

            // ピックアップ → ホットバーへ右クリック分配 → 残りを戻す の 3 ステップ
            int hotbarCount = inventory.getStack(hotbarSlot).getCount();
            int needed = maxCount - hotbarCount;
            if (needed <= 0) break;

            int take = Math.min(needed, source.getCount());
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
