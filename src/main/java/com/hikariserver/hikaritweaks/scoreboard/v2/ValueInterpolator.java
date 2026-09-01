package com.hikariserver.hikaritweaks.scoreboard.v2;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

// 表示される数値を実時間で補間する（docs/ranking-v2-protocol.md §5.2）。
//
// ★ MC 非依存。時刻は引数で受け取るので単体テストできる。
//
// ┌─ 設計の前提（消さないこと）────────────────────────────────┐
// │ ・進めるのは**実時間**であって tick 数でもパケット数でもない。  │
// │   サーバー側の ScoreSmoother が「送信レートに従属して破綻」した │
// │   のがそもそもの今回のラグの原因で、Phase 1 で削除済み。       │
// │   tick / パケットを基準にすると同じ失敗を繰り返す。            │
// │ ・並び順は**実値**で決め、補間するのは表示する数字だけ。        │
// │   補間値で並べると補間中に行が入れ替わってちらつく。            │
// └────────────────────────────────────────────────────────────┘
public final class ValueInterpolator {

    // 目標値へ寄る速さの時定数（秒）。
    // 指数補間なので「差の 1/e まで縮むのに掛かる時間」。
    // 0.12 秒だと 60fps で 8 フレーム程度、体感で「ぬるっと動く」範囲に収まる。
    private static final double TIME_CONSTANT_SECONDS = 0.12;

    // このフレーム時間を超えた間隔が空いたら補間せず即座に目標値へ飛ばす。
    // ワールドロード・一時停止・ウィンドウ非アクティブでフレームが止まったあと、
    // 何秒ぶんもまとめて補間しても意味が無いため。
    private static final double MAX_STEP_SECONDS = 0.5;

    private static final class State {
        // 現在表示している値（小数で保持する）
        double current;
        // 最後に更新した時刻（ナノ秒）
        long   lastNanos;
        // 最後に参照されたフレーム番号。世代が古いものは endFrame() で捨てる。
        long   frame;

        State(long target, long nowNanos, long frame) {
            this.current   = target;
            this.lastNanos = nowNanos;
            this.frame     = frame;
        }
    }

    private final Map<UUID, State> states = new HashMap<>();

    // 合計行（serverTotal）の状態。行と違って UUID を持たないので独立した箱で持つ。
    // ★ 行と同じ Map にセンチネル UUID で相乗りさせないこと。
    //   serverTotal は「行の 1 つ」ではなく、実在しないプレイヤー ID を作ると
    //   §3.3 の並び順や §3.6 の自分判定に紛れ込む余地を残すことになる。
    // 未表示のあいだは null。次に現れたときは「初出」扱いで即座に実値になる。
    private State totalState;

    private long frame;

    // 指定した行の「表示すべき値」を返し、補間を 1 フレーム分進める。
    //
    // nowNanos は System.nanoTime() の値（単調増加・実時間）。
    //
    // 初出の行は補間せずいきなり target を返す。
    // 前の値を知らないので 0 から数え上げると嘘の演出になるため。
    public long display(UUID playerId, long target, long nowNanos) {
        State s = states.get(playerId);
        if (s == null) {
            states.put(playerId, new State(target, nowNanos, frame));
            return target;
        }
        return advance(s, target, nowNanos);
    }

    // 合計行（docs/ranking-v2-protocol.md §5.2 の serverTotal）の「表示すべき値」を返し、
    // 補間を 1 フレーム分進める。行と同じ補間器・同じ実時間の時計を使う。
    //
    // HUD 上でいちばん大きい数字なので、行が滑らかに動いている横でここだけ飛ぶと目立つ。
    //
    // 行と同じく、初出（Total 行が現れた直後）は補間せずいきなり target を返す。
    // 呼び出し側は「Total 行を実際に描くフレーム」でだけ呼ぶこと。
    // 呼ばなかったフレームがあれば endFrame() が状態を捨てるので、
    // 設定 OFF → ON や serverTotal が負から戻ったときは補間ではなく即座に実値になる。
    public long displayTotal(long target, long nowNanos) {
        if (totalState == null) {
            totalState = new State(target, nowNanos, frame);
            return target;
        }
        return advance(totalState, target, nowNanos);
    }

    // 既存の状態を 1 フレーム分進める共通処理。
    // 行と合計行で速さや吸着の条件がずれないよう、必ずここ 1 箇所に置く。
    private long advance(State s, long target, long nowNanos) {
        double dt = (nowNanos - s.lastNanos) / 1_000_000_000.0;
        s.lastNanos = nowNanos;
        s.frame     = frame;

        if (dt > MAX_STEP_SECONDS) {
            // 長時間フレームが止まっていた。まとめて補間せず即座に合わせる。
            s.current = target;
            return target;
        }
        if (dt > 0.0) {
            // 実時間ベースの指数補間。フレームレートが変わっても速さが変わらない。
            double factor = 1.0 - Math.exp(-dt / TIME_CONSTANT_SECONDS);
            s.current += (target - s.current) * factor;
        }
        // dt <= 0 のときは進めない。
        // 同一フレーム内で 2 回呼ばれる（幅計算と描画）ケースと、
        // nanoTime が巻き戻ったケースの両方をここで吸収する。

        // 差が 1 未満になったら目標値へ吸着させる。
        // long → double 変換は 2^53 を超えると誤差が出るので、
        // **最終的に表示する値は必ず target そのもの**にして誤差を残さない。
        if (Math.abs(target - s.current) < 1.0) {
            s.current = target;
            return target;
        }
        return Math.round(s.current);
    }

    // 1 フレーム分の描画が終わったら呼ぶ。
    // このフレームで参照されなかった行（テーブルから消えた行・別ページの行）の
    // 状態を捨てる。これが無いと状態が単調増加する。
    //
    // 消えた行が後で復活したときは「初出」扱いになり、その時点の値から始まる。
    // 途中まで補間していた値には戻らないが、値そのものは正しい。
    public void endFrame() {
        Iterator<Map.Entry<UUID, State>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().frame != frame) it.remove();
        }
        // 合計行も同じ規則で捨てる。
        // Total 行を描かなかったフレームがあれば（設定 OFF・§3.5 の負値センチネル）、
        // 次に現れたときは行と同じく「初出」から始まる。
        if (totalState != null && totalState.frame != frame) totalState = null;
        frame++;
    }

    // 全状態を捨てる。ボードが切り替わったとき（別の stat になったとき）と
    // HIDE / 切断で呼ぶ。別 stat の値へ補間すると、
    // 「採掘数 → 死亡回数」のような無関係な数値間で数え下げ演出が出てしまう。
    // 合計行も同じ理由で同時に捨てる（別 stat の合計は別の数）。
    public void reset() {
        states.clear();
        totalState = null;
    }

    // 保持している行数（テストと診断用）。合計行は含まない。
    public int trackedRows() {
        return states.size();
    }

    // 合計行の状態を保持しているか（テストと診断用）
    public boolean trackingTotal() {
        return totalState != null;
    }
}
