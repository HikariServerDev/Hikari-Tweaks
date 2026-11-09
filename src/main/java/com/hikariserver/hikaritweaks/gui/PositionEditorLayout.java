package com.hikariserver.hikaritweaks.gui;

public final class PositionEditorLayout {
    public static final int SLIDER_W = 200;
    public static final int SLIDER_H = 20;
    public static final int BUTTON_W = 100;
    public static final int BUTTON_H = 20;

    private PositionEditorLayout() {}

    public static int sliderX(int screenW)  { return screenW / 2 - 100; }
    public static int sliderY(int screenH)  { return screenH - 68; }
    public static int confirmX(int screenW) { return screenW / 2 - 105; }
    public static int cancelX(int screenW)  { return screenW / 2 + 5; }
    public static int buttonY(int screenH)  { return screenH - 42; }

    public static int anchorX(int screenW, int percentX) { return (int) (screenW * percentX / 100.0); }
    public static int anchorY(int screenH, int percentY) { return (int) (screenH * percentY / 100.0); }

    public static int scaled(int size, float scale) { return (int) (size * scale); }

    public static int previewX(int screenW, int percentX, int scaledW) {
        return anchorX(screenW, percentX) - scaledW;
    }
    public static int previewY(int screenH, int percentY, int scaledH) {
        return anchorY(screenH, percentY) - scaledH / 2;
    }

    public static boolean contains(double px, double py, int rx, int ry, int rw, int rh) {
        return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
    }

    public static boolean isOverPreview(double px, double py,
                                        int screenW, int screenH,
                                        int percentX, int percentY, float scale,
                                        int boxW, int boxH) {
        int scaledW = scaled(boxW, scale);
        int scaledH = scaled(boxH, scale);
        return contains(px, py,
                previewX(screenW, percentX, scaledW),
                previewY(screenH, percentY, scaledH),
                scaledW, scaledH);
    }

    public static boolean isOverControl(double px, double py, int screenW, int screenH) {
        return contains(px, py, sliderX(screenW),  sliderY(screenH), SLIDER_W, SLIDER_H)
                || contains(px, py, confirmX(screenW), buttonY(screenH), BUTTON_W, BUTTON_H)
                || contains(px, py, cancelX(screenW),  buttonY(screenH), BUTTON_W, BUTTON_H);
    }

    public static boolean startsPreviewDrag(double px, double py,
                                            int screenW, int screenH,
                                            int percentX, int percentY, float scale,
                                            int boxW, int boxH) {
        if (isOverControl(px, py, screenW, screenH)) return false;
        return isOverPreview(px, py, screenW, screenH, percentX, percentY, scale, boxW, boxH);
    }
}
