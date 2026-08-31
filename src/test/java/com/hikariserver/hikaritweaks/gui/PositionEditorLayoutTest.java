package com.hikariserver.hikaritweaks.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// PositionEditorScreen のプレビュー当たり判定のテスト。
//
// 「プレビューの矩形が確定ボタンを飲み込んでいて保存できない」という
// 実際に踏んだ不具合の回帰テストが主目的。
class PositionEditorLayoutTest {

    // プレビューのダミーボックスのサイズ（PositionEditorScreen と同じ値）
    private static final int PREVIEW_W = 130;
    private static final int PREVIEW_H = (5 + 1) * 9 + 2; // 56

    // 代表的な GUI スケール後の画面サイズ
    private static final int SCREEN_W = 854;
    private static final int SCREEN_H = 480;

    @Test
    @DisplayName("X=50% Y=100% スケール3.0 のプレビューは確定ボタンを完全に覆う")
    void previewSwallowsConfirmButton() {
        int scaledW = PositionEditorLayout.scaled(PREVIEW_W, 3.0f);
        int scaledH = PositionEditorLayout.scaled(PREVIEW_H, 3.0f);
        int left = PositionEditorLayout.previewX(SCREEN_W, 50, scaledW);
        int top  = PositionEditorLayout.previewY(SCREEN_H, 100, scaledH);

        int confirmL = PositionEditorLayout.confirmX(SCREEN_W);
        int confirmT = PositionEditorLayout.buttonY(SCREEN_H);

        // 確定ボタンの四隅がすべてプレビュー矩形の内側にある＝完全に覆われている
        assertTrue(left <= confirmL && confirmL + PositionEditorLayout.BUTTON_W <= left + scaledW,
                "確定ボタンが横方向でプレビューに収まっていない");
        assertTrue(top <= confirmT && confirmT + PositionEditorLayout.BUTTON_H <= top + scaledH,
                "確定ボタンが縦方向でプレビューに収まっていない");
    }

    @Test
    @DisplayName("覆われていても確定ボタン上ではドラッグを開始しない")
    void confirmButtonStaysClickable() {
        // 確定ボタンの中心
        double px = PositionEditorLayout.confirmX(SCREEN_W) + PositionEditorLayout.BUTTON_W / 2.0;
        double py = PositionEditorLayout.buttonY(SCREEN_H) + PositionEditorLayout.BUTTON_H / 2.0;

        // プレビューの上ではある（＝この点は確かに重なっている）
        assertTrue(PositionEditorLayout.isOverPreview(px, py, SCREEN_W, SCREEN_H,
                50, 100, 3.0f, PREVIEW_W, PREVIEW_H));
        // それでもドラッグは始まらない（super.mouseClicked へ落ちてボタンが押せる）
        assertFalse(PositionEditorLayout.startsPreviewDrag(px, py, SCREEN_W, SCREEN_H,
                50, 100, 3.0f, PREVIEW_W, PREVIEW_H));
    }

    @Test
    @DisplayName("スケールスライダー上でもドラッグを開始しない")
    void scaleSliderStaysClickable() {
        double px = PositionEditorLayout.sliderX(SCREEN_W) + 10;
        double py = PositionEditorLayout.sliderY(SCREEN_H) + PositionEditorLayout.SLIDER_H / 2.0;

        assertTrue(PositionEditorLayout.isOverPreview(px, py, SCREEN_W, SCREEN_H,
                50, 100, 3.0f, PREVIEW_W, PREVIEW_H), "前提：この点はプレビューに重なっている");
        assertFalse(PositionEditorLayout.startsPreviewDrag(px, py, SCREEN_W, SCREEN_H,
                50, 100, 3.0f, PREVIEW_W, PREVIEW_H));
    }

    @Test
    @DisplayName("キャンセルボタンはそもそもプレビューの外にある")
    void cancelButtonIsOutsidePreview() {
        double px = PositionEditorLayout.cancelX(SCREEN_W) + PositionEditorLayout.BUTTON_W / 2.0;
        double py = PositionEditorLayout.buttonY(SCREEN_H) + PositionEditorLayout.BUTTON_H / 2.0;

        assertFalse(PositionEditorLayout.isOverPreview(px, py, SCREEN_W, SCREEN_H,
                50, 100, 3.0f, PREVIEW_W, PREVIEW_H));
        assertFalse(PositionEditorLayout.startsPreviewDrag(px, py, SCREEN_W, SCREEN_H,
                50, 100, 3.0f, PREVIEW_W, PREVIEW_H));
    }

    @Test
    @DisplayName("コントロールに重ならないプレビュー上ではドラッグを開始する")
    void dragStartsOnPlainPreviewArea() {
        // 既定位置（右端・上下中央）ならプレビューは下部コントロールと重ならない
        int scaledW = PositionEditorLayout.scaled(PREVIEW_W, 1.0f);
        int scaledH = PositionEditorLayout.scaled(PREVIEW_H, 1.0f);
        double px = PositionEditorLayout.previewX(SCREEN_W, 100, scaledW) + scaledW / 2.0;
        double py = PositionEditorLayout.previewY(SCREEN_H, 50, scaledH) + scaledH / 2.0;

        assertFalse(PositionEditorLayout.isOverControl(px, py, SCREEN_W, SCREEN_H));
        assertTrue(PositionEditorLayout.startsPreviewDrag(px, py, SCREEN_W, SCREEN_H,
                100, 50, 1.0f, PREVIEW_W, PREVIEW_H));
    }

    @Test
    @DisplayName("プレビューの外ではドラッグを開始しない")
    void dragDoesNotStartOutsidePreview() {
        assertFalse(PositionEditorLayout.startsPreviewDrag(5, 5, SCREEN_W, SCREEN_H,
                100, 50, 1.0f, PREVIEW_W, PREVIEW_H));
    }

    @Test
    @DisplayName("アンカーは % を画面サイズへ切り捨てで変換する")
    void anchorConversion() {
        assertEquals(0,   PositionEditorLayout.anchorX(SCREEN_W, 0));
        assertEquals(427, PositionEditorLayout.anchorX(SCREEN_W, 50));
        assertEquals(854, PositionEditorLayout.anchorX(SCREEN_W, 100));
        assertEquals(240, PositionEditorLayout.anchorY(SCREEN_H, 50));
        assertEquals(480, PositionEditorLayout.anchorY(SCREEN_H, 100));
    }

    @Test
    @DisplayName("プレビューはアンカーを右端・上下中央として左上へ広がる")
    void previewGrowsLeftFromAnchor() {
        int scaledW = PositionEditorLayout.scaled(PREVIEW_W, 2.0f);
        int scaledH = PositionEditorLayout.scaled(PREVIEW_H, 2.0f);
        assertEquals(260, scaledW);
        assertEquals(112, scaledH);
        assertEquals(854 - 260, PositionEditorLayout.previewX(SCREEN_W, 100, scaledW));
        assertEquals(240 - 56,  PositionEditorLayout.previewY(SCREEN_H, 50,  scaledH));
    }
}
