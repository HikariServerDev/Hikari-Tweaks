package com.hikariserver.hikaritweaks.compat;

import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;

public final class ItemCompat {
    private ItemCompat() {}

    public static boolean canCombine(ItemStack a, ItemStack b) {
        //? if >=1.20.5 {
        /*return ItemStack.isSameItemSameComponents(a, b);
        *///?} else {
        return ItemStack.isSameItemSameTags(a, b);
        //?}
    }

    public static ChatFormatting rarityFormatting(ItemStack stack) {
        //? if >=1.20.5 {
        /*return stack.getRarity().color();
        *///?} else {
        return stack.getRarity().color;
        //?}
    }
}
