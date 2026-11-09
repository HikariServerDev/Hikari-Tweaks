package com.hikariserver.hikaritweaks.compat;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class WidgetCompat {
    private WidgetCompat() {}

    public static Button button(int x, int y, int width, int height,
                                      Component message, Button.OnPress onPress) {
        //? if >=1.19.3 {
        /*return Button.builder(message, onPress).bounds(x, y, width, height).build();
        *///?} else {
        return new Button(x, y, width, height, message, onPress);
        //?}
    }

    public static Button button(int x, int y, int width, int height,
                                      String label, Button.OnPress onPress) {
        return button(x, y, width, height, TextCompat.literal(label), onPress);
    }
}
