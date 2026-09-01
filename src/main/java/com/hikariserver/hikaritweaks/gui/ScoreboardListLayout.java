package com.hikariserver.hikaritweaks.gui;

// ScoreboardTab のプレイヤー一覧まわりのスクロール計算をまとめた純ロジック。
//
// 同じ式が render / mouseClicked / mouseDragged / mouseScrolled の 4 箇所に
// コピーされており、片方だけ直すと当たり判定と描画がずれる。
// Minecraft に依存しないのでユニットテストで固定できる。
public final class ScoreboardListLayout {

    // インスタンス化を禁止するプライベートコンストラクタ
    private ScoreboardListLayout() {}

    // 仮想リスト全体の高さ。空でないカテゴリだけカテゴリヘッダー分を足す。
    public static int totalVirtualHeight(int visibleCount, int blockedCount,
                                         int categoryH, int rowHeight) {
        int total = 0;
        if (visibleCount > 0) total += categoryH + visibleCount * rowHeight;
        if (blockedCount > 0) total += categoryH + blockedCount * rowHeight;
        return total;
    }

    // スクロールできる最大量（リストが収まりきるなら 0）
    public static int maxScroll(int totalVirtualH, int visibleH) {
        return Math.max(0, totalVirtualH - visibleH);
    }

    // スクロール位置を 0〜maxScroll に収める
    public static int clampScroll(int scroll, int maxScroll) {
        return Math.max(0, Math.min(scroll, maxScroll));
    }

    // スクロールバーのつまみの高さ（最低 24px、可視高は超えない）
    public static int scrollbarHeight(int visibleH, int totalVirtualH) {
        int barH = Math.max(24, visibleH * visibleH / Math.max(1, totalVirtualH));
        return Math.min(barH, visibleH);
    }

    // スクロールバーのつまみの Y 座標
    public static int scrollbarY(int listTop, int listBottom, int barH,
                                 int visibleH, int scrollOffset, int maxScroll) {
        int trackH = visibleH - barH;
        int barY = listTop + (trackH > 0 && maxScroll > 0 ? trackH * scrollOffset / maxScroll : 0);
        return Math.min(barY, listBottom - barH);
    }

    // つまみが動ける範囲（トラックの高さ）
    public static int trackHeight(int visibleH, int barH) {
        return visibleH - barH;
    }

    // つまみのドラッグ量からスクロール位置を求める
    public static int scrollFromDrag(int dragStartScroll, double mouseDeltaY,
                                     int maxScroll, int trackH) {
        if (trackH <= 0) return clampScroll(dragStartScroll, maxScroll);
        return clampScroll(dragStartScroll + (int) (mouseDeltaY * maxScroll / trackH), maxScroll);
    }

    // ホイールの回転量からスクロール位置を求める
    public static int scrollFromWheel(int scrollOffset, double amount,
                                      int scrollSpeed, int maxScroll) {
        return clampScroll((int) (scrollOffset - amount * scrollSpeed), maxScroll);
    }
}
