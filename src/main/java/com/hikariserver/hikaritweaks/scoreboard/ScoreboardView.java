package com.hikariserver.hikaritweaks.scoreboard;

import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import com.hikariserver.hikaritweaks.scoreboard.v1.RankingV1Data;
import com.hikariserver.hikaritweaks.scoreboard.v1.RankingV1Entry;
import com.hikariserver.hikaritweaks.scoreboard.v2.RankingRow;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.UUID;

public final class ScoreboardView {
    private ScoreboardView() {}

    public static final class Data {
        private final String title;
        private final long   serverTotal;

        private final List<RankingRow> v2Rows;
        private final UUID             selfId;

        private final List<RankingV1Entry> v1Rows;
        private final String                                    v1SelfName;

        private final int[] indexMap;

        private Data(String title, long serverTotal,
                     List<RankingRow> v2Rows, UUID selfId,
                     List<RankingV1Entry> v1Rows, String v1SelfName,
                     int[] indexMap) {
            this.title       = title;
            this.serverTotal = serverTotal;
            this.v2Rows      = v2Rows;
            this.selfId      = selfId;
            this.v1Rows      = v1Rows;
            this.v1SelfName  = v1SelfName;
            this.indexMap    = indexMap;
        }

        public String title()       { return title; }
        public long   serverTotal() { return serverTotal; }

        public int size() {
            if (indexMap != null) return indexMap.length;
            return v2Rows != null ? v2Rows.size() : v1Rows.size();
        }

        private int raw(int index) {
            return indexMap != null ? indexMap[index] : index;
        }

        public int rank(int index) {
            return raw(index) + 1;
        }

        public String name(int index) {
            int i = raw(index);
            return v2Rows != null ? v2Rows.get(i).name() : v1Rows.get(i).name();
        }

        public long value(int index) {
            int i = raw(index);
            return v2Rows != null ? v2Rows.get(i).value() : v1Rows.get(i).value();
        }

        public boolean isBot(int index) {
            return v2Rows != null && v2Rows.get(raw(index)).bot();
        }

        public boolean interpolatable() {
            return v2Rows != null;
        }

        public UUID interpolationKey(int index) {
            return v2Rows != null ? v2Rows.get(raw(index)).playerId() : null;
        }

        public boolean isSelf(int index) {
            int i = raw(index);
            if (v2Rows != null) {
                return selfId != null && selfId.equals(v2Rows.get(i).playerId());
            }
            return v1SelfName != null && v1SelfName.equals(v1Rows.get(i).name());
        }
    }

    public static Data current() {
        if (RankingV2Client.isEngaged()) {
            if (!RankingV2Client.hasBoard()) return null;
            List<RankingRow> rows = RankingV2Client.sortedRows();
            return new Data(
                    RankingV2Client.title(),
                    RankingV2Client.serverTotal(),
                    rows,
                    selfUuid(),
                    null, null,
                    hiddenBotIndexMap(rows));
        }

        if (ScoreboardPacketClient.isServerHidden()) return null;
        RankingV1Data data = ScoreboardPacketClient.getCachedRanking();
        if (data == null) return null;
        return new Data(
                data.title(),
                data.serverTotal(),
                null, null,
                data.full(),
                data.selfName(),
                null);
    }

    private static int[] hiddenBotIndexMap(List<RankingRow> rows) {
        if (!ClientConfigManager.config.scoreboardHideBots) return null;

        int keep = 0;
        for (RankingRow row : rows) {
            if (!row.bot()) keep++;
        }
        if (keep == rows.size()) return null;

        int[] map = new int[keep];
        int at = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (!rows.get(i).bot()) map[at++] = i;
        }
        return map;
    }

    private static UUID selfUuid() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getUUID() : null;
    }
}
