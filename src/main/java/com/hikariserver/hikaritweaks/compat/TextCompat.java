package com.hikariserver.hikaritweaks.compat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
//? if <1.19 {
import net.minecraft.network.chat.TextComponent;
//?}

public final class TextCompat {
    private TextCompat() {}

    public static Component literal(String value) {
        //? if >=1.19 {
        /*return Component.literal(value);
        *///?} else {
        return new TextComponent(value);
        //?}
    }

    public static Component empty() {
        return literal("");
    }

    public static void sendChat(LocalPlayer player, Component message) {
        //? if >=26.1 {
        /*player.sendSystemMessage(message);
        *///?} else {
        player.displayClientMessage(message, false);
        //?}
    }

    public static void sendActionBar(LocalPlayer player, Component message) {
        //? if >=26.1 {
        /*player.sendOverlayMessage(message);
        *///?} else {
        player.displayClientMessage(message, true);
        //?}
    }
}
