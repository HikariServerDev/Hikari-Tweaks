package com.hikariserver.hikaritweaks.gui;

public final class ScoreboardListLayout {
    private ScoreboardListLayout() {}

    public static int totalVirtualHeight(int visibleCount, int blockedCount,
                                         int categoryH, int rowHeight) {
        int total = 0;
        if (visibleCount > 0) total += categoryH + visibleCount * rowHeight;
        if (blockedCount > 0) total += categoryH + blockedCount * rowHeight;
        return total;
    }

    public static int maxScroll(int totalVirtualH, int visibleH) {
        return Math.max(0, totalVirtualH - visibleH);
    }

    public static int clampScroll(int scroll, int maxScroll) {
        return Math.max(0, Math.min(scroll, maxScroll));
    }

    public static int scrollbarHeight(int visibleH, int totalVirtualH) {
        int barH = Math.max(24, visibleH * visibleH / Math.max(1, totalVirtualH));
        return Math.min(barH, visibleH);
    }

    public static int scrollbarY(int listTop, int listBottom, int barH,
                                 int visibleH, int scrollOffset, int maxScroll) {
        int trackH = visibleH - barH;
        int barY = listTop + (trackH > 0 && maxScroll > 0 ? trackH * scrollOffset / maxScroll : 0);
        return Math.min(barY, listBottom - barH);
    }

    public static int trackHeight(int visibleH, int barH) {
        return visibleH - barH;
    }

    public static int scrollFromDrag(int dragStartScroll, double mouseDeltaY,
                                     int maxScroll, int trackH) {
        if (trackH <= 0) return clampScroll(dragStartScroll, maxScroll);
        return clampScroll(dragStartScroll + (int) (mouseDeltaY * maxScroll / trackH), maxScroll);
    }

    public static int scrollFromWheel(int scrollOffset, double amount,
                                      int scrollSpeed, int maxScroll) {
        return clampScroll((int) (scrollOffset - amount * scrollSpeed), maxScroll);
    }
}
