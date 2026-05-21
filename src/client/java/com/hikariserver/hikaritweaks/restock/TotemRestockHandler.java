package com.hikariserver.hikaritweaks.restock;

import com.hikariserver.hikaritweaks.config.TweaksOptions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

// トーテム自動補充ハンドラ。
// トーテムが使用されたことを MixinClientPlayNetworkHandler が検知し
// onLocalTotemPopped() を呼び出すことで補充処理を開始する。
public final class TotemRestockHandler {
    // インベントリスロット上のオフハンドのスロット ID
    private static final int OFFHAND_SLOT = 40;
    // 補充ターゲットが未設定の状態を示すセンチネル値
    private static final int NO_PENDING_SLOT = -1;
    // 補充リトライの最大回数
    private static final int MAX_PENDING_RETRIES = 100;

    // トーテムポップ前のスナップショット（どちらの手にトーテムがあったか）
    private static boolean snapshotMainHandHadTotem;
    private static boolean snapshotOffHandHadTotem;
    // トーテムポップ前に選択されていたホットバースロット
    private static int snapshotSelectedHotbarSlot;

    // 補充先スロット（NO_PENDING_SLOT = 補充不要）
    private static int pendingTargetInventorySlot = NO_PENDING_SLOT;
    // 残りリトライ回数
    private static int pendingRetries;
    // 補充前のクールダウン tick 数（インベントリ同期を待つため）
    private static int cooldownTicks;

    // インスタンス化を禁止するプライベートコンストラクタ
    private TotemRestockHandler() {}

    // 毎 tick 呼ばれるエントリーポイント
    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        // プレイヤーまたはワールドが存在しない場合はリセットして早期リターン
        if (player == null || client.world == null) {
            reset();
            return;
        }

        // 機能が無効な場合はスナップショットだけ更新して補充はスキップする
        if (!TweaksOptions.TOTEM_RESTOCK.getBooleanValue()) {
            refreshSnapshot(player);
            clearPending();
            cooldownTicks = 0;
            return;
        }

        // 補充待ちがある場合はクールダウン消化後に補充を試みる
        if (pendingTargetInventorySlot != NO_PENDING_SLOT) {
            if (cooldownTicks > 0) {
                cooldownTicks--;
            } else {
                if (tryRestockToSlot(client, pendingTargetInventorySlot)) {
                    // 補充成功したらクールダウンを設定して pending をクリアする
                    cooldownTicks = 5;
                    clearPending();
                } else if (--pendingRetries <= 0) {
                    // リトライ上限に達したら諦める
                    clearPending();
                }
            }
        }

        // 次 tick のために現在のスナップショットを更新する
        refreshSnapshot(player);
    }

    // MixinClientPlayNetworkHandler からトーテム使用パケット受信時に呼ばれる
    public static void onLocalTotemPopped(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        // 機能が無効なら何もしない
        if (!TweaksOptions.TOTEM_RESTOCK.getBooleanValue()) {
            return;
        }

        // スナップショットからトーテムが使われた手のスロットを特定する
        int targetSlot = resolveTargetSlot(player);
        if (targetSlot == NO_PENDING_SLOT) {
            return;
        }

        // 補充ターゲットを設定してクールダウン後に処理を開始する
        pendingTargetInventorySlot = targetSlot;
        pendingRetries = MAX_PENDING_RETRIES;
        // Wait a couple of ticks for inventory sync after totem-pop status packet.
        cooldownTicks = 2;
    }

    // 指定スロットへトーテムを補充する（成功したら true を返す）
    private static boolean tryRestockToSlot(MinecraftClient client, int targetInventorySlot) {
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        if (player == null || interactionManager == null) {
            return false;
        }

        // プレイヤーインベントリ画面が開いているときのみ操作できる
        if (!(player.currentScreenHandler instanceof PlayerScreenHandler)) {
            return false;
        }

        // 対象スロットにトーテムがまだある場合はインベントリ同期を待つ
        if (isTotem(getInventoryStack(player.getInventory(), targetInventorySlot))) {
            // Totem may still appear here before server inventory sync arrives.
            // Keep pending and retry instead of completing early.
            return false;
        }

        // インベントリ内で補充元のトーテムスロットを探す
        int sourceInventorySlot = findTotemSlot(player.getInventory(), targetInventorySlot);
        if (sourceInventorySlot < 0) {
            return false;
        }

        // インベントリスロット ID を ScreenHandler スロット ID に変換する
        int sourceSlotId = toScreenHandlerSlotId(sourceInventorySlot);
        int targetSlotId = toScreenHandlerSlotId(targetInventorySlot);
        if (sourceSlotId < 0 || targetSlotId < 0) {
            return false;
        }

        // ソースをピックアップ → ターゲットへ置く → ソースへ残りを戻す
        int syncId = player.currentScreenHandler.syncId;
        interactionManager.clickSlot(syncId, sourceSlotId, 0, SlotActionType.PICKUP, player);
        interactionManager.clickSlot(syncId, targetSlotId, 0, SlotActionType.PICKUP, player);
        interactionManager.clickSlot(syncId, sourceSlotId, 0, SlotActionType.PICKUP, player);
        return true;
    }

    // スナップショットと現在の手の状態を比較して補充先スロットを決定する
    private static int resolveTargetSlot(ClientPlayerEntity player) {
        boolean hadMain = snapshotMainHandHadTotem;
        boolean hadOff = snapshotOffHandHadTotem;

        // どちらの手にもトーテムがなかった場合は補充不要
        if (!hadMain && !hadOff) {
            return NO_PENDING_SLOT;
        }

        boolean currentMainHasTotem = isTotem(player.getMainHandStack());
        boolean currentOffHasTotem = isTotem(player.getOffHandStack());

        // オフハンドのトーテムが消えた場合はオフハンドへ補充する
        if (hadOff && !currentOffHasTotem && (!hadMain || currentMainHasTotem)) {
            return OFFHAND_SLOT;
        }

        // メインハンドのトーテムが消えた場合はそのホットバースロットへ補充する
        if (hadMain && !currentMainHasTotem && (!hadOff || currentOffHasTotem)) {
            return snapshotSelectedHotbarSlot;
        }

        // フォールバック：オフハンドを優先する
        if (hadOff && !hadMain) {
            return OFFHAND_SLOT;
        }

        if (hadMain) {
            return snapshotSelectedHotbarSlot;
        }

        return NO_PENDING_SLOT;
    }

    // インベントリ内で指定スロット以外のトーテムを探して返す（なければ -1）
    private static int findTotemSlot(PlayerInventory inventory, int excludedSlot) {
        // まずメインインベントリ（スロット 9〜35）を優先して探す
        for (int i = 9; i < 36; i++) {
            if (i != excludedSlot && isTotem(inventory.getStack(i))) {
                return i;
            }
        }

        // 次にホットバー（スロット 0〜8）を探す
        for (int i = 0; i < 9; i++) {
            if (i != excludedSlot && isTotem(inventory.getStack(i))) {
                return i;
            }
        }

        return -1;
    }

    // インベントリスロット ID からアイテムスタックを返す（オフハンド対応）
    private static ItemStack getInventoryStack(PlayerInventory inventory, int inventorySlot) {
        // メインインベントリスロットの場合
        if (inventorySlot >= 0 && inventorySlot < inventory.main.size()) {
            return inventory.getStack(inventorySlot);
        }

        // オフハンドスロットの場合
        if (inventorySlot == OFFHAND_SLOT) {
            return inventory.offHand.get(0);
        }

        return ItemStack.EMPTY;
    }

    // 現在の手の状態をスナップショットとして保存する
    private static void refreshSnapshot(ClientPlayerEntity player) {
        snapshotMainHandHadTotem = isTotem(player.getMainHandStack());
        snapshotOffHandHadTotem = isTotem(player.getOffHandStack());
        snapshotSelectedHotbarSlot = player.getInventory().selectedSlot;
    }

    // 補充待ち状態をクリアする
    private static void clearPending() {
        pendingTargetInventorySlot = NO_PENDING_SLOT;
        pendingRetries = 0;
    }

    // インベントリスロット ID を ScreenHandler スロット ID に変換する
    private static int toScreenHandlerSlotId(int inventorySlot) {
        // メインインベントリ（スロット 9〜35）はそのまま
        if (inventorySlot >= 9 && inventorySlot <= 35) {
            return inventorySlot;
        }

        // ホットバー（スロット 0〜8）は 36 を足して変換する
        if (inventorySlot >= 0 && inventorySlot <= 8) {
            return 36 + inventorySlot;
        }

        // オフハンドは ScreenHandler 上のスロット 45
        if (inventorySlot == OFFHAND_SLOT) {
            return 45;
        }

        return -1;
    }

    // アイテムスタックが不死のトーテムかどうかを返す
    private static boolean isTotem(ItemStack stack) {
        Item item = stack.getItem();
        return !stack.isEmpty() && item == Items.TOTEM_OF_UNDYING;
    }

    // 全状態をリセットする（ワールドを離れた時などに使う）
    private static void reset() {
        snapshotMainHandHadTotem = false;
        snapshotOffHandHadTotem = false;
        snapshotSelectedHotbarSlot = 0;
        clearPending();
        cooldownTicks = 0;
    }
}
