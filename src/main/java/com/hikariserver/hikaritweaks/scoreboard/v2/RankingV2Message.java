package com.hikariserver.hikaritweaks.scoreboard.v2;

import java.util.List;
import java.util.UUID;

public record RankingV2Message(
        Type type,
        int boardId,
        String title,
        long serverTotal,
        List<RankingRow> rows,
        List<UUID> removals
) {
    public enum Type {
        HIDE(RankingV2Codec.TYPE_HIDE),
        SNAPSHOT(RankingV2Codec.TYPE_SNAPSHOT),
        DELTA(RankingV2Codec.TYPE_DELTA);

        private final int wireId;

        Type(int wireId) { this.wireId = wireId; }

        public int wireId() { return wireId; }
    }

    public RankingV2Message {
        rows     = rows     == null ? List.of() : List.copyOf(rows);
        removals = removals == null ? List.of() : List.copyOf(removals);
        title    = title    == null ? ""        : title;
    }

    public static RankingV2Message hide() {
        return new RankingV2Message(Type.HIDE, 0, "", -1L, List.of(), List.of());
    }

    public static RankingV2Message snapshot(int boardId, String title, long serverTotal, List<RankingRow> rows) {
        return new RankingV2Message(Type.SNAPSHOT, boardId, title, serverTotal, rows, List.of());
    }

    public static RankingV2Message delta(int boardId, long serverTotal,
                                         List<RankingRow> upserts, List<UUID> removals) {
        return new RankingV2Message(Type.DELTA, boardId, "", serverTotal, upserts, removals);
    }
}
