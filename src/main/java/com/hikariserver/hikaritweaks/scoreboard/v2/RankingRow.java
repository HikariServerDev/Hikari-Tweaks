package com.hikariserver.hikaritweaks.scoreboard.v2;

import java.util.UUID;

// ランキング 1 行分のデータ。
//
// ★ このパッケージ（scoreboard.v2）は Minecraft のクラスを一切参照しない。
//   docs/ranking-v2-protocol.md §6 が「コーデックは MC に依存しない純ロジックへ
//   切り出してユニットテストを書く」ことを要求しているため、
//   net.minecraft.* を import した瞬間にテストが動かなくなる。
//   ここへ MC の型を持ち込まないこと。
public record RankingRow(UUID playerId, String name, long value) {
}
