package com.hikariserver.hikaritweaks.gui;

import com.hikariserver.hikaritweaks.config.ClientConfig;
import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

// スコアボードの位置をドラッグ&ドロップで調整できる専用画面。
//
// - スコアボードのプレビューを画面上に表示しドラッグで移動できる
// - スケールスライダーで拡大縮小を調整できる
// - 「確定」で設定を保存、「キャンセル」で破棄
public final class PositionEditorScreen extends Screen {

    // プレビューボックスの横幅（論理ピクセル）
    private static final int PREVIEW_W = 130;
    // 各行の高さ（論理ピクセル）
    private static final int LINE_H    = 9;

    // 前の画面への参照
    private final Screen parent;

    // 確定前の一時値（ドラッグ中に更新される）
    private int   tempX;     // 0-100%
    private int   tempY;     // 0-100%
    private float tempScale; // 0.5-3.0

    // ドラッグ状態フラグ
    private boolean dragging    = false;
    // ドラッグ開始時のマウスとプレビューの左端の差（px）
    private int     dragOffsetX = 0;
    private int     dragOffsetY = 0;

    // スケール調整スライダー
    private ScaleSlider scaleSlider;

    // 親画面を受け取り、設定から初期位置とスケールを読み込む
    public PositionEditorScreen(Screen parent) {
        super(new LiteralText(I18n.translate("hikaritweaks.position.title")));
        this.parent    = parent;
        ClientConfig cfg = ClientConfigManager.config;
        this.tempX     = cfg.scoreboardPositionX;
        this.tempY     = cfg.scoreboardPositionY;
        this.tempScale = cfg.scoreboardScale;
    }

    // 画面初期化時にスライダーとボタンを生成する
    @Override
    protected void init() {
        super.init();

        // スケールスライダー（0.5–3.0 を 0–1 に正規化して渡す）
        scaleSlider = new ScaleSlider(
                width / 2 - 100, height - 68,
                200, 20,
                (tempScale - 0.5f) / 2.5f // 0.5-3.0 → 0-1
        );
        addDrawableChild(scaleSlider);

        // 確定ボタン：tempX/Y/Scale を設定に書き込んで保存する
        addDrawableChild(new ButtonWidget(
                width / 2 - 105, height - 42, 100, 20,
                new LiteralText("§a" + I18n.translate("hikaritweaks.button.confirm")),
                btn -> {
                    ClientConfig cfg = ClientConfigManager.config;
                    cfg.scoreboardPositionX = tempX;
                    cfg.scoreboardPositionY = tempY;
                    cfg.scoreboardScale     = tempScale;
                    ClientConfigManager.save();
                    close();
                }
        ));

        // キャンセルボタン：変更を破棄して親画面へ戻る
        addDrawableChild(new ButtonWidget(
                width / 2 + 5, height - 42, 100, 20,
                new LiteralText("§c" + I18n.translate("hikaritweaks.button.cancel")),
                btn -> close()
        ));
    }

    // 毎フレーム呼ばれる描画メソッド
    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);

        // 操作説明テキストを描画する
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String msg1 = "§e" + I18n.translate("hikaritweaks.position.drag_hint");
        tr.drawWithShadow(matrices, msg1, (width - tr.getWidth(msg1)) / 2f, 12, 0xFFFFFF);
        // 現在のスケール値を表示する
        String msg2 = "§7" + I18n.translate("hikaritweaks.position.scale_prefix") + String.format("%.2f", tempScale) + "x";
        tr.drawWithShadow(matrices, msg2, width / 2f + 110, height - 63, 0xFFFFFF);

        // スコアボードのプレビューを描画する
        renderPreview(matrices, mouseX, mouseY);

        super.render(matrices, mouseX, mouseY, delta);
    }

    // ── プレビュー描画 ─────────────────────────────────────

    // プレビュー用ダミーデータを使ってスコアボードを描画する
    private void renderPreview(MatrixStack matrices, int mouseX, int mouseY) {
        // プレビュー用ダミーデータ
        String title = I18n.translate("hikaritweaks.position.scoreboard_label");
        String[] names  = {"Player1", "Player2", "Player3", "Player4", "Player5"};
        int[]    scores = {1234, 987, 756, 543, 210};

        int lines   = names.length;
        int lineH_  = LINE_H;
        int boxW    = PREVIEW_W;
        int boxH    = (lines + 1) * lineH_ + 2;

        float sc = tempScale;
        int scaledW = (int)(boxW * sc);
        int scaledH = (int)(boxH * sc);

        // % → ピクセル変換してアンカー座標を求める
        int anchorX = (int)(width  * tempX / 100.0);
        int anchorY = (int)(height * tempY / 100.0);

        int drawX = anchorX - scaledW;
        int drawY = anchorY - scaledH / 2;

        // スケール変換をマトリクススタックに積む
        matrices.push();
        matrices.translate(anchorX, anchorY, 0);
        matrices.scale(sc, sc, 1.0f);
        matrices.translate(-anchorX / sc, -anchorY / sc, 0);

        // スケール後の論理座標でプレビューの左上を計算する
        int lX = (int)(anchorX / sc) - boxW;
        int lY = (int)(anchorY / sc) - boxH / 2;

        ClientConfig cfg = ClientConfigManager.config;

        // ヘッダー行を描画する
        DrawableHelper.fill(matrices, lX, lY, lX + boxW, lY + lineH_ + 1,
                cfg.scoreboardHeaderColor);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int titleW = tr.getWidth(title);
        // タイトルを中央揃えで描画する
        tr.drawWithShadow(matrices, title, lX + (boxW - titleW) / 2, lY + 1,
                cfg.scoreboardTextColor);

        // エントリ行を描画する
        for (int i = 0; i < lines; i++) {
            int rowY = lY + (i + 1) * lineH_ + 1;
            DrawableHelper.fill(matrices, lX, rowY, lX + boxW, rowY + lineH_,
                    cfg.scoreboardBodyColor);

            // 順位・名前・スコアを描画する
            String rank   = String.format("%2d ", i + 1);
            int rankW     = tr.getWidth(rank);
            tr.drawWithShadow(matrices, rank,     lX + 2,                   rowY, 0xFFAAAAAA);
            tr.drawWithShadow(matrices, names[i], lX + 2 + rankW,           rowY, cfg.scoreboardTextColor);
            String sc_str = String.valueOf(scores[i]);
            // スコアを右寄せで描画する
            tr.drawWithShadow(matrices, sc_str, lX + boxW - tr.getWidth(sc_str) - 2,
                    rowY, cfg.scoreboardScoreColor);
        }

        // ホバー or ドラッグ中は黄色ボーダーを描画する
        boolean hovered = mouseX >= drawX && mouseX <= drawX + scaledW
                && mouseY >= drawY && mouseY <= drawY + scaledH;
        if (hovered || dragging) {
            int bx = lX - 1;
            int by = lY - 1;
            int bxe = lX + boxW + 1;
            int bye = lY + boxH + 1;
            int c = 0xFFFFFF00;
            // 4辺それぞれ1ピクセルのボーダーを描画する
            DrawableHelper.fill(matrices, bx,  by,  bxe, by + 1, c);
            DrawableHelper.fill(matrices, bx,  bye - 1, bxe, bye, c);
            DrawableHelper.fill(matrices, bx,  by,  bx + 1, bye, c);
            DrawableHelper.fill(matrices, bxe - 1, by,  bxe, bye, c);
        }

        // マトリクスを元に戻す
        matrices.pop();
    }

    // ── 入力処理 ─────────────────────────────────────────────

    // マウスクリックでプレビューのドラッグを開始する
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseOverPreview((int)mouseX, (int)mouseY)) {
            dragging = true;
            float sc  = tempScale;
            int scaledW = (int)(PREVIEW_W * sc);
            int scaledH = (int)(calcPreviewH() * sc);
            int drawX   = (int)(width  * tempX / 100.0) - scaledW;
            int drawY   = (int)(height * tempY / 100.0) - scaledH / 2;
            // クリック位置とプレビュー左上の差をオフセットとして保持する
            dragOffsetX = (int)mouseX - drawX;
            dragOffsetY = (int)mouseY - drawY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // マウスボタンリリース時にドラッグを終了する
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ドラッグ中にプレビューの位置を更新する
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY) {
        if (dragging && button == 0) {
            float sc    = tempScale;
            int scaledW = (int)(PREVIEW_W * sc);
            int scaledH = (int)(calcPreviewH() * sc);
            // マウス座標からアンカー位置を逆算する
            int newAnchorX = (int)mouseX - dragOffsetX + scaledW;
            int newAnchorY = (int)mouseY - dragOffsetY + scaledH / 2;
            // % に変換して 0–100 に収める
            tempX = Math.max(0, Math.min(100, (int)(newAnchorX * 100.0 / width)));
            tempY = Math.max(0, Math.min(100, (int)(newAnchorY * 100.0 / height)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    // 画面が閉じられるとき親画面へ戻る
    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    // ゲームを一時停止しないようにする（背景のゲームが見えたほうが位置調整しやすい）
    @Override
    public boolean shouldPause() { return false; }

    // ── ヘルパー ─────────────────────────────────────────────

    // マウスカーソルがプレビュー上にあるかどうかを判定する
    private boolean isMouseOverPreview(int mouseX, int mouseY) {
        float sc    = tempScale;
        int scaledW = (int)(PREVIEW_W * sc);
        int scaledH = (int)(calcPreviewH() * sc);
        int drawX   = (int)(width  * tempX / 100.0) - scaledW;
        int drawY   = (int)(height * tempY / 100.0) - scaledH / 2;
        return mouseX >= drawX && mouseX <= drawX + scaledW
                && mouseY >= drawY && mouseY <= drawY + scaledH;
    }

    // プレビューのボックス高さを計算する（ダミーデータは 5 行固定）
    private static int calcPreviewH() {
        return (5 + 1) * LINE_H + 2;
    }

    // ── スケールスライダー ────────────────────────────────────

    // スケール値を tempScale に反映するスライダーの内部クラス
    private class ScaleSlider extends SliderWidget {
        ScaleSlider(int x, int y, int w, int h, double initValue) {
            super(x, y, w, h, new LiteralText(""), initValue);
            updateMessage();
        }

        // スライダーラベルを現在のスケール値で更新する
        @Override
        protected void updateMessage() {
            setMessage(new LiteralText(
                    I18n.translate("hikaritweaks.position.scale_prefix") + String.format("%.2f", tempScale) + "x"));
        }

        // 0–1 の相対値を 0.5–3.0 のスケール値に変換して tempScale に反映する
        @Override
        protected void applyValue() {
            tempScale = (float)(0.5 + value * 2.5); // 0-1 → 0.5-3.0
            updateMessage();
        }
    }
}
