package com.hikariserver.hikaritweaks;

import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import com.hikariserver.hikaritweaks.config.HotkeyCallbacks;
import com.hikariserver.hikaritweaks.hotkey.HikariTweaksHotkeyProvider;
import com.hikariserver.hikaritweaks.restock.AutoRestockHotbarHandler;
import com.hikariserver.hikaritweaks.restock.HandRestockHandler;
import com.hikariserver.hikaritweaks.restock.TotemRestockHandler;
import com.hikariserver.hikaritweaks.scoreboard.ScoreboardHudRenderer;
import com.hikariserver.hikaritweaks.scoreboard.ScoreboardPacketClient;
import com.hikariserver.hikaritweaks.warning.DurabilityWarningHandler;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

// Hikari-Tweaks のクライアントサイドエントリーポイント。
// 各ハンドラの登録、設定読み込み、ホットキー初期化をここで行う。
public class HikariTweaksClient implements ClientModInitializer {

    // MOD の識別子・表示名・バージョン定数
    public static final String MOD_ID      = "hikari-tweaks";
    public static final String MOD_NAME    = "Hikari-Tweaks";
    public static final String MOD_VERSION = "1.0.11";

    // FabricLoader から取得したバージョン文字列のキャッシュ
    private static String cachedVersion;

    @Override
    public void onInitializeClient() {
        // 設定ファイルをロード
        ClientConfigManager.load();

        // malilib に設定ハンドラを登録（設定画面の保存・読み込みに使われる）
        ConfigManager.getInstance().registerConfigHandler(MOD_ID, ClientConfigManager.CONFIG_HANDLER);

        // ホットキーコールバックを登録
        HotkeyCallbacks.init();

        // malilib のキーバインドマネージャにホットキープロバイダを登録
        InputEventHandler.getKeybindManager().registerKeybindProvider(HikariTweaksHotkeyProvider.INSTANCE);
        InputEventHandler.getKeybindManager().updateUsedKeys();

        // スコアボードパケット受信の登録
        ScoreboardPacketClient.register();

        // 毎 tick 処理の登録
        ClientTickEvents.END_CLIENT_TICK.register(AutoRestockHotbarHandler::tick);
        ClientTickEvents.END_CLIENT_TICK.register(TotemRestockHandler::tick);
        ClientTickEvents.END_CLIENT_TICK.register(DurabilityWarningHandler::tick);
        ClientTickEvents.END_CLIENT_TICK.register(HandRestockHandler::tick);

        // サーバーから切断時の後片付け
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ScoreboardPacketClient.setOnListUpdated(null);
            ScoreboardPacketClient.setOnRankingUpdated(null);
            ScoreboardPacketClient.resetHiddenState();
        });

        // サーバー参加時の初期化とバージョン通知
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ScoreboardPacketClient.resetHiddenState();
            ScoreboardHudRenderer.resetPage();
            // プレイヤーが存在するときのみバージョン通知メッセージを送信する
            if (client.player != null) {
                client.player.sendMessage(
                        Text.of("[" + MOD_NAME + "] " + getModVersion() + " Running"), false);
            }
        });
    }

    // fabric.mod.json のバージョン文字列を返す。初回取得後はキャッシュを返す。
    public static String getModVersion() {
        // キャッシュ済みなら即返却
        if (cachedVersion != null) return cachedVersion;
        // FabricLoader 経由でバージョン文字列を取得し、見つからなければ "unknown" を使う
        cachedVersion = FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        return cachedVersion;
    }
}
