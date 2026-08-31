package com.hikariserver.hikaritweaks.scoreboard.v1;

// v1 ranking_data の 1 エントリ（名前とスコア値）。
//
// v2 の RankingRow と違って uuid を持たない。これは v1 のワイヤ仕様であって
// 手落ちではないので、行の同一性は名前でしか表せない
//（補間と自分判定が v1 で効かない理由。ScoreboardView を参照）。
public record RankingV1Entry(String name, long value) {}
