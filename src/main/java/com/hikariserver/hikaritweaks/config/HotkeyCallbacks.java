package com.hikariserver.hikaritweaks.config;

import com.hikariserver.hikaritweaks.compat.ScreenCompat;
import com.hikariserver.hikaritweaks.gui.HikariTweaksConfigScreen;
import com.hikariserver.hikaritweaks.scoreboard.ScoreboardHudRenderer;
import fi.dy.masa.malilib.config.IHotkeyTogglable;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.util.InfoUtils;
import net.minecraft.client.Minecraft;

public final class HotkeyCallbacks {
    private HotkeyCallbacks() {}

    public static void init() {
        attachToggle(TweaksOptions.FIX_BEACON_RANGE_FREE_CAM);
        attachToggle(TweaksOptions.DURABILITY_WARNING_ENABLED);
        attachToggle(TweaksOptions.AUTO_RESTOCK_HOTBAR);
        attachToggle(TweaksOptions.TOTEM_RESTOCK);
        attachToggle(TweaksOptions.HAND_RESTOCK);
        attachToggle(TweaksOptions.VILLAGER_TRADE_FIX);

        TweaksOptions.OPEN_CONFIG.getKeybind().setCallback(new IHotkeyCallback() {
            @Override
            public boolean onKeyAction(KeyAction action, IKeybind key) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> ScreenCompat.setScreen(mc, new HikariTweaksConfigScreen(ScreenCompat.currentScreen(mc))));
                }
                return true;
            }
        });

        TweaksOptions.SCOREBOARD_NEXT_PAGE.getKeybind().setCallback(new IHotkeyCallback() {
            @Override
            public boolean onKeyAction(KeyAction action, IKeybind key) {
                ScoreboardHudRenderer.nextPage();
                return true;
            }
        });

        TweaksOptions.SCOREBOARD_PREV_PAGE.getKeybind().setCallback(new IHotkeyCallback() {
            @Override
            public boolean onKeyAction(KeyAction action, IKeybind key) {
                ScoreboardHudRenderer.prevPage();
                return true;
            }
        });
    }

    private static void attachToggle(IHotkeyTogglable config) {
        config.getKeybind().setCallback(new IHotkeyCallback() {
            @Override
            public boolean onKeyAction(KeyAction action, IKeybind key) {
                config.toggleBooleanValue();
                boolean enabled = config.getBooleanValue();
                String status = enabled
                        ? GuiBase.TXT_GREEN + "ON"  + GuiBase.TXT_RST
                        : GuiBase.TXT_RED   + "OFF" + GuiBase.TXT_RST;
                InfoUtils.printActionbarMessage("%s: %s", config.getPrettyName(), status);
                TweaksOptions.writeToConfig(ClientConfigManager.config);
                ClientConfigManager.save();
                return true;
            }
        });
    }
}
