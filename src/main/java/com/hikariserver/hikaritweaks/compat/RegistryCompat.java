package com.hikariserver.hikaritweaks.compat;

import net.minecraft.world.item.Item;
//? if >=1.19.3 {
/*import net.minecraft.core.registries.BuiltInRegistries;
*///?} else {
import net.minecraft.core.Registry;
//?}

public final class RegistryCompat {
    private RegistryCompat() {}

    public static Iterable<Item> items() {
        //? if >=1.19.3 {
        /*return BuiltInRegistries.ITEM;
        *///?} else {
        return Registry.ITEM;
        //?}
    }

    public static String itemId(Item item) {
        //? if >=1.19.3 {
        /*return BuiltInRegistries.ITEM.getKey(item).toString();
        *///?} else {
        return Registry.ITEM.getKey(item).toString();
        //?}
    }
}
