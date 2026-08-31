package com.hikariserver.hikaritweaks.scoreboard;

import com.hikariserver.hikaritweaks.compat.IdCompat;
import com.hikariserver.hikaritweaks.compat.NetCompat;
import com.hikariserver.hikaritweaks.scoreboard.v1.RankingV1Data;
import com.hikariserver.hikaritweaks.scoreboard.v1.ScoreboardV1Codec;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

// Client-side packet utility for HikariScoreBoard integration.
public final class ScoreboardPacketClient {

    private static final Logger LOGGER = LoggerFactory.getLogger("Hikari-Tweaks/scoreboard");

    // パケット識別子の定義
    private static final Identifier RANKING_DATA         = IdCompat.of("hikariscoreboard", "ranking_data");
    private static final Identifier PLAYER_LIST_REQUEST  = IdCompat.of("hikariscoreboard", "player_list_request");
    private static final Identifier PLAYER_LIST_RESPONSE = IdCompat.of("hikariscoreboard", "player_list_response");
    private static final Identifier BLOCK_TOGGLE         = IdCompat.of("hikariscoreboard", "block_toggle");
    // 旧 VANILLA_SIDEBAR_CONTROL チャネルは廃止。
    // HikariScoreBoard v1.4.3 以降、サーバーは Tweaks 検知時にバニラ Objective を
    // 送らなくなったため、クライアント側で隠す必要が無くなった。

    // 壊れたパケットのログを絞る間隔（ナノ秒）。
    // 壊れたサーバーが毎 tick 送ってくるとログが埋まるため（v2 側と同じ扱い）。
    private static final long MALFORMED_LOG_INTERVAL_NANOS = 5_000_000_000L;

    // サーバーから受信したプレイヤーリストのキャッシュ
    private static List<PlayerListEntry> cachedList = new ArrayList<>();
    // 最後に受信したランキングデータのキャッシュ（volatile でスレッド安全に保つ）
    private static volatile RankingV1Data cachedRanking = null;
    // サーバーから hide 指示を受けた状態。true の間はデータが届いても HUD を表示しない
    private static volatile boolean serverHidden = false;
    // プレイヤーリスト更新時に呼ぶコールバック
    private static Consumer<List<PlayerListEntry>> onListUpdated = null;
    // ランキング更新時に呼ぶコールバック
    private static Runnable onRankingUpdated = null;

    // 最後に壊れたパケットを記録した時刻（ナノ秒）
    private static long lastMalformedLogNanos = Long.MIN_VALUE;

    // インスタンス化を禁止するプライベートコンストラクタ
    private ScoreboardPacketClient() {}

    // サーバーからのパケットを受信するハンドラをグローバルに登録する。
    //
    // ┌─ 受信コールバックから例外を出してはいけない理由 ─────────────────┐
    // │ ここに登録するラムダを呼ぶのは Fabric のネットワーキング API で、    │
    // │ 呼び出し元のスタックがこのファイルからは見えない。実際の実行先は     │
    // │   MC 1.20.5 未満 … legacy ハンドラは **netty のイベントループ上**    │
    // │     で走る。抜けた例外は netty の exceptionCaught に落ち、           │
    // │     ClientConnection がそれを致命的として扱うのでサーバー切断になる。 │
    // │   MC 1.20.5 以降 … ハンドラはクライアントスレッドで走るので、        │
    // │     抜けた例外はそのままクラッシュレポートになる。                    │
    // │ どちらも「壊れたパケット 1 通でセッションが終わる」ことに変わりはない。│
    // │ したがってパースは必ず try/catch で囲み、読めなければパケットを       │
    // │ 捨てるだけにすること（v2 = RankingV2Client と同じ扱い）。            │
    // └──────────────────────────────────────────────────────────────────┘
    public static void register() {
        // ランキングデータパケットを受信してキャッシュに保存する
        NetCompat.registerReceiver(RANKING_DATA,
                (client, buf) -> {
                    RankingV1Data data = decode(buf, ScoreboardV1Codec::decodeRanking, "ranking_data");
                    // 壊れていたらこのパケットは捨てる（キャッシュは前回の値のまま）
                    if (data == null) return;

                    // 非表示フラグが立っている場合はランキングをクリアして早期リターン
                    if (data.hidden()) {
                        client.execute(() -> {
                            serverHidden = true;
                            cachedRanking = null;
                        });
                        return;
                    }

                    // MC スレッドでキャッシュを更新してコールバックを呼ぶ
                    client.execute(() -> {
                        serverHidden = false;
                        cachedRanking = data;
                        if (onRankingUpdated != null) {
                            onRankingUpdated.run();
                        }
                    });
                });

        // プレイヤーリストレスポンスを受信してキャッシュに保存する
        NetCompat.registerReceiver(PLAYER_LIST_RESPONSE,
                (client, buf) -> {
                    List<PlayerListEntry> list =
                            decode(buf, ScoreboardV1Codec::decodePlayerList, "player_list_response");
                    // 壊れていたらこのパケットは捨てる（キャッシュは前回の値のまま）
                    if (list == null) return;

                    // MC スレッドでリストを更新してコールバックを呼ぶ
                    client.execute(() -> {
                        cachedList = list;
                        if (onListUpdated != null) {
                            onListUpdated.accept(list);
                        }
                    });
                });

        // 旧 VANILLA_SIDEBAR_CONTROL レシーバはここから削除。
        // HikariScoreBoard v1.4.3 以降は Tweaks 検知時にバニラ Objective を送らないため不要。

        // 差分プロトコル hikariscoreboard:ranking_v2 の受信登録。
        //
        // ★ 上の RANKING_DATA（v1）と**同じタイミング**で登録すること。
        //   サーバーは canSend(RANKING_V2) が true かどうかだけで v2 を選ぶので
        //   （docs/ranking-v2-protocol.md §1）、登録が遅れると v1 で送られてしまう。
        // ★ v1 の受信経路は消さないこと。サーバーが旧版のままの構成があり得る（§5.1）。
        RankingV2Client.register();

        // 送信専用チャンネルを登録する（1.20.5 以降は事前登録が必須）
        NetCompat.registerSendChannel(PLAYER_LIST_REQUEST);
        NetCompat.registerSendChannel(BLOCK_TOGGLE);
    }

    // 受信バッファを byte[] に落として MC 非依存のコーデックへ渡す共通処理。
    // 読めなければ null を返すので、呼び出し側はそのパケットを捨てること。
    //
    // buf はコールバックを抜けると無効になるのでここで読み切る。
    private static <T> T decode(PacketByteBuf buf, Function<byte[], T> decoder, String channel) {
        try {
            byte[] payload = new byte[buf.readableBytes()];
            buf.readBytes(payload);
            return decoder.apply(payload);
        } catch (Exception e) {
            // 例外を外へ出さないこと（register() のコメント参照）。
            logMalformed(channel, e);
            return null;
        }
    }

    // 壊れたパケットのログ（間隔を空ける）
    private static void logMalformed(String channel, Exception e) {
        long now = System.nanoTime();
        if (now - lastMalformedLogNanos < MALFORMED_LOG_INTERVAL_NANOS) return;
        lastMalformedLogNanos = now;
        LOGGER.warn("{}: dropped a malformed packet: {}", channel, e.toString());
    }

    // サーバーへプレイヤーリストのリクエストパケットを送信する。
    //
    // @return 実際に送信できたら true。サーバーがチャンネルを登録していない
    //         （＝HikariScoreBoard が入っていない・バニラサーバー・シングルプレイ）
    //         ときは何も送らずに false を返す。呼び出し側は false を
    //         「応答は永久に来ない」と解釈してよい。
    public static boolean requestPlayerList() {
        if (!NetCompat.canSend(PLAYER_LIST_REQUEST)) return false;
        NetCompat.send(PLAYER_LIST_REQUEST, NetCompat.createBuf());
        return true;
    }

    // 指定したプレイヤーのブロック状態を必要なら変更する。
    // 一括操作（まとめて表示/非表示）で使用する。
    //
    // @param uuid      対象プレイヤーの UUID 文字列
    // @param shouldBlock true = 非表示にしたい, false = 表示したい
    public static void toggleBlockIfNeeded(String uuid, boolean shouldBlock) {
        // キャッシュからブロック状態を確認し、目的と異なる場合のみトグルする
        boolean currentlyBlocked = cachedList.stream()
                .filter(e -> e.uuid().equals(uuid))
                .findFirst()
                .map(PlayerListEntry::isBlocked)
                .orElse(false);

        if (currentlyBlocked != shouldBlock) {
            toggleBlock(uuid);
        }
    }

    // 指定 UUID のブロック状態をサーバーへ通知してトグルする
    public static void toggleBlock(String uuid) {
        if (NetCompat.canSend(BLOCK_TOGGLE)) {
            PacketByteBuf buf = NetCompat.createBuf();
            buf.writeString(uuid, 36);
            NetCompat.send(BLOCK_TOGGLE, buf);
        }
    }

    // プレイヤーリスト更新時のコールバックを設定する（null で解除）
    public static void setOnListUpdated(Consumer<List<PlayerListEntry>> callback) {
        onListUpdated = callback;
    }

    // ランキング更新時のコールバックを設定する（null で解除）
    public static void setOnRankingUpdated(Runnable callback) {
        onRankingUpdated = callback;
    }

    // キャッシュ済みプレイヤーリストを返す
    public static List<PlayerListEntry> getCachedList() {
        return cachedList;
    }

    // キャッシュ済みランキングデータを返す（データなしの場合は null）
    public static RankingV1Data getCachedRanking() {
        return cachedRanking;
    }

    // ランキングキャッシュをクリアする
    public static void clearRanking() {
        cachedRanking = null;
    }

    // サーバーから非表示指示を受けている状態かどうかを返す
    public static boolean isServerHidden() {
        return serverHidden;
    }

    // サーバー切断時などに状態をリセットする
    public static void resetHiddenState() {
        serverHidden = false;
        cachedRanking = null;
    }
}
