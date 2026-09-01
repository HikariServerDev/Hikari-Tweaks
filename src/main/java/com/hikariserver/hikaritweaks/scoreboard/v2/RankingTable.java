package com.hikariserver.hikaritweaks.scoreboard.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

// クライアント側が保持するランキングテーブル（docs/ranking-v2-protocol.md §5.1）。
//
// ★ MC 非依存。単体テスト対象。
//
// ┌─ ここを触る前に読むこと ────────────────────────────────────┐
// │ 1. SNAPSHOT は boardId が変わっていなくても届く（§3.1）。      │
// │    「SNAPSHOT ⇒ 新しいボード」と決め打ったり、                 │
// │    「既知の boardId の SNAPSHOT は無視」と最適化したりすると、  │
// │    PING / HEALTH / PLAYER_LEVEL の表示が固まる。              │
// │    これらはサーバー側に版が無いため、同じ boardId の SNAPSHOT   │
// │    が 250ms ごとに届くのが正常動作である（§4.3）。             │
// │ 2. 値でフィルタしてはいけない（§3.7）。                        │
// │    0 は PING / HEALTH / PLAYER_LEVEL では正当な値であり、      │
// │    何を出すかを決めるのはサーバーだけ。                        │
// └────────────────────────────────────────────────────────────┘
public final class RankingTable {

    // テーブル件数のメモリガード（§3.8）。
    // **200（CLIENT_FULL_LIMIT）で切ってはいけない。**
    // 200 で切るとサーバー側の窓管理バグ（§4.4：上位 N 件から押し出された行の
    // remove が送られない）を隠してしまい、表示は正しいのに原因が分からなくなる。
    // 512 を超えるのはサーバー側の不具合を意味するので警告を出す。
    public static final int MAX_ROWS = 512;

    // 並び順（§3.3）。value 降順、同値は playerId.toString() の辞書順昇順。
    //
    // ★ UUID.compareTo を使ってはいけない。
    //   UUID.compareTo は most/least significant bits の**符号付き long 比較**なので、
    //   最上位ビットが立った UUID（例: ffffffff-...）が負の値として扱われ、
    //   文字列の辞書順とは違う結果になる。サーバー側の Ranking と順序がずれる。
    public static final Comparator<RankingRow> ORDER = (a, b) -> {
        int byValue = Long.compare(b.value(), a.value());
        if (byValue != 0) return byValue;
        return a.playerId().toString().compareTo(b.playerId().toString());
    };

    // apply() の結果
    public enum ApplyResult {
        // 反映した
        APPLIED,
        // boardId が一致しない DELTA だったので捨てた（§3.1）
        DROPPED_BOARD_MISMATCH
    }

    private final Map<UUID, RankingRow> rows = new HashMap<>();

    // 保持しているボードがあるか。HIDE 直後と初期状態は false。
    // false のあいだ DELTA は必ず捨てる（マージ先が空だと壊れた表になるため）。
    private boolean hasBoard;
    private int     boardId;
    private String  title = "";
    // §3.5: 負値は「Total 行を出すな」のセンチネル
    private long    serverTotal = -1L;
    private boolean hidden;

    // ボードの同一性が変わるたびに増える。表示側が補間状態を捨てる契機に使う。
    private int generation;

    // 描画は毎フレーム呼ばれるので、変更が無い限りソート結果を使い回す
    private List<RankingRow> sortedCache;

    // 512 件超過の警告シンク。既定は何もしない（MC 非依存を保つため）。
    private Consumer<String> warningSink = message -> {};
    // 超過状態に入った瞬間だけ警告する。PING 系は 250ms ごとに SNAPSHOT が
    // 届くので、毎回出すとログが秒間 4 行で埋まる。
    private boolean overflowWarned;

    // 警告の出力先を設定する（MC 側から Logger を渡す）
    public void setWarningSink(Consumer<String> sink) {
        this.warningSink = sink == null ? message -> {} : sink;
    }

    // ── 受信メッセージの反映 ─────────────────────────────────

    public ApplyResult apply(RankingV2Message msg) {
        switch (msg.type()) {
            case HIDE:
                // §2: 表示中のボードを消し、保持しているテーブルを捨てる。
                // hasBoard を false に落とすのが要点。§3.2 のとおりサーバーは
                // HIDE の直後に boardId を上げて SNAPSHOT を送るが、
                // 万一 DELTA が来ても空のテーブルへマージしないようにする。
                clearBoard();
                hidden = true;
                bumpGeneration();
                return ApplyResult.APPLIED;

            case SNAPSHOT: {
                // §3.1: boardId が同じでも中身を丸ごと置き換える。捨ててはいけない。
                boolean boardChanged = !hasBoard || boardId != msg.boardId();
                rows.clear();
                for (RankingRow row : msg.rows()) {
                    rows.put(row.playerId(), row);
                }
                hasBoard    = true;
                hidden      = false;
                boardId     = msg.boardId();
                title       = msg.title();
                serverTotal = msg.serverTotal();
                invalidate();
                enforceMemoryGuard();
                if (boardChanged) bumpGeneration();
                return ApplyResult.APPLIED;
            }

            case DELTA:
                // §3.1: boardId が一致しない DELTA は捨てる。
                if (!hasBoard || boardId != msg.boardId()) {
                    return ApplyResult.DROPPED_BOARD_MISMATCH;
                }
                // DELTA は title を運ばない（§2）ので既存の title を維持する
                serverTotal = msg.serverTotal();
                for (RankingRow row : msg.rows()) {
                    rows.put(row.playerId(), row);
                }
                for (UUID id : msg.removals()) {
                    rows.remove(id);
                }
                invalidate();
                enforceMemoryGuard();
                return ApplyResult.APPLIED;

            default:
                return ApplyResult.APPLIED;
        }
    }

    // 切断・再接続時の後片付け
    public void reset() {
        clearBoard();
        hidden = false;
        overflowWarned = false;
        bumpGeneration();
    }

    private void clearBoard() {
        rows.clear();
        hasBoard    = false;
        boardId     = 0;
        title       = "";
        serverTotal = -1L;
        invalidate();
    }

    private void invalidate() {
        sortedCache = null;
    }

    private void bumpGeneration() {
        generation++;
    }

    // 512 件のメモリガード（§3.8）。
    // 超過ぶんは並び順の下位から落とす（どれを落とすかは仕様が定めていない。
    // 上位を残すのが表示上いちばん妥当なので順位下位から捨てる）。
    private void enforceMemoryGuard() {
        if (rows.size() <= MAX_ROWS) {
            overflowWarned = false;
            return;
        }
        if (!overflowWarned) {
            overflowWarned = true;
            warningSink.accept("ranking_v2: table exceeded " + MAX_ROWS + " rows (" + rows.size()
                    + "). This means the server is not sending remove for rows that fell out of"
                    + " its top-N window (see protocol doc 4.4). Trimming to " + MAX_ROWS + ".");
        }
        List<RankingRow> ordered = new ArrayList<>(rows.values());
        ordered.sort(ORDER);
        for (int i = MAX_ROWS; i < ordered.size(); i++) {
            rows.remove(ordered.get(i).playerId());
        }
        invalidate();
    }

    // ── 参照 ─────────────────────────────────────────────────

    // §3.3 の規則で並べた行を返す。呼び出し側は変更してはいけない。
    // §3.4 の「順位 = 並べ替えた後の添字 + 1」はこのリストの添字に対応する。
    public List<RankingRow> sorted() {
        if (sortedCache == null) {
            List<RankingRow> ordered = new ArrayList<>(rows.values());
            ordered.sort(ORDER);
            sortedCache = Collections.unmodifiableList(ordered);
        }
        return sortedCache;
    }

    public boolean hasBoard()   { return hasBoard; }
    public boolean isHidden()   { return hidden; }
    public int     boardId()    { return boardId; }
    public String  title()      { return title; }
    public long    serverTotal(){ return serverTotal; }
    public int     size()       { return rows.size(); }
    public int     generation() { return generation; }
}
