package com.hikariserver.hikaritweaks.scoreboard.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class RankingTable {
    public static final int MAX_ROWS = 512;

    public static final Comparator<RankingRow> ORDER = (a, b) -> {
        int byValue = Long.compare(b.value(), a.value());
        if (byValue != 0) return byValue;
        return a.playerId().toString().compareTo(b.playerId().toString());
    };

    public enum ApplyResult {
        APPLIED,
        DROPPED_BOARD_MISMATCH
    }

    private final Map<UUID, RankingRow> rows = new HashMap<>();

    private boolean hasBoard;
    private int     boardId;
    private String  title = "";
    private long    serverTotal = -1L;
    private boolean hidden;

    private int generation;

    private List<RankingRow> sortedCache;

    private Consumer<String> warningSink = message -> {};
    private boolean overflowWarned;

    public void setWarningSink(Consumer<String> sink) {
        this.warningSink = sink == null ? message -> {} : sink;
    }

    public ApplyResult apply(RankingV2Message msg) {
        switch (msg.type()) {
            case HIDE:
                clearBoard();
                hidden = true;
                bumpGeneration();
                return ApplyResult.APPLIED;

            case SNAPSHOT: {
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
                if (!hasBoard || boardId != msg.boardId()) {
                    return ApplyResult.DROPPED_BOARD_MISMATCH;
                }
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
