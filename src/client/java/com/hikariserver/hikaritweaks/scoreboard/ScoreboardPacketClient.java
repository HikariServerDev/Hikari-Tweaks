package com.hikariserver.hikaritweaks.scoreboard;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Client-side packet utility for HikariScoreBoard integration.
public final class ScoreboardPacketClient {

    // パケット識別子の定義
    private static final Identifier RANKING_DATA         = new Identifier("hikariscoreboard", "ranking_data");
    private static final Identifier PLAYER_LIST_REQUEST  = new Identifier("hikariscoreboard", "player_list_request");
    private static final Identifier PLAYER_LIST_RESPONSE = new Identifier("hikariscoreboard", "player_list_response");
    private static final Identifier BLOCK_TOGGLE         = new Identifier("hikariscoreboard", "block_toggle");
    // Server → Client: バニラサイドバーの表示/非表示をクライアントに指示（Hikari-Tweaks連携）
    private static final Identifier VANILLA_SIDEBAR_CONTROL = new Identifier("hikariscoreboard", "vanilla_sidebar_control");

    // サーバーから受信したプレイヤーリストのキャッシュ
    private static List<PlayerListEntry> cachedList = new ArrayList<>();
    // 最後に受信したランキングデータのキャッシュ（volatile でスレッド安全に保つ）
    private static volatile RankingData cachedRanking = null;
    // サーバーから hide 指示を受けた状態。true の間はデータが届いても HUD を表示しない
    private static volatile boolean serverHidden = false;
    // プレイヤーリスト更新時に呼ぶコールバック
    private static Consumer<List<PlayerListEntry>> onListUpdated = null;
    // ランキング更新時に呼ぶコールバック
    private static Runnable onRankingUpdated = null;

    // インスタンス化を禁止するプライベートコンストラクタ
    private ScoreboardPacketClient() {}

    // サーバーからのパケットを受信するハンドラをグローバルに登録する
    public static void register() {
        // ランキングデータパケットを受信してキャッシュに保存する
        ClientPlayNetworking.registerGlobalReceiver(RANKING_DATA,
                (client, handler, buf, responseSender) -> {
                    // 非表示フラグが立っている場合はランキングをクリアして早期リターン
                    boolean isHidden = buf.readBoolean();
                    if (isHidden) {
                        client.execute(() -> {
                            serverHidden = true;
                            cachedRanking = null;
                        });
                        return;
                    }

                    // タイトルとランキングエントリを読み込む
                    String title = buf.readString(256);
                    int count = buf.readVarInt();
                    List<RankingEntry> top = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        String name = buf.readString(64);
                        long value = buf.readLong();
                        top.add(new RankingEntry(name, value));
                    }

                    // サーバー合計・自分のランク・スコア・名前を読み込む
                    long serverTotal = buf.readLong();
                    int selfRank = buf.readVarInt();
                    long selfValue = buf.readLong();
                    String selfName = buf.readString(64);

                    // フルリストが含まれている場合は読み込む（なければ top を使う）
                    List<RankingEntry> full;
                    if (buf.isReadable()) {
                        int fullCount = buf.readVarInt();
                        full = new ArrayList<>(fullCount);
                        for (int i = 0; i < fullCount; i++) {
                            String name = buf.readString(64);
                            long val = buf.readLong();
                            full.add(new RankingEntry(name, val));
                        }
                    } else {
                        full = top;
                    }

                    // MC スレッドでキャッシュを更新してコールバックを呼ぶ
                    RankingData data = new RankingData(title, top, full, serverTotal, selfRank, selfValue, selfName);
                    client.execute(() -> {
                        serverHidden = false;
                        cachedRanking = data;
                        if (onRankingUpdated != null) {
                            onRankingUpdated.run();
                        }
                    });
                });

        // プレイヤーリストレスポンスを受信してキャッシュに保存する
        ClientPlayNetworking.registerGlobalReceiver(PLAYER_LIST_RESPONSE,
                (client, handler, buf, responseSender) -> {
                    int count = buf.readVarInt();
                    List<PlayerListEntry> list = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        String uuid = buf.readString(36);
                        String displayName = buf.readString(64);
                        boolean isBot = buf.readBoolean();
                        boolean isBlocked = buf.readBoolean();
                        list.add(new PlayerListEntry(uuid, displayName, isBot, isBlocked));
                    }
                    // MC スレッドでリストを更新してコールバックを呼ぶ
                    client.execute(() -> {
                        cachedList = list;
                        if (onListUpdated != null) {
                            onListUpdated.accept(list);
                        }
                    });
                });

        // HikariScoreBoard から「バニラサイドバーを非表示にする」指示を受信（Hikari-Tweaks 連携）
        // Hikari-Tweaks がない場合はこのパケットは届かないため副作用なし。
        // ここでは設定をディスクに保存しない（サーバー指示のたびに書き込むのは不要。
        // 画面を閉じた際に ClientConfigManager.save() が呼ばれるため永続化は担保される）。
        ClientPlayNetworking.registerGlobalReceiver(VANILLA_SIDEBAR_CONTROL,
                (client, handler, buf, responseSender) -> {
                    boolean hideVanilla = buf.readBoolean();
                    // MC スレッドで設定値を更新する
                    client.execute(() -> {
                        com.hikariserver.hikaritweaks.config.ClientConfigManager.config.scoreboardHideVanilla = hideVanilla;
                    });
                });
    }

    // サーバーへプレイヤーリストのリクエストパケットを送信する
    public static void requestPlayerList() {
        if (ClientPlayNetworking.canSend(PLAYER_LIST_REQUEST)) {
            ClientPlayNetworking.send(PLAYER_LIST_REQUEST, PacketByteBufs.empty());
        }
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
        if (ClientPlayNetworking.canSend(BLOCK_TOGGLE)) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(uuid, 36);
            ClientPlayNetworking.send(BLOCK_TOGGLE, buf);
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
    public static RankingData getCachedRanking() {
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

    // ランキングの1エントリ（名前とスコア値）を表すレコード
    public record RankingEntry(String name, long value) {}

    // サーバーから受信したランキング全体データを表すレコード
    public record RankingData(
            String title,
            List<RankingEntry> top,
            List<RankingEntry> full,
            long serverTotal,
            int selfRank,
            long selfValue,
            String selfName
    ) {}
}
