package com.hikariserver.hikaritweaks.scoreboard.v2;

import java.util.UUID;

public record RankingRow(UUID playerId, String name, long value, boolean bot) {
    public RankingRow(UUID playerId, String name, long value) {
        this(playerId, name, value, false);
    }

    public RankingRow withBot(boolean isBot) {
        return bot == isBot ? this : new RankingRow(playerId, name, value, isBot);
    }
}
