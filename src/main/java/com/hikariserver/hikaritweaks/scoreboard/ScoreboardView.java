package com.hikariserver.hikaritweaks.scoreboard;

import com.hikariserver.hikaritweaks.scoreboard.v1.RankingV1Data;
import com.hikariserver.hikaritweaks.scoreboard.v1.RankingV1Entry;
import com.hikariserver.hikaritweaks.scoreboard.v2.RankingRow;
import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.UUID;

// 「いま HUD に出すべきランキング」を v1 / v2 のどちらから取るかを決める唯一の場所。
//
// サーバーは同じプレイヤーへ v1 と v2 を両方送らない（§1）。
// クライアント側の選択規則もここ 1 箇所だけに置き、
// HUD 描画と設定画面が別々の判断をしないようにする。
public final class ScoreboardView {

    // インスタンス化を禁止するプライベートコンストラクタ
    private ScoreboardView() {}

    // 描画側が必要とする情報だけを持つビュー。
    //
    // 行を record のリストへ詰め直さないのは、これが**毎フレーム**作られるため。
    // v2 は RankingTable がソート済みリストをキャッシュしているので、
    // ここでは参照を持つだけで 1 行も確保しない。
    public static final class Data {
        private final String title;
        private final long   serverTotal;

        // v2 経路。v1 のときは null。
        private final List<RankingRow> v2Rows;
        private final UUID             selfId;

        // v1 経路。v2 のときは null。
        private final List<RankingV1Entry> v1Rows;
        private final String                                    v1SelfName;

        private Data(String title, long serverTotal,
                     List<RankingRow> v2Rows, UUID selfId,
                     List<RankingV1Entry> v1Rows, String v1SelfName) {
            this.title       = title;
            this.serverTotal = serverTotal;
            this.v2Rows      = v2Rows;
            this.selfId      = selfId;
            this.v1Rows      = v1Rows;
            this.v1SelfName  = v1SelfName;
        }

        public String title()       { return title; }
        // §3.5: 負値は「Total 行を出すな」のセンチネル
        public long   serverTotal() { return serverTotal; }

        public int size() {
            return v2Rows != null ? v2Rows.size() : v1Rows.size();
        }

        public String name(int index) {
            return v2Rows != null ? v2Rows.get(index).name() : v1Rows.get(index).name();
        }

        public long value(int index) {
            return v2Rows != null ? v2Rows.get(index).value() : v1Rows.get(index).value();
        }

        // この経路の数値を補間してよいか（v2 だけ true）。
        // 合計行のように行と違って uuid を持たない値の判定に使う。
        //
        // ★ v1 で合計行だけ補間してはいけない。v1 は行を補間できない
        //   （下の interpolationKey を参照）ので、合計だけ滑らかに動いて
        //   その下の行が飛ぶという、直そうとしている問題の裏返しになる。
        public boolean interpolatable() {
            return v2Rows != null;
        }

        // 補間のキー。v1 は uuid を運ばないので null を返し、補間対象から外れる。
        // v1 の行を名前でキーにしてはいけない。集計由来の別名が付くと
        // 別人の補間状態を引き継いでしまう（v1 の自分判定が壊れていたのと同じ原因）。
        public UUID interpolationKey(int index) {
            return v2Rows != null ? v2Rows.get(index).playerId() : null;
        }

        // 自分の行かどうか。
        // v2 は uuid で判定する（§3.6）。v1 は従来どおり表示名の一致で判定する
        // （v1 の受信経路と挙動を変えないため。別名が付くと効かない既知の不具合がある）。
        public boolean isSelf(int index) {
            if (v2Rows != null) {
                return selfId != null && selfId.equals(v2Rows.get(index).playerId());
            }
            return v1SelfName != null && v1SelfName.equals(v1Rows.get(index).name());
        }
    }

    // 現在表示すべきデータを返す。何も出さないときは null。
    // MC スレッドから呼ぶこと。
    public static Data current() {
        // v2 を 1 通でも受け取っていれば、このセッションは v2 で確定。
        if (RankingV2Client.isEngaged()) {
            if (!RankingV2Client.hasBoard()) return null;
            return new Data(
                    RankingV2Client.title(),
                    RankingV2Client.serverTotal(),
                    RankingV2Client.sortedRows(),
                    selfUuid(),
                    null, null);
        }

        // v1 経路（従来どおり。サーバーが並べた順をそのまま描く）
        if (ScoreboardPacketClient.isServerHidden()) return null;
        RankingV1Data data = ScoreboardPacketClient.getCachedRanking();
        if (data == null) return null;
        return new Data(
                data.title(),
                data.serverTotal(),
                null, null,
                data.full(),
                data.selfName());
    }

    // 自分の UUID。ワールド外などで player が null になりうる。
    private static UUID selfUuid() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null ? mc.player.getUuid() : null;
    }
}
