package com.hikariserver.hikaritweaks.config;

import com.google.common.collect.ImmutableList;
import com.hikariserver.hikaritweaks.compat.MaliLibConfigCompat;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.hotkeys.IHotkey;

import java.util.List;

// 全設定オプションの定義。
// UTILITY / LISTS / HOTKEYS_LIST の 3 グループに分けて GUI タブと対応させている。
public final class TweaksOptions {

    // loadFromConfig() 中に値変更コールバックが走って保存が二重にならないよう制御するフラグ
    private static boolean loadingFromConfig = false;

    // ── 補助機能 ───────────────────────────────────────────────────────────────

    // NOTE: 設定の「表示名」と「ホバーコメント」は MaLiLibConfigCompat が解決する。
    // 生の ConfigBooleanHotkeyed / ConfigHotkey / ConfigStringList を直接 new しないこと。
    //
    // malilib は 17 ターゲットで 17 バージョンをピン留めしているサードパーティで、
    // 名前とコメントの翻訳解決が 0.10 / 0.11.8 / 0.21.10 / 0.27.17 の 4 回作り替えられている。
    // 特に comment 引数に "" を渡すと 0.21.10〜0.26.8（MC 1.21.1〜1.21.10）だけが
    // "Durability Warning Enabled Comment?" というプレースホルダを表示する（v1.1.0 のバグ）。
    // どの引数に何を渡すべきか・どの世代がどう振る舞うかは
    // compat/MaliLibConfigCompat.java の先頭コメントと docs/multiversion/PLAN.md §3.10 に記録した。
    //
    // lang キーは設定名から機械的に導く（"config.name.<name.lower>" / "config.comment.<name.lower>"）。
    // キーの過不足は LangFormatTest が検証する。

    // MiniHUD フリーカメラ時のビーコン範囲をプレイヤー位置基準に修正するオプション
    public static final ConfigBooleanHotkeyed FIX_BEACON_RANGE_FREE_CAM =
            new MaliLibConfigCompat.BooleanHotkeyed("fixBeaconRangeFreeCam", true, "");
    // 耐久値 1% 以下になったときにチャットへ警告を出すオプション
    public static final ConfigBooleanHotkeyed DURABILITY_WARNING_ENABLED =
            new MaliLibConfigCompat.BooleanHotkeyed("durabilityWarningEnabled", true, "");
    // コンテナを開いた時にホットバーへ自動補充するオプション
    public static final ConfigBooleanHotkeyed AUTO_RESTOCK_HOTBAR =
            new MaliLibConfigCompat.BooleanHotkeyed("autoRestockHotbar", false, "");
    // 使われたトーテムをインベントリから補充するオプション
    public static final ConfigBooleanHotkeyed TOTEM_RESTOCK =
            new MaliLibConfigCompat.BooleanHotkeyed("totemRestock", false, "");
    // (Litematica 自動 Refresh は v1.0.10 で削除)
    // Tweakeroo handrestock 相当。リストのアイテムが 5 個以下になったらインベントリから自動補充する。
    public static final ConfigBooleanHotkeyed HAND_RESTOCK =
            new MaliLibConfigCompat.BooleanHotkeyed("handRestock", false, "");

    // ── リスト ─────────────────────────────────────────────────────────────────

    // ホットバー自動補充の対象アイテム ID リスト
    public static final ConfigStringList HOTBAR_RESTOCK_LIST = new MaliLibConfigCompat.StringList(
            "hotbarRestockList",
            ImmutableList.of("minecraft:firework_rocket", "minecraft:golden_carrot")
    );
    // 手持ち自動補充の対象アイテム ID リスト
    public static final ConfigStringList HAND_RESTOCK_LIST = new MaliLibConfigCompat.StringList(
            "handRestockList",
            ImmutableList.of()
    );

    // ── ホットキー ─────────────────────────────────────────────────────────────

    // 設定画面を開くホットキー（デフォルト: H を押しながら T）
    // malilib のキー組み合わせ表現はカンマ区切り（表示は "H + T"）。
    // 既定値は ClientConfig と必ず揃えること。
    public static final ConfigHotkey OPEN_CONFIG = new MaliLibConfigCompat.Hotkey(
            "openConfig", ClientConfig.DEFAULT_OPEN_CONFIG_HOTKEY
    );
    // スコアボードの次ページへ切り替えるホットキー
    public static final ConfigHotkey SCOREBOARD_NEXT_PAGE = new MaliLibConfigCompat.Hotkey(
            "scoreboardNextPage", ""
    );
    // スコアボードの前ページへ切り替えるホットキー
    public static final ConfigHotkey SCOREBOARD_PREV_PAGE = new MaliLibConfigCompat.Hotkey(
            "scoreboardPrevPage", ""
    );

    // ── タブ別グループ ─────────────────────────────────────────────────────────

    // 「補助機能」タブに表示する設定リスト
    private static final List<IConfigBase> UTILITY = List.of(
            FIX_BEACON_RANGE_FREE_CAM,
            DURABILITY_WARNING_ENABLED,
            AUTO_RESTOCK_HOTBAR,
            TOTEM_RESTOCK,
            HAND_RESTOCK
    );
    // 「リスト」タブに表示する設定リスト
    private static final List<IConfigBase> LISTS = List.of(
            HOTBAR_RESTOCK_LIST,
            HAND_RESTOCK_LIST
    );
    // 「ホットキー」タブに表示する設定リスト
    private static final List<IConfigBase> HOTKEYS_LIST = List.of(
            OPEN_CONFIG,
            SCOREBOARD_NEXT_PAGE,
            SCOREBOARD_PREV_PAGE
    );

    // 値変更コールバックを登録。設定変更があれば即座に保存する。
    static {
        FIX_BEACON_RANGE_FREE_CAM.setValueChangeCallback(c -> onConfigChanged());
        DURABILITY_WARNING_ENABLED.setValueChangeCallback(c -> onConfigChanged());
        AUTO_RESTOCK_HOTBAR.setValueChangeCallback(c -> onConfigChanged());
        TOTEM_RESTOCK.setValueChangeCallback(c -> onConfigChanged());
        HAND_RESTOCK.setValueChangeCallback(c -> onConfigChanged());
        HOTBAR_RESTOCK_LIST.setValueChangeCallback(c -> onConfigChanged());
        HAND_RESTOCK_LIST.setValueChangeCallback(c -> onConfigChanged());
        OPEN_CONFIG.setValueChangeCallback(c -> onConfigChanged());
        SCOREBOARD_NEXT_PAGE.setValueChangeCallback(c -> onConfigChanged());
        SCOREBOARD_PREV_PAGE.setValueChangeCallback(c -> onConfigChanged());
    }

    // インスタンス化を禁止するプライベートコンストラクタ
    private TweaksOptions() {}

    // 補助機能タブの設定リストを返す
    public static List<IConfigBase> utility() { return UTILITY; }
    // リストタブの設定リストを返す
    public static List<IConfigBase> lists()   { return LISTS; }
    // ホットキータブの設定リストを返す
    public static List<IConfigBase> hotkeys() { return HOTKEYS_LIST; }

    // malilib の HotkeyProvider に渡す全ホットキーリスト
    public static List<IHotkey> allHotkeys() {
        return List.of(
                FIX_BEACON_RANGE_FREE_CAM,
                DURABILITY_WARNING_ENABLED,
                AUTO_RESTOCK_HOTBAR,
                TOTEM_RESTOCK,
                HAND_RESTOCK,
                OPEN_CONFIG,
                SCOREBOARD_NEXT_PAGE,
                SCOREBOARD_PREV_PAGE
        );
    }

    // 設定ファイルから読み込んだ値を各オプションへ反映する
    public static void loadFromConfig(ClientConfig config) {
        // 正規化して範囲外の値を修正する
        config.normalize();
        // コールバックによる二重保存を防ぐためフラグを立てる
        loadingFromConfig = true;
        try {
            // 各オプションの boolean 値とホットキーを設定ファイルの値で上書きする
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
            HOTBAR_RESTOCK_LIST.setStrings(config.hotbarRestockList);
            HAND_RESTOCK_LIST.setStrings(config.handRestockList);
            OPEN_CONFIG.getKeybind().setValueFromString(config.openConfigHotkey);
            SCOREBOARD_NEXT_PAGE.getKeybind().setValueFromString(config.scoreboardNextPageHotkey);
            SCOREBOARD_PREV_PAGE.getKeybind().setValueFromString(config.scoreboardPrevPageHotkey);
        } finally {
            // 必ずフラグを解除してコールバックが正常動作するようにする
            loadingFromConfig = false;
        }
    }

    // 現在の各オプション値を設定ファイル用データクラスへ書き出す
    public static void writeToConfig(ClientConfig config) {
        // 各オプションの boolean 値とホットキーを config フィールドへ書き込む
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
        config.hotbarRestockList              = new java.util.ArrayList<>(HOTBAR_RESTOCK_LIST.getStrings());
        config.handRestockList                = new java.util.ArrayList<>(HAND_RESTOCK_LIST.getStrings());
        config.openConfigHotkey               = OPEN_CONFIG.getKeybind().getStringValue();
        config.scoreboardNextPageHotkey       = SCOREBOARD_NEXT_PAGE.getKeybind().getStringValue();
        config.scoreboardPrevPageHotkey       = SCOREBOARD_PREV_PAGE.getKeybind().getStringValue();
        // 書き込み後に正規化して範囲外の値を修正する
        config.normalize();
    }

    // 実行時設定を config に反映するヘルパー
    public static void applyRuntimeConfig() {
        writeToConfig(ClientConfigManager.config);
    }

    // 設定値が変更されたときに呼ばれる。loadFromConfig() 中は保存をスキップする。
    private static void onConfigChanged() {
        // 読み込み中は二重保存を防ぐためスキップする
        if (loadingFromConfig) return;
        if (ClientConfigManager.config != null) {
            applyRuntimeConfig();
            ClientConfigManager.save();
        }
    }
}
