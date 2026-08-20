package com.hikariserver.hikaritweaks.compat;

import net.minecraft.client.MinecraftClient;

// HUD 状態の取得まわりのバージョン差分を吸収するファサード。
public final class HudCompat {

    private HudCompat() {}

    // F3 デバッグ画面が表示中かどうかを返す。
    // 1.20.2 で GameOptions.debugEnabled が廃止され、
    // InGameHud.getDebugHud().shouldShowDebugHud() に移された。
    public static boolean isDebugHudShown(MinecraftClient mc) {
        //? if >=1.20.2 {
        /*return mc.inGameHud.getDebugHud().shouldShowDebugHud();
        *///?} else {
        return mc.options.debugEnabled;
        //?}
    }
}
