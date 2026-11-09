package com.hikariserver.hikaritweaks.config;

import com.google.common.collect.ImmutableList;
import com.hikariserver.hikaritweaks.compat.MaliLibConfigCompat;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.hotkeys.IHotkey;

import java.util.List;

public final class TweaksOptions {
    private static boolean loadingFromConfig = false;

    public static final ConfigBooleanHotkeyed FIX_BEACON_RANGE_FREE_CAM =
            new MaliLibConfigCompat.BooleanHotkeyed("fixBeaconRangeFreeCam", true, "");
    public static final ConfigBooleanHotkeyed DURABILITY_WARNING_ENABLED =
            new MaliLibConfigCompat.BooleanHotkeyed("durabilityWarningEnabled", true, "");
    public static final ConfigBooleanHotkeyed AUTO_RESTOCK_HOTBAR =
            new MaliLibConfigCompat.BooleanHotkeyed("autoRestockHotbar", false, "");
    public static final ConfigBooleanHotkeyed TOTEM_RESTOCK =
            new MaliLibConfigCompat.BooleanHotkeyed("totemRestock", false, "");
    public static final ConfigBooleanHotkeyed HAND_RESTOCK =
            new MaliLibConfigCompat.BooleanHotkeyed("handRestock", false, "");
    public static final ConfigBooleanHotkeyed VILLAGER_TRADE_FIX =
            new MaliLibConfigCompat.BooleanHotkeyed("villagerTradeFix", true, "");

    public static final ConfigStringList HOTBAR_RESTOCK_LIST = new MaliLibConfigCompat.StringList(
            "hotbarRestockList",
            ImmutableList.of("minecraft:firework_rocket", "minecraft:golden_carrot")
    );
    public static final ConfigStringList HAND_RESTOCK_LIST = new MaliLibConfigCompat.StringList(
            "handRestockList",
            ImmutableList.of()
    );

    public static final ConfigHotkey OPEN_CONFIG = new MaliLibConfigCompat.Hotkey(
            "openConfig", ClientConfig.DEFAULT_OPEN_CONFIG_HOTKEY
    );
    public static final ConfigHotkey SCOREBOARD_NEXT_PAGE = new MaliLibConfigCompat.Hotkey(
            "scoreboardNextPage", ""
    );
    public static final ConfigHotkey SCOREBOARD_PREV_PAGE = new MaliLibConfigCompat.Hotkey(
            "scoreboardPrevPage", ""
    );

    private static final List<IConfigBase> UTILITY = List.of(
            FIX_BEACON_RANGE_FREE_CAM,
            DURABILITY_WARNING_ENABLED,
            AUTO_RESTOCK_HOTBAR,
            TOTEM_RESTOCK,
            HAND_RESTOCK,
            VILLAGER_TRADE_FIX
    );
    private static final List<IConfigBase> LISTS = List.of(
            HOTBAR_RESTOCK_LIST,
            HAND_RESTOCK_LIST
    );
    private static final List<IConfigBase> HOTKEYS_LIST = List.of(
            OPEN_CONFIG,
            SCOREBOARD_NEXT_PAGE,
            SCOREBOARD_PREV_PAGE
    );

    static {
        FIX_BEACON_RANGE_FREE_CAM.setValueChangeCallback(c -> onConfigChanged());
        DURABILITY_WARNING_ENABLED.setValueChangeCallback(c -> onConfigChanged());
        AUTO_RESTOCK_HOTBAR.setValueChangeCallback(c -> onConfigChanged());
        TOTEM_RESTOCK.setValueChangeCallback(c -> onConfigChanged());
        HAND_RESTOCK.setValueChangeCallback(c -> onConfigChanged());
        VILLAGER_TRADE_FIX.setValueChangeCallback(c -> onConfigChanged());
        HOTBAR_RESTOCK_LIST.setValueChangeCallback(c -> onConfigChanged());
        HAND_RESTOCK_LIST.setValueChangeCallback(c -> onConfigChanged());
        OPEN_CONFIG.setValueChangeCallback(c -> onConfigChanged());
        SCOREBOARD_NEXT_PAGE.setValueChangeCallback(c -> onConfigChanged());
        SCOREBOARD_PREV_PAGE.setValueChangeCallback(c -> onConfigChanged());
    }

    private TweaksOptions() {}

    public static List<IConfigBase> utility() { return UTILITY; }
    public static List<IConfigBase> lists()   { return LISTS; }
    public static List<IConfigBase> hotkeys() { return HOTKEYS_LIST; }

    public static List<IHotkey> allHotkeys() {
        return List.of(
                FIX_BEACON_RANGE_FREE_CAM,
                DURABILITY_WARNING_ENABLED,
                AUTO_RESTOCK_HOTBAR,
                TOTEM_RESTOCK,
                HAND_RESTOCK,
                VILLAGER_TRADE_FIX,
                OPEN_CONFIG,
                SCOREBOARD_NEXT_PAGE,
                SCOREBOARD_PREV_PAGE
        );
    }

    public static void loadFromConfig(ClientConfig config) {
        config.normalize();
        loadingFromConfig = true;
        try {
            FIX_BEACON_RANGE_FREE_CAM.setBooleanValue(config.fixBeaconRangeFreeCam);
            FIX_BEACON_RANGE_FREE_CAM.getKeybind().setValueFromString(config.fixBeaconRangeFreeCamHotkey);
            DURABILITY_WARNING_ENABLED.setBooleanValue(config.durabilityWarningEnabled);
            DURABILITY_WARNING_ENABLED.getKeybind().setValueFromString(config.durabilityWarningEnabledHotkey);
            AUTO_RESTOCK_HOTBAR.setBooleanValue(config.autoRestockHotbar);
            AUTO_RESTOCK_HOTBAR.getKeybind().setValueFromString(config.autoRestockHotbarHotkey);
            TOTEM_RESTOCK.setBooleanValue(config.totemRestock);
            TOTEM_RESTOCK.getKeybind().setValueFromString(config.totemRestockHotkey);
            HAND_RESTOCK.setBooleanValue(config.handRestock);
            HAND_RESTOCK.getKeybind().setValueFromString(config.handRestockHotkey);
            VILLAGER_TRADE_FIX.setBooleanValue(config.villagerTradeFix);
            VILLAGER_TRADE_FIX.getKeybind().setValueFromString(config.villagerTradeFixHotkey);
            HOTBAR_RESTOCK_LIST.setStrings(config.hotbarRestockList);
            HAND_RESTOCK_LIST.setStrings(config.handRestockList);
            OPEN_CONFIG.getKeybind().setValueFromString(config.openConfigHotkey);
            SCOREBOARD_NEXT_PAGE.getKeybind().setValueFromString(config.scoreboardNextPageHotkey);
            SCOREBOARD_PREV_PAGE.getKeybind().setValueFromString(config.scoreboardPrevPageHotkey);
        } finally {
            loadingFromConfig = false;
        }
    }

    public static void writeToConfig(ClientConfig config) {
        config.fixBeaconRangeFreeCam          = FIX_BEACON_RANGE_FREE_CAM.getBooleanValue();
        config.fixBeaconRangeFreeCamHotkey    = FIX_BEACON_RANGE_FREE_CAM.getKeybind().getStringValue();
        config.durabilityWarningEnabled       = DURABILITY_WARNING_ENABLED.getBooleanValue();
        config.durabilityWarningEnabledHotkey = DURABILITY_WARNING_ENABLED.getKeybind().getStringValue();
        config.autoRestockHotbar              = AUTO_RESTOCK_HOTBAR.getBooleanValue();
        config.autoRestockHotbarHotkey        = AUTO_RESTOCK_HOTBAR.getKeybind().getStringValue();
        config.totemRestock                   = TOTEM_RESTOCK.getBooleanValue();
        config.totemRestockHotkey             = TOTEM_RESTOCK.getKeybind().getStringValue();
        config.handRestock                    = HAND_RESTOCK.getBooleanValue();
        config.handRestockHotkey              = HAND_RESTOCK.getKeybind().getStringValue();
        config.villagerTradeFix               = VILLAGER_TRADE_FIX.getBooleanValue();
        config.villagerTradeFixHotkey         = VILLAGER_TRADE_FIX.getKeybind().getStringValue();
        config.hotbarRestockList              = new java.util.ArrayList<>(HOTBAR_RESTOCK_LIST.getStrings());
        config.handRestockList                = new java.util.ArrayList<>(HAND_RESTOCK_LIST.getStrings());
        config.openConfigHotkey               = OPEN_CONFIG.getKeybind().getStringValue();
        config.scoreboardNextPageHotkey       = SCOREBOARD_NEXT_PAGE.getKeybind().getStringValue();
        config.scoreboardPrevPageHotkey       = SCOREBOARD_PREV_PAGE.getKeybind().getStringValue();
        config.normalize();
    }

    public static void applyRuntimeConfig() {
        writeToConfig(ClientConfigManager.config);
    }

    private static void onConfigChanged() {
        if (loadingFromConfig) return;
        if (ClientConfigManager.config != null) {
            applyRuntimeConfig();
            ClientConfigManager.save();
        }
    }
}
