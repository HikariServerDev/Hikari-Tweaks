package com.hikariserver.hikaritweaks.scoreboard.v2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// docs/ranking-v2-protocol.md §5.2（実時間ベースの数値補間）のテスト。
class ValueInterpolatorTest {

    private static final UUID ID_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final long MS = 1_000_000L;

    @Test
    @DisplayName("初めて見る行は補間せずいきなり目標値を返す")
    void firstSightSnaps() {
        ValueInterpolator interpolator = new ValueInterpolator();
        assertEquals(1234L, interpolator.display(ID_1, 1234L, 0L));
    }

    @Test
    @DisplayName("目標値が変わると中間値を経て到達する")
    void interpolatesTowardsTarget() {
        ValueInterpolator interpolator = new ValueInterpolator();
        interpolator.display(ID_1, 0L, 0L);

        long afterOneFrame = interpolator.display(ID_1, 1000L, 16 * MS);
        assertTrue(afterOneFrame > 0L,   "1 フレームで動き出していない");
        assertTrue(afterOneFrame < 1000L, "1 フレームで飛んでしまっている");

        // 十分に時間が経てば最終的に目標値そのものになる
        long value = afterOneFrame;
        long now = 16 * MS;
        for (int i = 0; i < 60 && value != 1000L; i++) {
            now += 16 * MS;
            value = interpolator.display(ID_1, 1000L, now);
        }
        assertEquals(1000L, value, "目標値へ吸着していない");
    }

    @Test
    @DisplayName("進み方は実時間で決まる（フレーム数ではない）")
    void advancesOnWallClockNotFrameCount() {
        // §5.2 の肝。tick 数やフレーム数で進めると
        // fps や送信レートに表示速度が従属してしまう。
        ValueInterpolator fewFrames = new ValueInterpolator();
        fewFrames.display(ID_1, 0L, 0L);
        long afterOneBigStep = fewFrames.display(ID_1, 10000L, 100 * MS);

        ValueInterpolator manyFrames = new ValueInterpolator();
        manyFrames.display(ID_1, 0L, 0L);
        long value = 0L;
        for (int i = 1; i <= 10; i++) {
            value = manyFrames.display(ID_1, 10000L, i * 10 * MS);
        }

        // 同じ 100ms を 1 フレームで進めても 10 フレームで進めてもほぼ同じ値になる
        assertTrue(Math.abs(afterOneBigStep - value) < 100,
                "frames=1 -> " + afterOneBigStep + " / frames=10 -> " + value);
    }

    @Test
    @DisplayName("同じ時刻で 2 回呼んでも二重に進まない")
    void sameTimestampDoesNotDoubleAdvance() {
        // 幅の計算と描画で同じフレームに 2 回参照されるため
        ValueInterpolator interpolator = new ValueInterpolator();
        interpolator.display(ID_1, 0L, 0L);
        long first  = interpolator.display(ID_1, 1000L, 16 * MS);
        long second = interpolator.display(ID_1, 1000L, 16 * MS);
        assertEquals(first, second);
    }

    @Test
    @DisplayName("長く止まっていたら補間せず即座に合わせる")
    void longGapSnaps() {
        ValueInterpolator interpolator = new ValueInterpolator();
        interpolator.display(ID_1, 0L, 0L);
        // ワールドロードなどで 5 秒フレームが止まったあと
        assertEquals(9999L, interpolator.display(ID_1, 9999L, 5_000L * MS));
    }

    @Test
    @DisplayName("nanoTime が巻き戻っても進めない（値が飛ばない）")
    void negativeDeltaDoesNotAdvance() {
        ValueInterpolator interpolator = new ValueInterpolator();
        interpolator.display(ID_1, 0L, 1000L * MS);
        long value = interpolator.display(ID_1, 5000L, 900L * MS);
        assertEquals(0L, value);
    }

    @Test
    @DisplayName("参照されなかった行の状態は endFrame() で捨てられる")
    void untouchedRowsAreEvicted() {
        ValueInterpolator interpolator = new ValueInterpolator();
        interpolator.display(ID_1, 10L, 0L);
        interpolator.display(ID_2, 20L, 0L);
        interpolator.endFrame();
        assertEquals(2, interpolator.trackedRows());

        // 次のフレームでは ID_1 しか描かれない（消えた / 別ページへ行った）
        interpolator.display(ID_1, 10L, 16 * MS);
        interpolator.endFrame();
        assertEquals(1, interpolator.trackedRows());
    }

    @Test
    @DisplayName("消えた行が復活したらその時点の値から始まる")
    void reappearingRowSnapsToCurrentValue() {
        ValueInterpolator interpolator = new ValueInterpolator();
        interpolator.display(ID_1, 0L, 0L);
        // 補間の途中で行が消える
        interpolator.display(ID_1, 1000L, 16 * MS);
        interpolator.endFrame();          // ID_1 は触れたので残る
        interpolator.endFrame();          // 触れなかったので捨てられる
        assertEquals(0, interpolator.trackedRows());

        // 復活したら「初めて見る行」扱いになる
        assertEquals(1000L, interpolator.display(ID_1, 1000L, 100 * MS));
    }

    @Test
    @DisplayName("reset() で全状態が消える（ボード切り替え・HIDE・切断）")
    void resetClearsAll() {
        ValueInterpolator interpolator = new ValueInterpolator();
        interpolator.display(ID_1, 0L, 0L);
        interpolator.display(ID_2, 0L, 0L);
        interpolator.reset();
        assertEquals(0, interpolator.trackedRows());
        // 別の stat になったので、以前の値から数え下げずに新しい値から始まる
        assertEquals(7L, interpolator.display(ID_1, 7L, 16 * MS));
    }

    @Test
    @DisplayName("行ごとに独立して補間される")
    void rowsAreIndependent() {
        ValueInterpolator interpolator = new ValueInterpolator();
        interpolator.display(ID_1, 0L, 0L);
        interpolator.display(ID_2, 0L, 0L);

        long a = interpolator.display(ID_1, 1000L, 16 * MS);
        long b = interpolator.display(ID_2, 0L, 16 * MS);
        assertNotEquals(a, b);
        assertEquals(0L, b);
    }

    // ── 合計行（serverTotal）─────────────────────────────────
    // §5.2「合計行（serverTotal）も同じように補間する」

    @Test
    @DisplayName("合計行も初めて現れたときは補間せずいきなり目標値を返す")
    void totalFirstSightSnaps() {
        ValueInterpolator interpolator = new ValueInterpolator();
        assertEquals(50000L, interpolator.displayTotal(50000L, 0L));
    }

    @Test
    @DisplayName("合計行は目標値が変わると中間値を経て到達する（飛ばない）")
    void totalInterpolatesTowardsTarget() {
        ValueInterpolator interpolator = new ValueInterpolator();
        interpolator.displayTotal(0L, 0L);

        long afterOneFrame = interpolator.displayTotal(100000L, 16 * MS);
        assertTrue(afterOneFrame > 0L,      "1 フレームで動き出していない");
        assertTrue(afterOneFrame < 100000L, "1 フレームで飛んでしまっている");

        long value = afterOneFrame;
        long now   = 16 * MS;
        for (int i = 0; i < 120 && value != 100000L; i++) {
            now += 16 * MS;
            value = interpolator.displayTotal(100000L, now);
        }
        assertEquals(100000L, value, "目標値へ吸着していない");
    }

    @Test
    @DisplayName("合計行は行とまったく同じ進み方をする（同じ補間器・同じ時計）")
    void totalAdvancesIdenticallyToRows() {
        // HUD 上で行と合計行の動きがずれないこと。
        // ずれると「行は滑らかなのに合計だけ遅れる／先に着く」という別の目立ち方をする。
        ValueInterpolator interpolator = new ValueInterpolator();
        interpolator.display(ID_1, 0L, 0L);
        interpolator.displayTotal(0L, 0L);

        for (int i = 1; i <= 5; i++) {
            long now = i * 16 * MS;
            assertEquals(interpolator.display(ID_1, 1000L, now),
                         interpolator.displayTotal(1000L, now),
                         "フレーム " + i + " で行と合計行の値がずれた");
        }
    }

    @Test
    @DisplayName("合計行は行と状態を共有しない")
    void totalIsIndependentOfRows() {
        ValueInterpolator interpolator = new ValueInterpolator();
        interpolator.display(ID_1, 0L, 0L);
        interpolator.displayTotal(9999L, 0L);

        // 行は 0 から動き出したところ、合計行は 9999 に居座っている
        assertEquals(9999L, interpolator.displayTotal(9999L, 16 * MS));
        assertTrue(interpolator.display(ID_1, 1000L, 16 * MS) < 1000L);
        // 行の状態を数える関数に合計行が混ざっていないこと
        assertEquals(1, interpolator.trackedRows());
    }

    @Test
    @DisplayName("合計行を描かなかったフレームがあると状態が捨てられ、再出現時は即座に実値になる")
    void totalIsEvictedWhenNotDrawn() {
        // Total 行は設定 OFF と §3.5 の負値センチネルで消える。
        // 消えているあいだに値が動いていた場合、戻ってきた瞬間に
        // 古い値から数え上げると実際には起きていない増加を捏造することになる。
        ValueInterpolator interpolator = new ValueInterpolator();
        interpolator.displayTotal(100L, 0L);
        interpolator.endFrame();
        assertTrue(interpolator.trackingTotal());

        // Total 行を描かないフレーム（設定 OFF / serverTotal < 0）
        interpolator.endFrame();
        assertFalse(interpolator.trackingTotal());

        // 復帰したら「初出」扱い
        assertEquals(80000L, interpolator.displayTotal(80000L, 32 * MS));
    }

    @Test
    @DisplayName("reset() は合計行の状態も捨てる（ボード切り替え・HIDE・切断）")
    void resetClearsTotal() {
        // 行と同じ契機で捨てる。別 stat の合計は別の数なので、
        // 採掘数の合計から死亡回数の合計へ数え下げる演出が出てはいけない。
        ValueInterpolator interpolator = new ValueInterpolator();
        interpolator.display(ID_1, 500L, 0L);
        interpolator.displayTotal(500000L, 0L);

        interpolator.reset();
        assertEquals(0, interpolator.trackedRows());
        assertFalse(interpolator.trackingTotal());
        assertEquals(3L, interpolator.displayTotal(3L, 16 * MS));
    }

    @Test
    @DisplayName("合計行も長く止まっていたら補間せず即座に合わせる")
    void totalLongGapSnaps() {
        ValueInterpolator interpolator = new ValueInterpolator();
        interpolator.displayTotal(0L, 0L);
        assertEquals(123456L, interpolator.displayTotal(123456L, 5_000L * MS));
    }

    @Test
    @DisplayName("合計行を同じ時刻で 2 回呼んでも二重に進まない")
    void totalSameTimestampDoesNotDoubleAdvance() {
        // 幅の計算と描画で同じフレームに 2 回参照されうるため
        ValueInterpolator interpolator = new ValueInterpolator();
        interpolator.displayTotal(0L, 0L);
        long first  = interpolator.displayTotal(1000L, 16 * MS);
        long second = interpolator.displayTotal(1000L, 16 * MS);
        assertEquals(first, second);
    }
}
