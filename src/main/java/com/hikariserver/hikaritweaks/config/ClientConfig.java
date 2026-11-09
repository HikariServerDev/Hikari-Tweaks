package com.hikariserver.hikaritweaks.config;

import java.util.ArrayList;
import java.util.List;

public class ClientConfig {
    public int configVersion = 8;

    public boolean fixBeaconRangeFreeCam = true;
    public String  fixBeaconRangeFreeCamHotkey = "";

    public boolean durabilityWarningEnabled = true;
    public String  durabilityWarningEnabledHotkey = "";

    public boolean autoRestockHotbar = false;
    public String  autoRestockHotbarHotkey = "";

    public boolean totemRestock = false;
    public String  totemRestockHotkey = "";

    public boolean handRestock = false;
    public String  handRestockHotkey = "";

    public boolean villagerTradeFix = true;
    public String  villagerTradeFixHotkey = "";

    public List<String> hotbarRestockList = new ArrayList<>(List.of(
            "minecraft:firework_rocket",
            "minecraft:golden_carrot"
    ));
    public List<String> handRestockList = new ArrayList<>();

    public static final String DEFAULT_OPEN_CONFIG_HOTKEY = "H,T";
    public static final String OLD_DEFAULT_OPEN_CONFIG_HOTKEY = "RIGHT_SHIFT";

    public String openConfigHotkey = DEFAULT_OPEN_CONFIG_HOTKEY;
    public String scoreboardNextPageHotkey = "";
    public String scoreboardPrevPageHotkey = "";

    public boolean scoreboardCustomHud      = true;
    public boolean scoreboardHideVanilla    = true;
    public int     scoreboardPageSize       = 10;
    public int     scoreboardPositionX      = 100;
    public int     scoreboardPositionY      = 50;
    public float   scoreboardScale          = 1.0f;
    public int     scoreboardHeaderColor    = 0x66000000;
    public int     scoreboardBodyColor      = 0x4D000000;
    public int     scoreboardTextColor      = 0xFFFFFFFF;
    public int     scoreboardScoreColor     = 0xFFFF5555;
    public int     scoreboardSelfColor      = 0xFFFFFF55;
    public boolean scoreboardShowServerTotal = true;
    public boolean scoreboardMarkBots       = true;
    public boolean scoreboardHideBots       = false;

    public void normalize() {
        if (hotbarRestockList == null)      hotbarRestockList = new ArrayList<>();
        if (handRestockList == null)        handRestockList   = new ArrayList<>();
        if (scoreboardNextPageHotkey == null) scoreboardNextPageHotkey = "";
        if (scoreboardPrevPageHotkey == null) scoreboardPrevPageHotkey = "";
        if (handRestockHotkey == null)      handRestockHotkey = "";
        if (villagerTradeFixHotkey == null) villagerTradeFixHotkey = "";

        scoreboardPageSize  = Math.max(1, Math.min(50, scoreboardPageSize));
        scoreboardPositionX = Math.max(0, Math.min(100, scoreboardPositionX));
        scoreboardPositionY = Math.max(0, Math.min(100, scoreboardPositionY));
        scoreboardScale     = Math.max(0.5f, Math.min(3.0f, scoreboardScale));
    }

    public void applyDefaults() {
        if (configVersion < 1) {
            scoreboardCustomHud   = true;
            scoreboardHideVanilla = true;
            scoreboardPageSize    = scoreboardPageSize  == 0    ? 10        : scoreboardPageSize;
            scoreboardPositionX   = scoreboardPositionX == 0    ? 100       : scoreboardPositionX;
            scoreboardPositionY   = scoreboardPositionY == 0    ? 50        : scoreboardPositionY;
            scoreboardScale       = scoreboardScale      == 0f   ? 1.0f      : scoreboardScale;
            scoreboardHeaderColor = scoreboardHeaderColor == 0  ? 0x66000000 : scoreboardHeaderColor;
            scoreboardBodyColor   = scoreboardBodyColor  == 0   ? 0x4D000000 : scoreboardBodyColor;
            scoreboardTextColor   = scoreboardTextColor  == 0   ? 0xFFFFFFFF : scoreboardTextColor;
            scoreboardScoreColor  = scoreboardScoreColor == 0   ? 0xFFFF5555 : scoreboardScoreColor;
            scoreboardSelfColor   = scoreboardSelfColor  == 0   ? 0xFFFFFF55 : scoreboardSelfColor;
            scoreboardShowServerTotal = true;
            configVersion = 1;
        }
        if (configVersion < 2) {
            configVersion = 2;
        }
        if (configVersion < 3) {
            scoreboardNextPageHotkey = "";
            scoreboardPrevPageHotkey = "";
            configVersion = 3;
        }
        if (configVersion < 4) {
            configVersion = 4;
        }
        if (configVersion < 5) {
            configVersion = 5;
        }
        if (configVersion < 6) {
            handRestock     = false;
            handRestockHotkey = "";
            handRestockList = new ArrayList<>();
            configVersion = 6;
        }
        if (configVersion < 7) {
            if (OLD_DEFAULT_OPEN_CONFIG_HOTKEY.equals(openConfigHotkey)) {
                openConfigHotkey = DEFAULT_OPEN_CONFIG_HOTKEY;
            }
            configVersion = 7;
        }
        if (configVersion < 8) {
            villagerTradeFix       = true;
            villagerTradeFixHotkey = "";
            configVersion = 8;
        }
        if (configVersion < 9) {
            scoreboardMarkBots = true;
            scoreboardHideBots = false;
            configVersion = 9;
        }
    }
}
