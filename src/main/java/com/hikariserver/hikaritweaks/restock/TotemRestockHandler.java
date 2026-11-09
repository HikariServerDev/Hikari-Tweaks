package com.hikariserver.hikaritweaks.restock;

import com.hikariserver.hikaritweaks.compat.InventoryCompat;
import com.hikariserver.hikaritweaks.compat.ScreenCompat;
import com.hikariserver.hikaritweaks.config.TweaksOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.InventoryMenu;

public final class TotemRestockHandler {
    private static final int OFFHAND_SLOT = 40;
    private static final int NO_PENDING_SLOT = -1;
    private static final int MAX_PENDING_RETRIES = 100;

    private static boolean snapshotMainHandHadTotem;
    private static boolean snapshotOffHandHadTotem;
    private static int snapshotSelectedHotbarSlot;

    private static int pendingTargetInventorySlot = NO_PENDING_SLOT;
    private static int pendingRetries;
    private static int cooldownTicks;

    private TotemRestockHandler() {}

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            reset();
            return;
        }

        if (!TweaksOptions.TOTEM_RESTOCK.getBooleanValue()) {
            refreshSnapshot(player);
            clearPending();
            cooldownTicks = 0;
            return;
        }

        if (pendingTargetInventorySlot != NO_PENDING_SLOT) {
            if (cooldownTicks > 0) {
                cooldownTicks--;
            } else {
                if (tryRestockToSlot(client, pendingTargetInventorySlot)) {
                    cooldownTicks = 5;
                    clearPending();
                } else if (--pendingRetries <= 0) {
                    clearPending();
                }
            }
        }

        refreshSnapshot(player);
    }

    public static void onLocalTotemPopped(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }
        if (!TweaksOptions.TOTEM_RESTOCK.getBooleanValue()) {
            return;
        }

        int targetSlot = resolveTargetSlot(player);
        if (targetSlot == NO_PENDING_SLOT) {
            return;
        }

        pendingTargetInventorySlot = targetSlot;
        pendingRetries = MAX_PENDING_RETRIES;
        cooldownTicks = 2;
    }

    private static boolean tryRestockToSlot(Minecraft client, int targetInventorySlot) {
        LocalPlayer player = client.player;
        MultiPlayerGameMode interactionManager = client.gameMode;
        if (player == null || interactionManager == null) {
            return false;
        }

        if (ScreenCompat.currentScreen(client) != null) {
            return false;
        }

        if (!(player.containerMenu instanceof InventoryMenu)) {
            return false;
        }

        if (isTotem(getInventoryStack(player.getInventory(), targetInventorySlot))) {
            return false;
        }

        int sourceInventorySlot = findTotemSlot(player.getInventory(), targetInventorySlot);
        if (sourceInventorySlot < 0) {
            return false;
        }

        int sourceSlotId = toScreenHandlerSlotId(sourceInventorySlot);
        int targetSlotId = toScreenHandlerSlotId(targetInventorySlot);
        if (sourceSlotId < 0 || targetSlotId < 0) {
            return false;
        }

        int syncId = player.containerMenu.containerId;
        InventoryCompat.pickup(interactionManager, syncId, sourceSlotId, 0, player);
        InventoryCompat.pickup(interactionManager, syncId, targetSlotId, 0, player);
        InventoryCompat.pickup(interactionManager, syncId, sourceSlotId, 0, player);
        return true;
    }

    private static int resolveTargetSlot(LocalPlayer player) {
        boolean hadMain = snapshotMainHandHadTotem;
        boolean hadOff = snapshotOffHandHadTotem;

        if (!hadMain && !hadOff) {
            return NO_PENDING_SLOT;
        }

        boolean currentMainHasTotem = isTotem(player.getMainHandItem());
        boolean currentOffHasTotem = isTotem(player.getOffhandItem());

        if (hadOff && !currentOffHasTotem && (!hadMain || currentMainHasTotem)) {
            return OFFHAND_SLOT;
        }

        if (hadMain && !currentMainHasTotem && (!hadOff || currentOffHasTotem)) {
            return snapshotSelectedHotbarSlot;
        }

        if (hadOff && !hadMain) {
            return OFFHAND_SLOT;
        }

        if (hadMain) {
            return snapshotSelectedHotbarSlot;
        }

        return NO_PENDING_SLOT;
    }

    private static int findTotemSlot(Inventory inventory, int excludedSlot) {
        for (int i = 9; i < 36; i++) {
            if (i != excludedSlot && isTotem(inventory.getItem(i))) {
                return i;
            }
        }

        for (int i = 0; i < 9; i++) {
            if (i != excludedSlot && isTotem(inventory.getItem(i))) {
                return i;
            }
        }

        return -1;
    }

    private static ItemStack getInventoryStack(Inventory inventory, int inventorySlot) {
        if (inventorySlot >= 0 && inventorySlot < InventoryCompat.mainSize(inventory)) {
            return inventory.getItem(inventorySlot);
        }

        if (inventorySlot == OFFHAND_SLOT) {
            return InventoryCompat.offHandStack(inventory);
        }

        return ItemStack.EMPTY;
    }

    private static void refreshSnapshot(LocalPlayer player) {
        snapshotMainHandHadTotem = isTotem(player.getMainHandItem());
        snapshotOffHandHadTotem = isTotem(player.getOffhandItem());
        snapshotSelectedHotbarSlot = InventoryCompat.selectedSlot(player.getInventory());
    }

    private static void clearPending() {
        pendingTargetInventorySlot = NO_PENDING_SLOT;
        pendingRetries = 0;
    }

    private static int toScreenHandlerSlotId(int inventorySlot) {
        if (inventorySlot >= 9 && inventorySlot <= 35) {
            return inventorySlot;
        }

        if (inventorySlot >= 0 && inventorySlot <= 8) {
            return 36 + inventorySlot;
        }

        if (inventorySlot == OFFHAND_SLOT) {
            return 45;
        }

        return -1;
    }

    private static boolean isTotem(ItemStack stack) {
        Item item = stack.getItem();
        return !stack.isEmpty() && item == Items.TOTEM_OF_UNDYING;
    }

    private static void reset() {
        snapshotMainHandHadTotem = false;
        snapshotOffHandHadTotem = false;
        snapshotSelectedHotbarSlot = 0;
        clearPending();
        cooldownTicks = 0;
    }
}
