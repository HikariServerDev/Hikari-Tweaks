package com.hikariserver.hikaritweaks.scoreboard;

// サーバーから受信したプレイヤー一覧の 1 行分データ。
// uuid:        プレイヤーの一意な識別子（UUID 文字列）
// displayName: 画面に表示するプレイヤー名
// isBot:       Bot かどうかのフラグ
// isBlocked:   非表示（ブロック）設定かどうかのフラグ
public record PlayerListEntry(
        String uuid,
        String displayName,
        boolean isBot,
        boolean isBlocked
) {}
