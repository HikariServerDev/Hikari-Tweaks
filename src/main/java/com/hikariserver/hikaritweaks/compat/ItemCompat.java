package com.hikariserver.hikaritweaks.compat;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Formatting;

// ItemStack まわりのバージョン差分を吸収するファサード。
// 1.20.5 の component 導入で NBT ベースの API が置き換えられた。
public final class ItemCompat {

    private ItemCompat() {}

    // 2 つのスタックがまとめられる（アイテム種別と付随データが一致する）かどうかを返す。
    // 1.20.5 で ItemStack.canCombine が areItemsAndComponentsEqual に置き換わった。
    public static boolean canCombine(ItemStack a, ItemStack b) {
        //? if >=1.20.5 {
        /*return ItemStack.areItemsAndComponentsEqual(a, b);
        *///?} else {
        return ItemStack.canCombine(a, b);
        //?}
    }

    // アイテムのレアリティに対応した Formatting を返す。
    // 1.20.5 で Rarity.formatting フィールドが private 化され getFormatting() になった。
    public static Formatting rarityFormatting(ItemStack stack) {
        //? if >=1.20.5 {
        /*return stack.getRarity().getFormatting();
        *///?} else {
        return stack.getRarity().formatting;
        //?}
    }
}
