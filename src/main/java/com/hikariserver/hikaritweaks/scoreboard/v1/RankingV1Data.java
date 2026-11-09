package com.hikariserver.hikaritweaks.scoreboard.v1;

import java.util.List;

public record RankingV1Data(
        boolean hidden,
        String title,
        List<RankingV1Entry> top,
        List<RankingV1Entry> full,
        long serverTotal,
        int selfRank,
        long selfValue,
        String selfName
) {
    private static final RankingV1Data HIDDEN =
            new RankingV1Data(true, "", List.of(), List.of(), -1L, 0, 0L, "");

    public static RankingV1Data hide() {
        return HIDDEN;
    }

    public static RankingV1Data of(String title,
                                   List<RankingV1Entry> top,
                                   List<RankingV1Entry> full,
                                   long serverTotal,
                                   int selfRank,
                                   long selfValue,
                                   String selfName) {
        return new RankingV1Data(false, title, top, full, serverTotal, selfRank, selfValue, selfName);
    }
}
