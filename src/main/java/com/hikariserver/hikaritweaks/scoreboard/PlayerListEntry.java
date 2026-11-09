package com.hikariserver.hikaritweaks.scoreboard;

public record PlayerListEntry(
        String uuid,
        String displayName,
        boolean isBot,
        boolean isBlocked
) {}
