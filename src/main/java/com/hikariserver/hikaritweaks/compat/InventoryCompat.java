package com.hikariserver.hikaritweaks.compat;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class InventoryCompat {
    private InventoryCompat() {}

    public static int mainSize(Inventory inventory) {
        //? if >=1.21.5 {
        /*return inventory.getNonEquipmentItems().size();
        *///?} else {
        return inventory.items.size();
        //?}
    }

    public static ItemStack offHandStack(Inventory inventory) {
        //? if >=1.21.5 {
        /*return inventory.getItem(Inventory.SLOT_OFFHAND);
        *///?} else {
        return inventory.offhand.get(0);
        //?}
    }

    public static int selectedSlot(Inventory inventory) {
        //? if >=1.21.5 {
        /*return inventory.getSelectedSlot();
        *///?} else {
        return inventory.selected;
        //?}
    }

    public static void pickup(MultiPlayerGameMode gameMode,
                              int syncId, int slotId, int button, Player player) {
        //? if >=26.1 {
        /*gameMode.handleContainerInput(syncId, slotId, button,
                net.minecraft.world.inventory.ContainerInput.PICKUP, player);
        *///?} else {
        gameMode.handleInventoryMouseClick(syncId, slotId, button,
                net.minecraft.world.inventory.ClickType.PICKUP, player);
        //?}
    }

    public static void quickMove(MultiPlayerGameMode gameMode,
                                 int syncId, int slotId, int button, Player player) {
        //? if >=26.1 {
        /*gameMode.handleContainerInput(syncId, slotId, button,
                net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);
        *///?} else {
        gameMode.handleInventoryMouseClick(syncId, slotId, button,
                net.minecraft.world.inventory.ClickType.QUICK_MOVE, player);
        //?}
    }
}
