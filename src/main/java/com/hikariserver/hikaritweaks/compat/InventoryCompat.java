package com.hikariserver.hikaritweaks.compat;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;

// PlayerInventory のバージョン差分を吸収するファサード。
// 1.21.5 で main / offHand / selectedSlot の各フィールドが private 化され、
// アクセサ経由になった。
public final class InventoryCompat {

    private InventoryCompat() {}

    // メインインベントリ（36 スロット）のサイズを返す
    public static int mainSize(PlayerInventory inventory) {
        //? if >=1.21.5 {
        /*return inventory.getMainStacks().size();
        *///?} else {
        return inventory.main.size();
        //?}
    }

    // オフハンドのスタックを返す
    public static ItemStack offHandStack(PlayerInventory inventory) {
        //? if >=1.21.5 {
        /*return inventory.getStack(PlayerInventory.OFF_HAND_SLOT);
        *///?} else {
        return inventory.offHand.get(0);
        //?}
    }

    // 現在選択中のホットバースロット番号を返す
    public static int selectedSlot(PlayerInventory inventory) {
        //? if >=1.21.5 {
        /*return inventory.getSelectedSlot();
        *///?} else {
        return inventory.selectedSlot;
        //?}
    }
}
