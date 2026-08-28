package com.hikariserver.hikaritweaks.compat;

import net.minecraft.item.Item;
//? if >=1.19.3 {
/*import net.minecraft.registry.Registries;
*///?} else {
import net.minecraft.util.registry.Registry;
//?}

// レジストリ参照のバージョン差分を吸収するファサード。
// 1.19.3 で net.minecraft.util.registry.Registry が
// net.minecraft.registry.Registries へ移動した。
public final class RegistryCompat {

    private RegistryCompat() {}

    // アイテムの登録 ID を "namespace:path" 形式で返す
    public static String itemId(Item item) {
        //? if >=1.19.3 {
        /*return Registries.ITEM.getId(item).toString();
        *///?} else {
        return Registry.ITEM.getId(item).toString();
        //?}
    }
}
