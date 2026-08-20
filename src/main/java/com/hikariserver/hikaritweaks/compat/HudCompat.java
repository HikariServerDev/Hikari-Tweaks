package com.hikariserver.hikaritweaks.compat;

import net.minecraft.client.MinecraftClient;

// HUD 状態の取得まわりのバージョン差分を吸収するファサード。
public final class HudCompat {

    private HudCompat() {}

    // F3 デバッグ画面が表示中かどうかを返す。
    // ★ この API の意味は「F3 が押されているか」**だけ**に揃えること。
    //   HUD 非表示（F1）の判定はここに混ぜず、呼び出し側で options.hudHidden を見る。
    //
    // 変遷:
    //   〜1.20.1 : GameOptions.debugEnabled（F3 フラグそのもの）
    //   1.20.2〜 : GameOptions.debugEnabled が廃止され
    //              InGameHud.getDebugHud().shouldShowDebugHud() に移された
    //   1.21.9〜 : デバッグ HUD が「ピン留め項目」方式になり、
    //              shouldShowDebugHud() が
    //                (F3 が有効 || ピン留め項目が非空) && (!hudHidden || 画面が開いている)
    //              という複合条件になった（javap で DebugHud.shouldShowDebugHud の
    //              バイトコードを確認済み）。これをそのまま使うと
    //              **F3 を押していなくてもピン留めしているだけでスコアボードが消える**。
    //              そのため F3 フラグ単体を持つ MinecraftClient.debugHudEntryList
    //              （net.minecraft.client.gui.hud.debug.DebugHudProfile）の
    //              isF3Enabled() を直接見る。
    public static boolean isDebugHudShown(MinecraftClient mc) {
        //? if >=1.21.9 {
        /*return mc.debugHudEntryList.isF3Enabled();
        *///?} elif >=1.20.2 {
        /*return mc.inGameHud.getDebugHud().shouldShowDebugHud();
        *///?} else {
        return mc.options.debugEnabled;
        //?}
    }
}
