package com.hikariserver.hikaritweaks.scoreboard;

import com.hikariserver.hikaritweaks.compat.NetCompat;
import com.hikariserver.hikaritweaks.scoreboard.v1.RankingV1Data;
import com.hikariserver.hikaritweaks.scoreboard.v1.ScoreboardV1Codec;
import net.minecraft.network.FriendlyByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ScoreboardPacketClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("Hikari-Tweaks/scoreboard");

    private static final String RANKING_DATA         = "hikariscoreboard:ranking_data";
    private static final String PLAYER_LIST_REQUEST  = "hikariscoreboard:player_list_request";
    private static final String PLAYER_LIST_RESPONSE = "hikariscoreboard:player_list_response";
    private static final String BLOCK_TOGGLE         = "hikariscoreboard:block_toggle";

    private static final long MALFORMED_LOG_INTERVAL_NANOS = 5_000_000_000L;

    private static List<PlayerListEntry> cachedList = new ArrayList<>();
    private static volatile RankingV1Data cachedRanking = null;
    private static volatile boolean serverHidden = false;
    private static Consumer<List<PlayerListEntry>> onListUpdated = null;
    private static Runnable onRankingUpdated = null;

    private static long lastMalformedLogNanos = Long.MIN_VALUE;

    private ScoreboardPacketClient() {}

    public static void register() {
        NetCompat.registerReceiver(RANKING_DATA,
                (client, buf) -> {
                    RankingV1Data data = decode(buf, ScoreboardV1Codec::decodeRanking, "ranking_data");
                    if (data == null) return;

                    if (data.hidden()) {
                        client.execute(() -> {
                            serverHidden = true;
                            cachedRanking = null;
                        });
                        return;
                    }

                    client.execute(() -> {
                        serverHidden = false;
                        cachedRanking = data;
                        if (onRankingUpdated != null) {
                            onRankingUpdated.run();
                        }
                    });
                });

        NetCompat.registerReceiver(PLAYER_LIST_RESPONSE,
                (client, buf) -> {
                    List<PlayerListEntry> list =
                            decode(buf, ScoreboardV1Codec::decodePlayerList, "player_list_response");
                    if (list == null) return;

                    client.execute(() -> {
                        cachedList = list;
                        if (onListUpdated != null) {
                            onListUpdated.accept(list);
                        }
                    });
                });

        RankingV2Client.register();

        NetCompat.registerSendChannel(PLAYER_LIST_REQUEST);
        NetCompat.registerSendChannel(BLOCK_TOGGLE);
    }

    private static <T> T decode(FriendlyByteBuf buf, Function<byte[], T> decoder, String channel) {
        try {
            byte[] payload = new byte[buf.readableBytes()];
            buf.readBytes(payload);
            return decoder.apply(payload);
        } catch (Exception e) {
            logMalformed(channel, e);
            return null;
        }
    }

    private static void logMalformed(String channel, Exception e) {
        long now = System.nanoTime();
        if (now - lastMalformedLogNanos < MALFORMED_LOG_INTERVAL_NANOS) return;
        lastMalformedLogNanos = now;
        LOGGER.warn("{}: dropped a malformed packet: {}", channel, e.toString());
    }

    public static boolean requestPlayerList() {
        if (!NetCompat.canSend(PLAYER_LIST_REQUEST)) return false;
        NetCompat.send(PLAYER_LIST_REQUEST, NetCompat.createBuf());
        return true;
    }

    public static void toggleBlockIfNeeded(String uuid, boolean shouldBlock) {
        boolean currentlyBlocked = cachedList.stream()
                .filter(e -> e.uuid().equals(uuid))
                .findFirst()
                .map(PlayerListEntry::isBlocked)
                .orElse(false);

        if (currentlyBlocked != shouldBlock) {
            toggleBlock(uuid);
        }
    }

    public static void toggleBlock(String uuid) {
        if (NetCompat.canSend(BLOCK_TOGGLE)) {
            FriendlyByteBuf buf = NetCompat.createBuf();
            buf.writeUtf(uuid, 36);
            NetCompat.send(BLOCK_TOGGLE, buf);
        }
    }

    public static void setOnListUpdated(Consumer<List<PlayerListEntry>> callback) {
        onListUpdated = callback;
    }

    public static void setOnRankingUpdated(Runnable callback) {
        onRankingUpdated = callback;
    }

    public static List<PlayerListEntry> getCachedList() {
        return cachedList;
    }

    public static RankingV1Data getCachedRanking() {
        return cachedRanking;
    }

    public static void clearRanking() {
        cachedRanking = null;
    }

    public static boolean isServerHidden() {
        return serverHidden;
    }

    public static void resetHiddenState() {
        serverHidden = false;
        cachedRanking = null;
    }
}
