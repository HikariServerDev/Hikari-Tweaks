package com.hikariserver.hikaritweaks.restock;

import com.hikariserver.hikaritweaks.config.TweaksOptions;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ホットバー自動補充ハンドラ。
// コンテナ（チェストなど）を開いたとき、設定リストのアイテムをホットバーへ補充する。
public final class AutoRestockHotbarHandler {
    // 最後に処理した画面（同じ画面で二重処理しないためのキャッシュ）
    private static Screen lastProcessedScreen;

    // インスタンス化を禁止するプライベートコンストラクタ
    private AutoRestockHotbarHandler() {}

    // 毎 tick 呼ばれるエントリーポイント
    public static void tick(MinecraftClient client) {
        // プレイヤーまたはワールドが存在しない場合はキャッシュをクリアして早期リターン
        if (client.player == null || client.world == null) {
            lastProcessedScreen = null;
            return;
        }

        // 機能が無効な場合はキャッシュをクリアして早期リターン
        if (!TweaksOptions.AUTO_RESTOCK_HOTBAR.getBooleanValue()) {
            lastProcessedScreen = null;
            return;
        }

        // コンテナ画面が開いていない場合はキャッシュをクリアして早期リターン
        Screen screen = client.currentScreen;
        if (!(screen instanceof HandledScreen<?> handledScreen)) {
            lastProcessedScreen = null;
            return;
        }

        // 同じ画面を二重処理しないようにする
        if (screen == lastProcessedScreen) {
            return;
        }

        // プレイヤーインベントリ画面は補充対象外（自分のインベントリだけ開いた場合）
        if (screen instanceof InventoryScreen || handledScreen.getScreenHandler() instanceof PlayerScreenHandler) {
            lastProcessedScreen = screen;
            return;
        }

        // 補充処理を実行してキャッシュを更新する
        process(client, handledScreen);
        lastProcessedScreen = screen;
    }

    // コンテナからホットバーへアイテムを補充する本体処理
    private static void process(MinecraftClient client, HandledScreen<?> handledScreen) {
        // エンダーチェストはスキップする（アイテムが消える危険があるため）
        if (shouldSkipForEnderChest(client)) {
            return;
        }

        ScreenHandler handler = handledScreen.getScreenHandler();
        // コンテナスロットとホットバースロットを分別して取得する
        List<Slot> containerSlots = new ArrayList<>();
        List<Slot> hotbarSlots = new ArrayList<>();

        for (Slot slot : handler.slots) {
            if (slot.inventory instanceof PlayerInventory && slot.getIndex() >= 0 && slot.getIndex() < 9) {
                hotbarSlots.add(slot);
            } else if (!(slot.inventory instanceof PlayerInventory)) {
                containerSlots.add(slot);
            }
        }

        // コンテナかホットバーが空の場合は補充できない
        if (containerSlots.isEmpty() || hotbarSlots.isEmpty()) {
            return;
        }

        // 補充したアイテム量を表示用にまとめる（表示名 → 個数）
        Map<String, Integer> movedAmounts = new LinkedHashMap<>();
        for (Slot hotbarSlot : hotbarSlots) {
            ItemStack hotbarStack = hotbarSlot.getStack();
            // 空スロットまたは満杯のスロットはスキップする
            if (hotbarStack.isEmpty() || hotbarStack.getCount() >= hotbarStack.getMaxCount()) {
                continue;
            }

            // 補充対象リストに含まれないアイテムはスキップする
            String itemId = Registry.ITEM.getId(hotbarStack.getItem()).toString();
            if (!TweaksOptions.HOTBAR_RESTOCK_LIST.getStrings().contains(itemId)) {
                continue;
            }

            // コンテナから該当スロットへ補充を試みる
            int moved = restockSlot(client, handledScreen, hotbarSlot, containerSlots);
            if (moved <= 0) {
                continue;
            }

            // 補充したアイテムをレアリティに応じた色で表示名に変換する
            String displayName = hotbarStack.getName().copy().formatted(getItemFormatting(hotbarStack)).getString();
            movedAmounts.merge(displayName, moved, Integer::sum);
        }

        // 補充があった場合はアクションバーに通知してインベントリを閉じる
        if (!movedAmounts.isEmpty()) {
            client.player.sendMessage(Text.of(buildActionbarMessage(movedAmounts)), true);
            client.player.closeHandledScreen();
        }
    }

    // 1 つのホットバースロットに対してコンテナから補充する
    private static int restockSlot(MinecraftClient client, HandledScreen<?> handledScreen, Slot hotbarSlot, List<Slot> containerSlots) {
        int movedTotal = 0;

        // コンテナの末尾から順に同じアイテムを探して補充する
        for (int i = containerSlots.size() - 1; i >= 0; i--) {
            Slot containerSlot = containerSlots.get(i);
            ItemStack containerStack = containerSlot.getStack().copy();
            ItemStack hotbarStack = hotbarSlot.getStack().copy();

            // どちらかが空の場合はスキップする
            if (containerStack.isEmpty() || hotbarStack.isEmpty()) {
                continue;
            }

            // スタック可能かどうかチェックする（種類・エンチャントなどが一致するか）
            if (!ItemStack.canCombine(containerStack, hotbarStack)) {
                continue;
            }

            // ホットバースロットに入る残り個数を計算する
            int remaining = hotbarStack.getMaxCount() - hotbarStack.getCount();
            if (remaining <= 0) {
                break;
            }

            // 移動できる個数はコンテナの個数と残り個数の小さい方
            int moveAmount = Math.min(remaining, containerStack.getCount());
            moveItems(client, handledScreen, containerSlot, hotbarSlot, moveAmount);
            movedTotal += moveAmount;
        }

        return movedTotal;
    }

    // スロット間でアイテムを移動するためのクリック操作を実行する
    private static void moveItems(MinecraftClient client, HandledScreen<?> handledScreen, Slot containerSlot, Slot hotbarSlot, int moveAmount) {
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        // interactionManager、player、moveAmount のいずれかが不正なら何もしない
        if (interactionManager == null || client.player == null || moveAmount <= 0) {
            return;
        }

        int syncId = handledScreen.getScreenHandler().syncId;
        // コンテナスロットをピックアップする
        interactionManager.clickSlot(syncId, containerSlot.id, 0, SlotActionType.PICKUP, client.player);
        // ホットバースロットへ 1 個ずつ右クリックで配置する
        for (int i = 0; i < moveAmount; i++) {
            interactionManager.clickSlot(syncId, hotbarSlot.id, 1, SlotActionType.PICKUP, client.player);
        }
        // カーソルに残ったアイテムをコンテナスロットへ戻す
        interactionManager.clickSlot(syncId, containerSlot.id, 0, SlotActionType.PICKUP, client.player);
    }

    // エンダーチェストをターゲットにしている場合は補充をスキップする判定
    private static boolean shouldSkipForEnderChest(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult blockHitResult)) {
            return false;
        }

        // ブロックエンティティがエンダーチェストならスキップする
        BlockEntity blockEntity = client.world.getBlockEntity(blockHitResult.getBlockPos());
        return blockEntity instanceof EnderChestBlockEntity;
    }

    // アイテムのレアリティに対応したフォーマット（色）を返す
    private static Formatting getItemFormatting(ItemStack stack) {
        return stack.getRarity().formatting;
    }

    // 補充結果をアクションバー用の文字列に変換する
    private static String buildActionbarMessage(Map<String, Integer> movedAmounts) {
        List<String> contents = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : movedAmounts.entrySet()) {
            contents.add(entry.getKey() + " +" + entry.getValue());
        }
        return "ホットバー自動補充: " + String.join(", ", contents);
    }
}
