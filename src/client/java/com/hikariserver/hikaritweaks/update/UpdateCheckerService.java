package com.hikariserver.hikaritweaks.update;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hikariserver.hikaritweaks.HikariTweaksClient;
import com.hikariserver.hikaritweaks.config.ClientConfig;
import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// GitHub Releases を参照して更新を通知するサービス。
public final class UpdateCheckerService {
    // JSON パーサー（スレッドセーフなので共有する）
    private static final Gson GSON = new Gson();
    // ダエモンスレッドで動作する単一スレッドのスケジューラ
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "hikari-tweaks-update-checker");
        t.setDaemon(true);
        return t;
    });

    // start() が一度だけ呼ばれるよう制御するフラグ
    private static volatile boolean started = false;
    // 現在チェック中かどうかのフラグ（重複実行防止）
    private static volatile boolean checking = false;
    // 最後に取得した最新リリース情報（null = 更新なし または未チェック）
    private static volatile UpdateInfo latestUpdate = null;

    // JOIN 後に通知待ちのクライアントを保持する（チェック完了後に通知するため）
    private static volatile MinecraftClient pendingNotifyClient = null;

    // インスタンス化を禁止するプライベートコンストラクタ
    private UpdateCheckerService() {}

    // スケジューラを起動する（2 回目以降は何もしない）
    public static void start() {
        if (started) {
            return;
        }
        started = true;
        // 起動 8 秒後に初回チェックを行い、その後は 60 秒ごとに間隔チェックを実行する
        EXECUTOR.scheduleWithFixedDelay(UpdateCheckerService::checkIfDueSafe, 8, 60, TimeUnit.SECONDS);
        EXECUTOR.execute(UpdateCheckerService::checkIfDueSafe);
    }

    // サーバーに JOIN したときに呼ぶ。
    // チェック済みなら即通知、未完了なら完了後に通知されるよう pending に登録してから
    // 強制チェックを走らせる。
    public static void onJoin(MinecraftClient client) {
        ClientConfig config = ClientConfigManager.config;
        // アップデートチェックまたは JOIN 通知が無効な場合は何もしない
        if (config == null || !config.updateCheckerEnabled || !config.updateNotifyOnJoin) {
            return;
        }
        if (latestUpdate != null) {
            // チェック済みなので即通知する
            notifyPlayer(client, latestUpdate);
            return;
        }
        // チェック未完了 → pending 登録して強制チェックを実行する
        pendingNotifyClient = client;
        EXECUTOR.execute(UpdateCheckerService::checkNowSafe);
    }

    // サーバーから DISCONNECT したときに呼ぶ。pending をクリアする。
    public static void onDisconnect() {
        pendingNotifyClient = null;
    }

    // ── 内部チェックロジック ─────────────────────────────────────────────────────

    // 例外をすべてキャッチして安全に間隔チェックを実行するラッパー
    private static void checkIfDueSafe() {
        try {
            checkIfDue(false);
        } catch (Exception ignored) {
            // ネットワーク由来の失敗は静かに無視する
        }
    }

    // 例外をすべてキャッチして安全に即時チェックを実行するラッパー
    private static void checkNowSafe() {
        try {
            checkIfDue(true);
        } catch (Exception ignored) {
        }
    }

    // @param force true のとき interval チェックを無視して即実行する
    private static void checkIfDue(boolean force) {
        ClientConfig config = ClientConfigManager.config;
        // アップデートチェックが無効なら何もしない
        if (config == null || !config.updateCheckerEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        long intervalMs = Math.max(5, config.updateCheckIntervalMinutes) * 60_000L;
        // force=false のとき、インターバル未到達またはチェック中なら早期リターン
        if (!force && (now - config.updateLastCheckedAt < intervalMs || checking)) {
            return;
        }
        if (checking) {
            return;
        }

        checking = true;
        try {
            // GitHub API からリリース情報を取得する
            ReleaseInfo release = null;
            try {
                release = fetchLatestRelease(config);
            } catch (IOException ignored) {
                // ネットワークエラー時は次回再試行する
            }
            // チェック時刻を記録する
            config.updateLastCheckedAt = now;
            // 取得したバージョンが現在より新しければ latestUpdate を更新する
            if (release != null && VersionComparator.isNewer(release.version, HikariTweaksClient.getModVersion())) {
                latestUpdate = new UpdateInfo(release.version, release.name, release.url);
            }
            ClientConfigManager.save();

            // pending な JOIN クライアントがいれば通知する
            MinecraftClient pending = pendingNotifyClient;
            if (pending != null && latestUpdate != null) {
                pendingNotifyClient = null;
                notifyPlayer(pending, latestUpdate);
            }
        } finally {
            // 必ずフラグを解除する
            checking = false;
        }
    }

    // ── GitHub API ────────────────────────────────────────────────────────────

    // GitHub Releases API から最新リリース情報を取得して返す
    private static ReleaseInfo fetchLatestRelease(ClientConfig config) throws IOException {
        String owner = config.updateGithubOwner == null ? "" : config.updateGithubOwner.trim();
        String repo = config.updateGithubRepo == null ? "" : config.updateGithubRepo.trim();
        // オーナーかリポジトリ名が空の場合は取得不可
        if (owner.isEmpty() || repo.isEmpty()) {
            return null;
        }

        // プレリリースを含めない場合は /releases/latest を使う
        if (!config.updateIncludePrerelease) {
            JsonObject obj = requestJsonObject(apiUrl(owner, repo, "/releases/latest"));
            if (obj == null || obj.has("draft") && obj.get("draft").getAsBoolean()) {
                return null;
            }
            return toReleaseInfo(obj, config);
        }

        // プレリリースを含める場合は最新 10 件から最初の非ドラフトを選ぶ
        JsonArray arr = requestJsonArray(apiUrl(owner, repo, "/releases?per_page=10"));
        if (arr == null) {
            return null;
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            // ドラフトはスキップする
            if (obj.has("draft") && obj.get("draft").getAsBoolean()) {
                continue;
            }
            return toReleaseInfo(obj, config);
        }
        return null;
    }

    // JSON オブジェクトから ReleaseInfo を構築する
    private static ReleaseInfo toReleaseInfo(JsonObject obj, ClientConfig config) {
        // tag_name を取得してバージョン文字列として使う
        String tag = getString(obj, "tag_name");
        if (tag == null || tag.isBlank()) {
            return null;
        }
        String name = getString(obj, "name");
        if (name == null || name.isBlank()) {
            name = tag;
        }
        // URL は設定の上書きを優先し、なければ html_url、それもなければデフォルト URL を使う
        String url = getString(obj, "html_url");
        if (config.updateReleaseUrlOverride != null && !config.updateReleaseUrlOverride.isBlank()) {
            url = config.updateReleaseUrlOverride.trim();
        } else if (url == null || url.isBlank()) {
            url = "https://github.com/" + config.updateGithubOwner + "/" + config.updateGithubRepo + "/releases";
        }
        return new ReleaseInfo(tag, name, url);
    }

    // URL に GET リクエストを送り、レスポンスを JSON オブジェクトとして返す
    private static JsonObject requestJsonObject(String url) throws IOException {
        String body = request(url);
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonElement element = JsonParser.parseString(body);
        return element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    // URL に GET リクエストを送り、レスポンスを JSON 配列として返す
    private static JsonArray requestJsonArray(String url) throws IOException {
        String body = request(url);
        if (body == null || body.isBlank()) {
            return null;
        }
        JsonElement element = JsonParser.parseString(body);
        return element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    // URL に GET リクエストを送り、成功時はレスポンスボディを文字列で返す
    private static String request(String url) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("GET");
        // タイムアウトを 8 秒に設定する
        con.setConnectTimeout(8_000);
        con.setReadTimeout(8_000);
        con.setRequestProperty("Accept", "application/vnd.github+json");
        con.setRequestProperty("User-Agent", "Hikari-Tweaks-UpdateChecker");
        con.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

        int code = con.getResponseCode();
        // 成功時は InputStream、エラー時は ErrorStream を読む
        InputStream stream = (code >= 200 && code < 300) ? con.getInputStream() : con.getErrorStream();
        if (stream == null) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            // 成功レスポンスのみ内容を返す
            if (code >= 200 && code < 300) {
                return sb.toString();
            }
            return null;
        } finally {
            // コネクションを確実に切断する
            con.disconnect();
        }
    }

    // GitHub API の URL を組み立てるヘルパー
    private static String apiUrl(String owner, String repo, String path) {
        return "https://api.github.com/repos/" + owner + "/" + repo + path;
    }

    // JSON オブジェクトから指定キーの文字列を安全に取得する
    private static String getString(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }

    // ── 通知 ──────────────────────────────────────────────────────────────────

    // プレイヤーにアップデート通知メッセージをチャットへ送る
    private static void notifyPlayer(MinecraftClient client, UpdateInfo info) {
        // MC スレッドで実行する必要がある
        client.execute(() -> {
            // プレイヤーが存在しない場合は通知できない
            if (client.player == null) {
                return;
            }
            ClientConfig config = ClientConfigManager.config;
            if (config == null) {
                return;
            }
            String current = HikariTweaksClient.getModVersion();

            // ── 1行目：アップデート通知 ──────────────────────────────
            // 水色のプレフィックス + 黄色の本文 + 緑の [Open Release] リンク
            MutableText prefix = new LiteralText("[" + HikariTweaksClient.MOD_NAME + "] ")
                    .setStyle(Style.EMPTY.withColor(Formatting.AQUA));
            MutableText body = new LiteralText("Update available: " + info.version + " (current: " + current + ") ")
                    .setStyle(Style.EMPTY.withColor(Formatting.YELLOW));
            MutableText link = new LiteralText("[Open Release]")
                    .setStyle(Style.EMPTY
                            .withColor(Formatting.GREEN)
                            .withUnderline(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, info.url)));

            client.player.sendMessage(prefix.copy().append(body).append(link), false);

            // ── 2行目：URLをそのまま表示（コピーしやすいようにクリック可能） ──
            MutableText urlLabel = new LiteralText("  → ")
                    .setStyle(Style.EMPTY.withColor(Formatting.DARK_GRAY));
            MutableText urlText = new LiteralText(info.url)
                    .setStyle(Style.EMPTY
                            .withColor(Formatting.GRAY)
                            .withUnderline(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, info.url)));

            client.player.sendMessage(urlLabel.copy().append(urlText), false);


        });
    }

    // GitHub API から取得したリリース情報を保持するレコード
    private record ReleaseInfo(String version, String name, String url) {}

    // チェック済みのアップデート情報を保持するレコード
    private record UpdateInfo(String version, String name, String url) {}
}
