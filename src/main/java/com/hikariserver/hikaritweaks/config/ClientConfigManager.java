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

// ClientConfig の読み書きを管理するクラス。
public final class ClientConfigManager {

    // ログ出力先（設定ファイルの破損・書き込み失敗を利用者に知らせるため）
    private static final Logger LOGGER = LoggerFactory.getLogger("Hikari-Tweaks/config");

    // JSON を整形出力するための Gson インスタンス
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // 設定ファイル名（退避ファイル名・一時ファイル名の組み立てにも使う）
    private static final String CONFIG_FILE_NAME = "hikari-tweaks.json";
    // 設定ファイルの保存先パス（FabricLoader のコンフィグディレクトリ以下）
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);

    // saveDeferred() の最短書き込み間隔（ミリ秒）
    private static final long SAVE_INTERVAL_MS = 1000L;

    // 現在の設定インスタンス（他クラスから直接参照される）
    public static ClientConfig config = new ClientConfig();

    // saveDeferred() 用：最後に実際にファイルへ書いた時刻
    private static long lastSaveTime = 0L;
    // saveDeferred() 用：間引かれて未書き込みの変更が残っているか
    private static boolean pendingSave = false;

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

    // 設定ファイルを読み込み、config フィールドへ反映する。
    //
    // ★ このメソッドは起動時の 1 回だけではない。malilib の WorldLoadHandler は
    //   ワールド／サーバーへ入るたびに ConfigManager.loadAllConfigs() を呼び、
    //   そこから登録済み IConfigHandler.load() が再実行される
    //   （malilib 0.11.8〜0.27.x のすべてで同じ。1.21 系ではサーバーの
    //   reconfiguration でも走る）。つまり config フィールドは
    //   **プレイ中に別インスタンスへ差し替わりうる**。
    //   ClientConfig の参照を掴んで持ち回るコードを書いてはならない。
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
        } catch (Exception e) {
            // ★ IOException だけを捕まえてはならない。
            //   Gson.fromJson は JSON が壊れていると **非検査例外** の
            //   JsonSyntaxException（JsonParseException 系）を投げる。これを捕まえないと
            //   例外が onInitializeClient() を突き抜け、Fabric が mod 初期化を中断して
            //   **ゲームがまったく起動しなくなる**。復旧手段が「設定ファイルを手で消す」
            //   しか無くなるため、必ず Exception で受ける。
            //
            //   壊れたファイルは黙って上書きせず .broken-<ミリ秒> へ退避してから
            //   デフォルト値で続行する（サーバー側 SyncConfig.load() と同じ作法）。
            //   退避しておけば利用者が中身を見て設定を書き戻せる。
            config = new ClientConfig();
            moveAsideCorruptFile(e);
        }
        // 読み込み直後はメモリとファイルが一致しているので、間引かれた保留は無効化する
        pendingSave = false;
        // デフォルト値適用・正規化・各オプションへの反映を行う
        config.applyDefaults();
        config.normalize();
        TweaksOptions.loadFromConfig(config);
    }

    // 読み込みに失敗した設定ファイルを .broken-<ミリ秒> へ退避する
    private static void moveAsideCorruptFile(Exception cause) {
        Path backup = CONFIG_PATH.resolveSibling(
                CONFIG_FILE_NAME + ".broken-" + System.currentTimeMillis());
        try {
            Files.move(CONFIG_PATH, backup);
            LOGGER.warn("Failed to load {}. Moved the corrupt file to {} and continuing with defaults: {}",
                    CONFIG_FILE_NAME, backup.getFileName(), cause.toString());
        } catch (IOException moveErr) {
            // 退避にも失敗した場合は、次の save() が上書きすることになる。
            // それでもデフォルト値で起動できるほうが、起動不能よりは良い。
            LOGGER.warn("Failed to load {}. Moving it aside also failed, so it will be overwritten: {}",
                    CONFIG_FILE_NAME, cause.toString());
        }
    }

    // 現在の設定を JSON ファイルへ書き出す
    public static void save() {
        // 最新のオプション値を config に書き戻してから保存する
        TweaksOptions.writeToConfig(config);
        // 間引き用の状態を更新する（即時保存したので保留は無くなる）
        lastSaveTime = System.currentTimeMillis();
        pendingSave  = false;
        try {
            // 親ディレクトリが存在しない場合は作成する
            Files.createDirectories(CONFIG_PATH.getParent());
            writeAtomically();
        } catch (IOException e) {
            LOGGER.warn("Failed to write {}: {}", CONFIG_FILE_NAME, e.toString());
        }
    }

    // 一時ファイルへ全量を書いてから本体へ差し替える（アトミック書き込み）。
    //
    // 本体を直接 truncate して書くと、書いている途中でクラッシュや電源断が起きたときに
    // **途中で切れた JSON** がそのまま残る。空ファイルなら Gson が null を返して
    // load() のガードで助かるが、切れた JSON は JsonSyntaxException になる。
    // save() はスライダーのコールバック（＝ドラッグ中に何度も）から呼ばれるので、
    // その危険な窓が 1 ドラッグにつき何十回も開くことになる。
    private static void writeAtomically() throws IOException {
        Path tmp = CONFIG_PATH.resolveSibling(CONFIG_FILE_NAME + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp)) {
            GSON.toJson(config, writer);
        }
        try {
            Files.move(tmp, CONFIG_PATH,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // 同一ディレクトリ内なので通常は起きないが、ファイルシステム次第では
            // アトミック移動が使えない。その場合は非アトミックな移動で妥協する。
            Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // 高頻度で呼ばれる値変更（スライダーのドラッグなど）用の保存。
    //
    // save() はレンダースレッド上で設定ファイル全体を同期書き込みする。
    // スライダーは 1 ステップごとにコールバックを呼ぶので、そのまま save() を繋ぐと
    // 1 回のドラッグで数十回のフルライトが走る。SAVE_INTERVAL_MS 以内の連続呼び出しは
    // 間引き、間引かれた分は画面／タブを離れるときに書き出す
    //（ScoreboardTab.onClose() の flushPendingSave() と
    //  HikariTweaksConfigScreen.removed() の save()）。
    public static void saveDeferred() {
        long now = System.currentTimeMillis();
        if (now - lastSaveTime >= SAVE_INTERVAL_MS) {
            save();
        } else {
            pendingSave = true;
        }
    }

    // 保留中の変更があれば書き出す。画面を閉じる直前など、
    // 「取りこぼしが許されない」タイミングで呼ぶ。
    public static void flushPendingSave() {
        if (pendingSave) {
            save();
        }
    }
}
