package com.hikariserver.hikaritweaks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fi.dy.masa.malilib.config.IConfigHandler;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ClientConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Hikari-Tweaks/config");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE_NAME = "hikari-tweaks.json";
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);

    private static final long SAVE_INTERVAL_MS = 1000L;

    public static ClientConfig config = new ClientConfig();

    private static long lastSaveTime = 0L;
    private static boolean pendingSave = false;

    public static final IConfigHandler CONFIG_HANDLER = new IConfigHandler() {
        @Override
        public void load() {
            ClientConfigManager.load();
        }

        @Override
        public void save() {
            ClientConfigManager.save();
        }
    };

    private ClientConfigManager() {}

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            config = new ClientConfig();
            TweaksOptions.loadFromConfig(config);
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            ClientConfig loaded = GSON.fromJson(reader, ClientConfig.class);
            if (loaded != null) {
                config = loaded;
            }
        } catch (Exception e) {
            config = new ClientConfig();
            moveAsideCorruptFile(e);
        }
        pendingSave = false;
        config.applyDefaults();
        config.normalize();
        TweaksOptions.loadFromConfig(config);
    }

    private static void moveAsideCorruptFile(Exception cause) {
        Path backup = CONFIG_PATH.resolveSibling(
                CONFIG_FILE_NAME + ".broken-" + System.currentTimeMillis());
        try {
            Files.move(CONFIG_PATH, backup);
            LOGGER.warn("Failed to load {}. Moved the corrupt file to {} and continuing with defaults: {}",
                    CONFIG_FILE_NAME, backup.getFileName(), cause.toString());
        } catch (IOException moveErr) {
            LOGGER.warn("Failed to load {}. Moving it aside also failed, so it will be overwritten: {}",
                    CONFIG_FILE_NAME, cause.toString());
        }
    }

    public static void save() {
        TweaksOptions.writeToConfig(config);
        lastSaveTime = System.currentTimeMillis();
        pendingSave  = false;
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            writeAtomically();
        } catch (IOException e) {
            LOGGER.warn("Failed to write {}: {}", CONFIG_FILE_NAME, e.toString());
        }
    }

    private static void writeAtomically() throws IOException {
        Path tmp = CONFIG_PATH.resolveSibling(CONFIG_FILE_NAME + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp)) {
            GSON.toJson(config, writer);
        }
        try {
            Files.move(tmp, CONFIG_PATH,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void saveDeferred() {
        long now = System.currentTimeMillis();
        if (now - lastSaveTime >= SAVE_INTERVAL_MS) {
            save();
        } else {
            pendingSave = true;
        }
    }

    public static void flushPendingSave() {
        if (pendingSave) {
            save();
        }
    }
}
