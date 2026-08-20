package com.hikariserver.hikaritweaks.compat;

import net.minecraft.util.Identifier;

// Identifier 生成のバージョン差分を吸収するファサード。
// 1.21 で public コンストラクタが削除され Identifier.of(...) になった。
public final class IdCompat {

    private IdCompat() {}

    // namespace と path から Identifier を作る
    public static Identifier of(String namespace, String path) {
        //? if >=1.21 {
        /*return Identifier.of(namespace, path);
        *///?} else {
        return new Identifier(namespace, path);
        //?}
    }
}
