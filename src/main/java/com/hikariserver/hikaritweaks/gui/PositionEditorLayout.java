package com.hikariserver.hikaritweaks.gui;

// PositionEditorScreen のレイアウトと当たり判定をまとめた純ロジック。
//
// Minecraft のクラスに一切依存しないので、クライアントを起動せずに
// ユニットテストで固定できる。画面側とテスト側で同じ式を共有するのが目的で、
// 「プレビューの矩形が下部ボタンを覆っていて確定できない」という
// 実際に踏んだ事故を回帰テストとして書けるようにしている。
public final class PositionEditorLayout {

    // スケールスライダーの矩形サイズ
    public static final int SLIDER_W = 200;
    public static final int SLIDER_H = 20;
    // 確定／キャンセルボタンの矩形サイズ
    public static final int BUTTON_W = 100;
    public static final int BUTTON_H = 20;

    // インスタンス化を禁止するプライベートコンストラクタ
    private PositionEditorLayout() {}

    // ── 下部コントロールの配置 ─────────────────────────────

    // スケールスライダーの左上座標
    public static int sliderX(int screenW)  { return screenW / 2 - 100; }
    public static int sliderY(int screenH)  { return screenH - 68; }
    // 確定ボタンの左端 X
    public static int confirmX(int screenW) { return screenW / 2 - 105; }
    // キャンセルボタンの左端 X
    public static int cancelX(int screenW)  { return screenW / 2 + 5; }
    // 確定／キャンセルボタンの上端 Y（2 つは同じ行）
    public static int buttonY(int screenH)  { return screenH - 42; }

    // ── プレビューの配置 ───────────────────────────────────

    // % 指定のアンカー座標をピクセルへ変換する
    public static int anchorX(int screenW, int percentX) { return (int) (screenW * percentX / 100.0); }
    public static int anchorY(int screenH, int percentY) { return (int) (screenH * percentY / 100.0); }

    // スケールを掛けた後のサイズ（切り捨て）
    public static int scaled(int size, float scale) { return (int) (size * scale); }

    // プレビュー矩形の左上座標。アンカーは右端・上下中央に置かれる。
    public static int previewX(int screenW, int percentX, int scaledW) {
        return anchorX(screenW, percentX) - scaledW;
    }
    public static int previewY(int screenH, int percentY, int scaledH) {
        return anchorY(screenH, percentY) - scaledH / 2;
    }

    // ── 当たり判定 ─────────────────────────────────────────

    // 点が矩形の内側かどうか（端を含む閉区間。既存のドラッグ判定と同じ）
    public static boolean contains(double px, double py, int rx, int ry, int rw, int rh) {
        return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
    }

    // 点がプレビュー矩形の上にあるかどうか
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

    // 点が下部コントロール（スライダー・確定・キャンセル）の上にあるかどうか
    public static boolean isOverControl(double px, double py, int screenW, int screenH) {
        return contains(px, py, sliderX(screenW),  sliderY(screenH), SLIDER_W, SLIDER_H)
                || contains(px, py, confirmX(screenW), buttonY(screenH), BUTTON_W, BUTTON_H)
                || contains(px, py, cancelX(screenW),  buttonY(screenH), BUTTON_W, BUTTON_H);
    }

    // プレビューのドラッグを開始してよい点かどうか。
    //
    // ★ プレビュー上であっても、下部コントロールに重なる点ではドラッグを始めない。
    //   プレビューはアンカー（X%,Y%）を右端・上下中央として左上へ広がるため、
    //   X=50% / Y=100% / スケール 3.0 では横 390px・縦 168px の矩形が
    //   画面中央下に来て、確定ボタン（width/2-105 〜 width/2-5, height-42 〜 height-22）を
    //   完全に飲み込む。ドラッグ判定を先に通すと確定ボタンが永久に押せなくなり、
    //   「今合わせた位置を保存できない」状態になる（キャンセルは矩形の外なので押せる）。
    //   スケールが大きいときはスライダーにも部分的に重なる。
    public static boolean startsPreviewDrag(double px, double py,
                                            int screenW, int screenH,
                                            int percentX, int percentY, float scale,
                                            int boxW, int boxH) {
        if (isOverControl(px, py, screenW, screenH)) return false;
        return isOverPreview(px, py, screenW, screenH, percentX, percentY, scale, boxW, boxH);
    }
}
