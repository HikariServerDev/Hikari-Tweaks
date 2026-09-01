package com.hikariserver.hikaritweaks.scoreboard.v1;

import java.util.List;

// v1 ranking_data 1 通分のデータ。
//
// hidden = true は「HUD を出すな」というサーバー指示で、
// 先頭の boolean だけで完結する（以降のフィールドは送られてこない）。
// v2 の RankingV2Message.hide() と同じ役割。
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

    // 非表示指示。中身は使われないので使い回す。
    private static final RankingV1Data HIDDEN =
            new RankingV1Data(true, "", List.of(), List.of(), -1L, 0, 0L, "");

    // 非表示指示を返す。
    // ★ 名前を hidden() にはできない。レコードのアクセサ hidden() と衝突する。
    public static RankingV1Data hide() {
        return HIDDEN;
    }

    // 通常のランキングデータを組み立てる
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
