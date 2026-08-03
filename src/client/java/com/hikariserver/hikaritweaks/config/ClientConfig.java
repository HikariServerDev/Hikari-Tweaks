package com.hikariserver.hikaritweaks.config;

import java.util.ArrayList;
import java.util.List;

// 設定ファイル（JSON）にシリアライズされるデータクラス。
// フィールドを追加した場合は configVersion を上げ、applyDefaults() に移行処理を追加すること。
public class ClientConfig {

    // ── バージョン管理 ─────────────────────────────────────
    // フィールド追加・削除のたびに +1 する
    public int configVersion = 6;

    // ── 補助機能 ───────────────────────────────────────────
    // MiniHUD フリーカメラ時のビーコン範囲修正の有効フラグとホットキー
    public boolean fixBeaconRangeFreeCam = true;
    public String  fixBeaconRangeFreeCamHotkey = "";

    // 耐久値 1% 警告の有効フラグとホットキー
    public boolean durabilityWarningEnabled = true;
    public String  durabilityWarningEnabledHotkey = "";

    // ホットバー自動補充の有効フラグとホットキー
    public boolean autoRestockHotbar = false;
    public String  autoRestockHotbarHotkey = "";

    // トーテム自動補充の有効フラグとホットキー
    public boolean totemRestock = false;
    public String  totemRestockHotkey = "";

    // Litematica 自動 Refresh の有効フラグとホットキー
    public boolean autoLitematicaRefresh = false;
    public String  autoLitematicaRefreshHotkey = "";

    // Tweakeroo handrestock 相当：特定アイテムが 5 個以下になったら自動補充
    public boolean handRestock = false;
    public String  handRestockHotkey = "";

    // ── リスト ─────────────────────────────────────────────
    // ホットバー自動補充の対象アイテム ID リスト（デフォルトはロケットと金にんじん）
    public List<String> hotbarRestockList = new ArrayList<>(List.of(
            "minecraft:firework_rocket",
            "minecraft:golden_carrot"
    ));
    // handRestock の補充対象アイテム ID リスト
    public List<String> handRestockList = new ArrayList<>();

    // ── ホットキー ─────────────────────────────────────────
    // 設定画面を開くホットキー（デフォルト: 右シフト）
    public String openConfigHotkey = "RIGHT_SHIFT";
    // スコアボードのページ切り替えホットキー
    public String scoreboardNextPageHotkey = "";
    public String scoreboardPrevPageHotkey = "";

    // ── スコアボード表示設定 ───────────────────────────────
    // カスタム HUD の有効フラグ
    public boolean scoreboardCustomHud      = true;
    // バニラサイドバーを非表示にするフラグ
    public boolean scoreboardHideVanilla    = true;
    // 1 ページに表示するエントリ数
    public int     scoreboardPageSize       = 10;
    // HUD の表示位置（画面幅・高さに対する % 0–100）
    public int     scoreboardPositionX      = 100;
    public int     scoreboardPositionY      = 50;
    // HUD の表示スケール（0.5–3.0）
    public float   scoreboardScale          = 1.0f;
    // HUD の各要素カラー（ARGB 形式）
    public int     scoreboardHeaderColor    = 0x66000000;
    public int     scoreboardBodyColor      = 0x4D000000;
    public int     scoreboardTextColor      = 0xFFFFFFFF;
    public int     scoreboardScoreColor     = 0xFFFF5555;
    public int     scoreboardSelfColor      = 0xFFFFFF55;
    // サーバー全体の合計スコアを表示するフラグ
    public boolean scoreboardShowServerTotal = true;
    // (Update Checker 関連フィールドは廃止されました)

    // null ガードと数値の範囲チェックを行う。ロード後に必ず呼ぶこと。
    public void normalize() {
        // null になり得るリスト・文字列フィールドを空オブジェクトで初期化する
        if (hotbarRestockList == null)      hotbarRestockList = new ArrayList<>();
        if (handRestockList == null)        handRestockList   = new ArrayList<>();
        if (scoreboardNextPageHotkey == null) scoreboardNextPageHotkey = "";
        if (scoreboardPrevPageHotkey == null) scoreboardPrevPageHotkey = "";
        if (autoLitematicaRefreshHotkey == null) autoLitematicaRefreshHotkey = "";
        if (handRestockHotkey == null)      handRestockHotkey = "";

        // 各数値を許容範囲内に収める
        scoreboardPageSize  = Math.max(1, Math.min(50, scoreboardPageSize));
        scoreboardPositionX = Math.max(0, Math.min(100, scoreboardPositionX));
        scoreboardPositionY = Math.max(0, Math.min(100, scoreboardPositionY));
        scoreboardScale     = Math.max(0.5f, Math.min(3.0f, scoreboardScale));
    }

    // 旧バージョンの設定ファイルを最新スキーマへ段階移行する。
    // configVersion が現在値より低い場合のみ実行される。
    public void applyDefaults() {
        // v0 → v1: スコアボード設定の初期値を適用する
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
        // v1 → v2: (Update Checker 設定を追加していたが v1.0.9 で廃止。configVersion だけ上げる)
        if (configVersion < 2) {
            configVersion = 2;
        }
        // v2 → v3: スコアボードページ切り替えホットキーを追加する
        if (configVersion < 3) {
            scoreboardNextPageHotkey = "";
            scoreboardPrevPageHotkey = "";
            configVersion = 3;
        }
        if (configVersion < 4) {
            // v3 以前のスライダーは 14 段階だったが v4 で 50 段階に拡張。
            // 既存値はそのまま維持（normalize() が上限を担保する）。
            configVersion = 4;
        }
        // v4 → v5: Litematica 自動 Refresh 設定を追加する
        if (configVersion < 5) {
            autoLitematicaRefresh    = false;
            autoLitematicaRefreshHotkey = "";
            configVersion = 5;
        }
        // v5 → v6: handRestock 設定を追加する
        if (configVersion < 6) {
            handRestock     = false;
            handRestockHotkey = "";
            handRestockList = new ArrayList<>();
            configVersion = 6;
        }
    }
}
