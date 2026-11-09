package com.hikariserver.hikaritweaks;

import com.hikariserver.hikaritweaks.compat.TextCompat;
import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import com.hikariserver.hikaritweaks.config.HotkeyCallbacks;
import com.hikariserver.hikaritweaks.hotkey.HikariTweaksHotkeyProvider;
import com.hikariserver.hikaritweaks.restock.AutoRestockHotbarHandler;
import com.hikariserver.hikaritweaks.restock.HandRestockHandler;
import com.hikariserver.hikaritweaks.restock.TotemRestockHandler;
import com.hikariserver.hikaritweaks.scoreboard.RankingV2Client;
import com.hikariserver.hikaritweaks.scoreboard.ScoreboardHudRenderer;
import com.hikariserver.hikaritweaks.scoreboard.ScoreboardPacketClient;
import com.hikariserver.hikaritweaks.warning.DurabilityWarningHandler;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public class HikariTweaksClient implements ClientModInitializer {
    public static final String MOD_ID      = "hikari-tweaks";
    public static final String MOD_NAME    = "Hikari-Tweaks";
    public static final String MOD_VERSION = "1.0.11";

    private static String cachedVersion;

    @Override
    public void onInitializeClient() {
        ClientConfigManager.load();

        ConfigManager.getInstance().registerConfigHandler(MOD_ID, ClientConfigManager.CONFIG_HANDLER);

        HotkeyCallbacks.init();

        InputEventHandler.getKeybindManager().registerKeybindProvider(HikariTweaksHotkeyProvider.INSTANCE);
        InputEventHandler.getKeybindManager().updateUsedKeys();

        ScoreboardPacketClient.register();

        ClientTickEvents.END_CLIENT_TICK.register(AutoRestockHotbarHandler::tick);
        ClientTickEvents.END_CLIENT_TICK.register(TotemRestockHandler::tick);
        ClientTickEvents.END_CLIENT_TICK.register(DurabilityWarningHandler::tick);
        ClientTickEvents.END_CLIENT_TICK.register(HandRestockHandler::tick);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ScoreboardPacketClient.setOnListUpdated(null);
            ScoreboardPacketClient.setOnRankingUpdated(null);
            ScoreboardPacketClient.resetHiddenState();
            client.execute(RankingV2Client::reset);
            client.execute(DurabilityWarningHandler::reset);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ScoreboardPacketClient.resetHiddenState();
            RankingV2Client.reset();
            ScoreboardHudRenderer.resetPage();
            DurabilityWarningHandler.reset();
            if (client.player != null) {
                TextCompat.sendChat(client.player,
                        Component.nullToEmpty(I18n.get("hikaritweaks.message.running", MOD_NAME, getModVersion())));
            }
        });
    }

    public static String getModVersion() {
        if (cachedVersion != null) return cachedVersion;
        cachedVersion = FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        return cachedVersion;
    }
}
