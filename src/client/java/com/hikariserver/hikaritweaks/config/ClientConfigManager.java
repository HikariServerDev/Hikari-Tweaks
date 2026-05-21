package com.hikariserver.hikaritweaks.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fi.dy.masa.malilib.config.IConfigHandler;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

// ClientConfig の読み書きを管理するクラス。
public final class ClientConfigManager {

    // JSON を整形出力するための Gson インスタンス
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // 設定ファイルの保存先パス（FabricLoader のコンフィグディレクトリ以下）
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("hikari-tweaks.json");

    // 現在の設定インスタンス（他クラスから直接参照される）
    public static ClientConfig config = new ClientConfig();
    // malilib の ConfigManager に渡す IConfigHandler の実装（ラムダを匿名クラスで包む）
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

    // インスタンス化を禁止するプライベートコンストラクタ
    private ClientConfigManager() {}

    // 設定ファイルを読み込み、config フィールドへ反映する
    public static void load() {
        // 設定ファイルが存在しない場合はデフォルト値で新規作成する
        if (!Files.exists(CONFIG_PATH)) {
            config = new ClientConfig();
            TweaksOptions.loadFromConfig(config);
            save();
            return;
        }
        // JSON を読み込んで ClientConfig にデシリアライズする
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            ClientConfig loaded = GSON.fromJson(reader, ClientConfig.class);
            if (loaded != null) {
                config = loaded;
            }
        } catch (IOException e) {
            // 読み込み失敗時はデフォルト設定にフォールバック
            config = new ClientConfig();
        }
        // デフォルト値適用・正規化・各オプションへの反映を行う
        config.applyDefaults();
        config.normalize();
        TweaksOptions.loadFromConfig(config);
    }

    // 現在の設定を JSON ファイルへ書き出す
    public static void save() {
        // 最新のオプション値を config に書き戻してから保存する
        TweaksOptions.writeToConfig(config);
        try {
            // 親ディレクトリが存在しない場合は作成する
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            // 保存失敗は無視
        }
    }
}
