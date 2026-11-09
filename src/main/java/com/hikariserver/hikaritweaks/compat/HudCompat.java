package com.hikariserver.hikaritweaks.compat;

import net.minecraft.client.Minecraft;

public final class HudCompat {
    private HudCompat() {}

    public static boolean isDebugHudShown(Minecraft mc) {
        //? if >=1.21.11 {
        /*return mc.debugEntries.isOverlayVisible();
        *///?} elif >=1.21.9 {
        /*return mc.debugEntries.isF3Visible();
        *///?} elif >=1.20.2 {
        /*return mc.gui.getDebugOverlay().showDebugScreen();
        *///?} else {
        return mc.options.renderDebug;
        //?}
    }

    public static boolean isHudHidden(Minecraft mc) {
        //? if >=26.2 {
        /*return mc.gui.hud.isHidden();
        *///?} else {
        return mc.options.hideGui;
        //?}
    }
}
