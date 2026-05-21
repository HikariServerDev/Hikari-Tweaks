package com.hikariserver.hikaritweaks.config;

import com.hikariserver.hikaritweaks.gui.HikariTweaksConfigScreen;
import com.hikariserver.hikaritweaks.scoreboard.ScoreboardHudRenderer;
import fi.dy.masa.malilib.config.IHotkeyTogglable;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.util.InfoUtils;
import net.minecraft.client.MinecraftClient;

// ホットキーのコールバックをまとめて登録するクラス。
// HikariTweaksClient#onInitializeClient() から一度だけ呼ぶ。
public final class HotkeyCallbacks {

    // インスタンス化を禁止するプライベートコンストラクタ
    private HotkeyCallbacks() {}

    // 全ホットキーのコールバックを一括登録する
    public static void init() {
        // トグル系：押すたびに ON/OFF が切り替わりアクションバーに状態表示
        attachToggle(TweaksOptions.FIX_BEACON_RANGE_FREE_CAM);
        attachToggle(TweaksOptions.DURABILITY_WARNING_ENABLED);
        attachToggle(TweaksOptions.AUTO_RESTOCK_HOTBAR);
        attachToggle(TweaksOptions.TOTEM_RESTOCK);
        attachToggle(TweaksOptions.AUTO_LITEMATICA_REFRESH);
        attachToggle(TweaksOptions.HAND_RESTOCK);

        // 設定画面を開く
        TweaksOptions.OPEN_CONFIG.getKeybind().setCallback(new IHotkeyCallback() {
            @Override
            public boolean onKeyAction(KeyAction action, IKeybind key) {
                // MC スレッドで画面遷移を行う
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null) {
                    mc.execute(() -> mc.setScreen(new HikariTweaksConfigScreen(mc.currentScreen)));
                }
                return true;
            }
        });

        // スコアボード次ページ
        TweaksOptions.SCOREBOARD_NEXT_PAGE.getKeybind().setCallback(new IHotkeyCallback() {
            @Override
            public boolean onKeyAction(KeyAction action, IKeybind key) {
                ScoreboardHudRenderer.nextPage();
                return true;
            }
        });

        // スコアボード前ページ
        TweaksOptions.SCOREBOARD_PREV_PAGE.getKeybind().setCallback(new IHotkeyCallback() {
            @Override
            public boolean onKeyAction(KeyAction action, IKeybind key) {
                ScoreboardHudRenderer.prevPage();
                return true;
            }
        });
    }

    // ConfigBooleanHotkeyed のトグルコールバックを一括で登録する。
    // 押すたびに値が反転し、アクションバーに ON/OFF を表示して設定を保存する。
    private static void attachToggle(IHotkeyTogglable config) {
        config.getKeybind().setCallback(new IHotkeyCallback() {
            @Override
            public boolean onKeyAction(KeyAction action, IKeybind key) {
                // 値を反転してアクションバーに状態を表示する
                config.toggleBooleanValue();
                boolean enabled = config.getBooleanValue();
                String status = enabled
                        ? GuiBase.TXT_GREEN + "ON"  + GuiBase.TXT_RST
                        : GuiBase.TXT_RED   + "OFF" + GuiBase.TXT_RST;
                InfoUtils.printActionbarMessage("%s: %s", config.getPrettyName(), status);
                // 変更を即座に設定ファイルへ保存する
                TweaksOptions.writeToConfig(ClientConfigManager.config);
                ClientConfigManager.save();
                return true;
            }
        });
    }
}
