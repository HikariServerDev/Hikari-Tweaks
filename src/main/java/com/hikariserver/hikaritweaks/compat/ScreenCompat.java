package com.hikariserver.hikaritweaks.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenCompat {
    private ScreenCompat() {}

    public static void setScreen(Minecraft client, Screen screen) {
        if (client == null) {
            return;
        }
        //? if >=26.2 {
        /*client.gui.setScreen(screen);
        *///?} elif >=1.17.1 {
        client.setScreen(screen);
        //?} else {
        /*client.openScreen(screen);
        *///?}
    }

    public static Screen currentScreen(Minecraft client) {
        if (client == null) {
            return null;
        }
        //? if >=26.2 {
        /*return client.gui.screen();
        *///?} else {
        return client.screen;
        //?}
    }
}
