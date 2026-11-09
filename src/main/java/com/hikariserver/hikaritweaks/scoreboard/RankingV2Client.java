package com.hikariserver.hikaritweaks.scoreboard;

import com.hikariserver.hikaritweaks.compat.NetCompat;
import com.hikariserver.hikaritweaks.scoreboard.v2.RankingRow;
import com.hikariserver.hikaritweaks.scoreboard.v2.RankingTable;
import com.hikariserver.hikaritweaks.scoreboard.v2.RankingV2Codec;
import com.hikariserver.hikaritweaks.scoreboard.v2.RankingV2Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class RankingV2Client {
    private static final Logger LOGGER = LoggerFactory.getLogger("Hikari-Tweaks/ranking_v2");

    private static final String RANKING_V2 = "hikariscoreboard:ranking_v2";

    private static final long MALFORMED_LOG_INTERVAL_NANOS = 5_000_000_000L;

    private static final RankingTable TABLE = new RankingTable();

    private static volatile boolean engaged = false;

    private static long lastMalformedLogNanos = Long.MIN_VALUE;

    private RankingV2Client() {}

    public static void register() {
        TABLE.setWarningSink(LOGGER::warn);

        NetCompat.registerReceiver(RANKING_V2, (client, buf) -> {
            RankingV2Message message;
            try {
                byte[] payload = new byte[buf.readableBytes()];
                buf.readBytes(payload);
                message = RankingV2Codec.decode(payload);
            } catch (Exception e) {
                logMalformed(e);
                return;
            }
            client.execute(() -> apply(message));
        });
    }

    private static void apply(RankingV2Message message) {
        engaged = true;
        RankingTable.ApplyResult result = TABLE.apply(message);
        if (result == RankingTable.ApplyResult.DROPPED_BOARD_MISMATCH) {
            LOGGER.debug("ranking_v2: dropped DELTA for boardId {} (holding {})",
                    message.boardId(), TABLE.boardId());
        }
    }

    private static void logMalformed(Exception e) {
        long now = System.nanoTime();
        if (now - lastMalformedLogNanos < MALFORMED_LOG_INTERVAL_NANOS) return;
        lastMalformedLogNanos = now;
        LOGGER.warn("ranking_v2: dropped a malformed packet: {}", e.toString());
    }

    public static void reset() {
        engaged = false;
        TABLE.reset();
    }

    public static boolean isEngaged() { return engaged; }

    public static boolean hasBoard() { return TABLE.hasBoard() && !TABLE.isHidden(); }

    public static List<RankingRow> sortedRows() { return TABLE.sorted(); }

    public static String title()       { return TABLE.title(); }
    public static long   serverTotal() { return TABLE.serverTotal(); }

    public static int generation() { return TABLE.generation(); }
}
