package com.hikariserver.hikaritweaks.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

// 画面遷移のバージョン差分を吸収するファサード。
// 1.17.1 で MinecraftClient.openScreen(Screen) が setScreen(Screen) にリネームされた。
public final class ScreenCompat {

    private ScreenCompat() {}

    // 指定した画面を開く（null を渡すと画面を閉じる）
    public static void setScreen(MinecraftClient client, Screen screen) {
        if (client == null) {
            return;
        }
        //? if >=1.17.1 {
        client.setScreen(screen);
        //?} else {
        /*client.openScreen(screen);
        *///?}
    }
}
