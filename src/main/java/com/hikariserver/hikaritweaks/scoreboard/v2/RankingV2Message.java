package com.hikariserver.hikaritweaks.scoreboard.v2;

import java.util.List;
import java.util.UUID;

// hikariscoreboard:ranking_v2 の 1 メッセージを表すデータ。
// バイト列との対応は docs/ranking-v2-protocol.md §2 を参照。
//
// ★ MC 非依存（RankingRow の注意書きを参照）。
public record RankingV2Message(
        Type type,
        int boardId,
        String title,
        long serverTotal,
        // SNAPSHOT では「テーブル全体」、DELTA では「upsert する行」
        List<RankingRow> rows,
        // DELTA でのみ意味を持つ「削除する playerId」
        List<UUID> removals
) {

    // メッセージ種別。先頭 1 バイトの値と 1 対 1 で対応する（§2）。
    public enum Type {
        HIDE(RankingV2Codec.TYPE_HIDE),
        SNAPSHOT(RankingV2Codec.TYPE_SNAPSHOT),
        DELTA(RankingV2Codec.TYPE_DELTA);

        private final int wireId;

        Type(int wireId) { this.wireId = wireId; }

        // ワイヤ上の種別バイトを返す
        public int wireId() { return wireId; }
    }

    // 防御的コピーを取って不変にする（受信スレッド → MC スレッドを跨ぐため）
    public RankingV2Message {
        rows     = rows     == null ? List.of() : List.copyOf(rows);
        removals = removals == null ? List.of() : List.copyOf(removals);
        title    = title    == null ? ""        : title;
    }

    // HIDE メッセージを作る。boardId / title / serverTotal はワイヤ上に存在しない。
    public static RankingV2Message hide() {
        return new RankingV2Message(Type.HIDE, 0, "", -1L, List.of(), List.of());
    }

    // SNAPSHOT メッセージを作る
    public static RankingV2Message snapshot(int boardId, String title, long serverTotal, List<RankingRow> rows) {
        return new RankingV2Message(Type.SNAPSHOT, boardId, title, serverTotal, rows, List.of());
    }

    // DELTA メッセージを作る。DELTA は title を運ばない（§2）。
    public static RankingV2Message delta(int boardId, long serverTotal,
                                         List<RankingRow> upserts, List<UUID> removals) {
        return new RankingV2Message(Type.DELTA, boardId, "", serverTotal, upserts, removals);
    }
}
