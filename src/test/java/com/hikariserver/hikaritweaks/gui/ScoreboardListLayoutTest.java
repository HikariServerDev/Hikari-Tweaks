package com.hikariserver.hikaritweaks.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreboardListLayoutTest {
    private static final int CATEGORY_H = 14;
    private static final int ROW_HEIGHT = 20;

    @Test
    @DisplayName("空でないカテゴリだけヘッダー分の高さが足される")
    void totalVirtualHeightCountsOnlyNonEmptyCategories() {
        assertEquals(0, ScoreboardListLayout.totalVirtualHeight(0, 0, CATEGORY_H, ROW_HEIGHT));
        assertEquals(74, ScoreboardListLayout.totalVirtualHeight(3, 0, CATEGORY_H, ROW_HEIGHT));
        assertEquals(54, ScoreboardListLayout.totalVirtualHeight(0, 2, CATEGORY_H, ROW_HEIGHT));
        assertEquals(128, ScoreboardListLayout.totalVirtualHeight(3, 2, CATEGORY_H, ROW_HEIGHT));
    }

    @Test
    @DisplayName("リストが収まりきるときスクロール量は 0")
    void maxScrollIsZeroWhenListFits() {
        assertEquals(0,  ScoreboardListLayout.maxScroll(50, 100));
        assertEquals(0,  ScoreboardListLayout.maxScroll(100, 100));
        assertEquals(28, ScoreboardListLayout.maxScroll(128, 100));
    }

    @Test
    @DisplayName("スクロール位置は 0〜maxScroll に収まる")
    void clampScrollStaysInRange() {
        assertEquals(0,  ScoreboardListLayout.clampScroll(-10, 28));
        assertEquals(14, ScoreboardListLayout.clampScroll(14, 28));
        assertEquals(28, ScoreboardListLayout.clampScroll(999, 28));
        assertEquals(0,  ScoreboardListLayout.clampScroll(999, 0));
    }

    @Test
    @DisplayName("つまみの高さは最低 24px、可視高は超えない")
    void scrollbarHeightIsBounded() {
        assertEquals(25,  ScoreboardListLayout.scrollbarHeight(100, 400));
        assertEquals(24,  ScoreboardListLayout.scrollbarHeight(100, 10000));
        assertEquals(20,  ScoreboardListLayout.scrollbarHeight(20, 400));
        assertEquals(100, ScoreboardListLayout.scrollbarHeight(100, 0));
    }

    @Test
    @DisplayName("つまみは先頭で上端、最大スクロールで下端に来る")
    void scrollbarYSpansTheTrack() {
        int listTop = 100, listBottom = 200, visibleH = 100, barH = 25, maxScroll = 28;
        assertEquals(100, ScoreboardListLayout.scrollbarY(listTop, listBottom, barH, visibleH, 0, maxScroll));
        assertEquals(175, ScoreboardListLayout.scrollbarY(listTop, listBottom, barH, visibleH, maxScroll, maxScroll));
        assertEquals(100, ScoreboardListLayout.scrollbarY(listTop, listBottom, barH, visibleH, 0, 0));
    }

    @Test
    @DisplayName("つまみをトラックの端まで引くと最大スクロールになる")
    void scrollFromDragReachesBothEnds() {
        int maxScroll = 28, trackH = 75;
        assertEquals(28, ScoreboardListLayout.scrollFromDrag(0, trackH, maxScroll, trackH));
        assertEquals(0,  ScoreboardListLayout.scrollFromDrag(0, -trackH, maxScroll, trackH));
        assertEquals(0,  ScoreboardListLayout.scrollFromDrag(0, 0, maxScroll, trackH));
        assertEquals(10, ScoreboardListLayout.scrollFromDrag(10, 50, maxScroll, 0));
    }

    @Test
    @DisplayName("ホイールは 1 ノッチあたり SCROLL_SPEED px 動き、範囲外へは出ない")
    void scrollFromWheelMovesBySpeed() {
        assertEquals(6,  ScoreboardListLayout.scrollFromWheel(10, 1.0, 4, 28));
        assertEquals(14, ScoreboardListLayout.scrollFromWheel(10, -1.0, 4, 28));
        assertEquals(0,  ScoreboardListLayout.scrollFromWheel(2, 1.0, 4, 28));
        assertEquals(28, ScoreboardListLayout.scrollFromWheel(26, -1.0, 4, 28));
    }
}
