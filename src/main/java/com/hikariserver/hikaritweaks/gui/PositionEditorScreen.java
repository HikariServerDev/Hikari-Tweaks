package com.hikariserver.hikaritweaks.gui;

import com.hikariserver.hikaritweaks.compat.ScreenCompat;
import com.hikariserver.hikaritweaks.config.ClientConfig;
import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import com.hikariserver.hikaritweaks.compat.DrawCtx;
import com.hikariserver.hikaritweaks.compat.TextCompat;
import com.hikariserver.hikaritweaks.compat.WidgetCompat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.math.MatrixStack;

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
        super(TextCompat.literal(I18n.translate("hikaritweaks.position.title")));
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

        // 座標は PositionEditorLayout に集約している。
        // プレビューのドラッグ判定（handleDragStart）が同じ矩形を参照して
        // 「ボタンの上ではドラッグを始めない」を判断するため、
        // ここに座標をベタ書きすると両者がずれる。
        // スケールスライダー（0.5–3.0 を 0–1 に正規化して渡す）
        scaleSlider = new ScaleSlider(
                PositionEditorLayout.sliderX(width), PositionEditorLayout.sliderY(height),
                PositionEditorLayout.SLIDER_W, PositionEditorLayout.SLIDER_H,
                (tempScale - 0.5f) / 2.5f // 0.5-3.0 → 0-1
        );
        addDrawableChild(scaleSlider);

        // 確定ボタン：tempX/Y/Scale を設定に書き込んで保存する
        addDrawableChild(WidgetCompat.button(
                PositionEditorLayout.confirmX(width), PositionEditorLayout.buttonY(height),
                PositionEditorLayout.BUTTON_W, PositionEditorLayout.BUTTON_H,
                TextCompat.literal("§a" + I18n.translate("hikaritweaks.button.confirm")),
                btn -> {
                    ClientConfig cfg = ClientConfigManager.config;
                    cfg.scoreboardPositionX = tempX;
                    cfg.scoreboardPositionY = tempY;
                    cfg.scoreboardScale     = tempScale;
                    ClientConfigManager.save();
                    returnToParent();
                }
        ));

        // キャンセルボタン：変更を破棄して親画面へ戻る
        addDrawableChild(WidgetCompat.button(
                PositionEditorLayout.cancelX(width), PositionEditorLayout.buttonY(height),
                PositionEditorLayout.BUTTON_W, PositionEditorLayout.BUTTON_H,
                TextCompat.literal("§c" + I18n.translate("hikaritweaks.button.cancel")),
                btn -> returnToParent()
        ));
    }

    // 毎フレーム呼ばれる描画メソッド。
    // 1.20 で Screen.render の引数が MatrixStack から DrawContext に変わったため、
    // 薄いラッパーだけをバージョン分岐させ、描画本体は renderContent に集約する。
    //
    // ★ 背景（暗幕）を「誰が」描くかがバージョンで異なるため、呼び出し順まで分岐させている。
    //   javap で Screen の実装を確認した結果:
    //     〜1.20.1      : Screen.render は renderBackground を呼ばない
    //                     → 呼び出し側（この Mod）が自分で呼ぶ必要がある
    //     1.20.2〜1.21.5: Screen.render が**先頭で** renderBackground を呼ぶ
    //     1.21.6〜      : Screen.render は呼ばない。renderWithTooltip が render より**前に**呼ぶ
    //
    //   1.20.2〜1.21.5 でここから renderBackground を呼ぶと暗幕が 2 枚重なるうえ
    //   （α=0xC0 × 2、1.20.6 以降はブラーも二重）、その後の super.render が
    //   もう一度暗幕を描くので **独自描画（renderContent）が暗幕の下に沈んで見えなくなる**。
    //   そのため renderBackground は呼ばず、super.render の**後**に renderContent を描く。
    //   結果として独自描画がボタンより手前に来るが、埋もれるよりは良いので仕様として許容する。
    //? if >=1.21.6 {
    /*@Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        // 背景は render に入る前に renderWithTooltip が描画済みなので、ここでは呼ばない。
        // 描画順は 1.20 未満と同じ（独自描画 → ウィジェット）に揃えられる。
        renderContent(new DrawCtx(context), mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }
    *///?} elif >=1.20.2 {
    /*@Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        // super.render が先頭で renderBackground を呼ぶため、ここでは呼ばない（二重暗幕の防止）。
        // かつ独自描画がその暗幕に沈まないよう、super.render の後に renderContent を描く。
        super.render(context, mouseX, mouseY, delta);
        renderContent(new DrawCtx(context), mouseX, mouseY, delta);
    }
    *///?} elif >=1.20 {
    /*@Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        // 1.20〜1.20.1 は Screen.render が背景を描かないので、自分で呼ぶ必要がある
        renderBackground(context);
        renderContent(new DrawCtx(context), mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }
    *///?} else {
    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        // 1.20 未満も Screen.render が背景を描かないので、自分で呼ぶ必要がある
        renderBackground(matrices);
        renderContent(new DrawCtx(matrices), mouseX, mouseY, delta);
        super.render(matrices, mouseX, mouseY, delta);
    }
    //?}

    // バージョン非依存の描画本体
    private void renderContent(DrawCtx ctx, int mouseX, int mouseY, float delta) {

        // 操作説明テキストを描画する
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        String msg1 = "§e" + I18n.translate("hikaritweaks.position.drag_hint");
        ctx.drawTextWithShadow(tr, msg1, (width - tr.getWidth(msg1)) / 2f, 12, 0xFFFFFF);
        // 現在のスケール値を表示する
        String msg2 = "§7" + I18n.translate("hikaritweaks.position.scale_prefix") + String.format("%.2f", tempScale) + "x";
        ctx.drawTextWithShadow(tr, msg2, width / 2f + 110, height - 63, 0xFFFFFF);

        // スコアボードのプレビューを描画する
        renderPreview(ctx, mouseX, mouseY);

    }

    // ── プレビュー描画 ─────────────────────────────────────

    // プレビュー用ダミーデータを使ってスコアボードを描画する
    private void renderPreview(DrawCtx ctx, int mouseX, int mouseY) {
        // プレビュー用ダミーデータ
        String title = I18n.translate("hikaritweaks.position.scoreboard_label");
        String[] names  = {"Player1", "Player2", "Player3", "Player4", "Player5"};
        int[]    scores = {1234, 987, 756, 543, 210};

        int lines   = names.length;
        int lineH_  = LINE_H;
        int boxW    = PREVIEW_W;
        int boxH    = (lines + 1) * lineH_ + 2;

        float sc = tempScale;
        int scaledW = PositionEditorLayout.scaled(boxW, sc);
        int scaledH = PositionEditorLayout.scaled(boxH, sc);

        // % → ピクセル変換してアンカー座標を求める
        int anchorX = PositionEditorLayout.anchorX(width,  tempX);
        int anchorY = PositionEditorLayout.anchorY(height, tempY);

        int drawX = anchorX - scaledW;
        int drawY = anchorY - scaledH / 2;

        // スケール変換をマトリクススタックに積む
        ctx.push();
        ctx.translate(anchorX, anchorY, 0);
        ctx.scale(sc, sc, 1.0f);
        ctx.translate(-anchorX / sc, -anchorY / sc, 0);

        // スケール後の論理座標でプレビューの左上を計算する
        int lX = (int)(anchorX / sc) - boxW;
        int lY = (int)(anchorY / sc) - boxH / 2;

        ClientConfig cfg = ClientConfigManager.config;

        // ヘッダー行を描画する
        ctx.fill(lX, lY, lX + boxW, lY + lineH_ + 1,
                cfg.scoreboardHeaderColor);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int titleW = tr.getWidth(title);
        // タイトルを中央揃えで描画する
        ctx.drawTextWithShadow(tr, title, lX + (boxW - titleW) / 2, lY + 1,
                cfg.scoreboardTextColor);

        // エントリ行を描画する
        for (int i = 0; i < lines; i++) {
            int rowY = lY + (i + 1) * lineH_ + 1;
            ctx.fill(lX, rowY, lX + boxW, rowY + lineH_,
                    cfg.scoreboardBodyColor);

            // 順位・名前・スコアを描画する
            String rank   = String.format("%2d ", i + 1);
            int rankW     = tr.getWidth(rank);
            ctx.drawTextWithShadow(tr, rank,     lX + 2,                   rowY, 0xFFAAAAAA);
            ctx.drawTextWithShadow(tr, names[i], lX + 2 + rankW,           rowY, cfg.scoreboardTextColor);
            String sc_str = String.valueOf(scores[i]);
            // スコアを右寄せで描画する
            ctx.drawTextWithShadow(tr, sc_str, lX + boxW - tr.getWidth(sc_str) - 2,
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
            ctx.fill(bx,  by,  bxe, by + 1, c);
            ctx.fill(bx,  bye - 1, bxe, bye, c);
            ctx.fill(bx,  by,  bx + 1, bye, c);
            ctx.fill(bxe - 1, by,  bxe, bye, c);
        }

        // マトリクスを元に戻す
        ctx.pop();
    }

    // ── 入力処理 ─────────────────────────────────────────────

    // マウス系イベントは 1.21.9 で引数が Click レコードにまとめられたため、
    // オーバーライドだけをバージョン分岐させ、処理本体は共通メソッドに寄せている。
    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (handleDragStart(click.x(), click.y(), click.button())) return true;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        if (click.button() == 0) dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double offsetX, double offsetY) {
        if (handleDrag(click.x(), click.y(), click.button())) return true;
        return super.mouseDragged(click, offsetX, offsetY);
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleDragStart(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY) {
        if (handleDrag(mouseX, mouseY, button)) return true;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    //?}

    // プレビュー上でクリックされたらドラッグを開始する（処理したら true）。
    //
    // ★ ここで true を返すと super.mouseClicked() へ落ちず、
    //   プレビュー矩形の下にあるウィジェットは一切クリックできなくなる。
    //   プレビューはアンカー（X%,Y%）を右端・上下中央として左上へ広がるので、
    //   X=50% / Y=100% / スケール 3.0 では 390x168px の矩形が画面中央下に来て
    //   確定ボタン（width/2-105〜width/2-5, height-42〜height-22）を丸ごと覆う。
    //   その状態で「確定」を押してもドラッグが始まるだけで、
    //   **今合わせた位置を保存できない**（キャンセルは矩形の外なので押せる）。
    //   スケールが大きいときはスケールスライダーにも部分的に重なる。
    //   そのため PositionEditorLayout.startsPreviewDrag() が
    //   「下部コントロールの上ではドラッグを始めない」を担保する。
    private boolean handleDragStart(double mouseX, double mouseY, int button) {
        if (button == 0 && PositionEditorLayout.startsPreviewDrag(
                mouseX, mouseY, width, height, tempX, tempY, tempScale,
                PREVIEW_W, calcPreviewH())) {
            dragging = true;
            float sc  = tempScale;
            int scaledW = PositionEditorLayout.scaled(PREVIEW_W, sc);
            int scaledH = PositionEditorLayout.scaled(calcPreviewH(), sc);
            int drawX   = PositionEditorLayout.previewX(width,  tempX, scaledW);
            int drawY   = PositionEditorLayout.previewY(height, tempY, scaledH);
            // クリック位置とプレビュー左上の差をオフセットとして保持する
            dragOffsetX = (int)mouseX - drawX;
            dragOffsetY = (int)mouseY - drawY;
            return true;
        }
        return false;
    }

    // ドラッグ中にプレビューの位置を更新する（処理したら true）
    private boolean handleDrag(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            float sc    = tempScale;
            int scaledW = PositionEditorLayout.scaled(PREVIEW_W, sc);
            int scaledH = PositionEditorLayout.scaled(calcPreviewH(), sc);
            // マウス座標からアンカー位置を逆算する
            int newAnchorX = (int)mouseX - dragOffsetX + scaledW;
            int newAnchorY = (int)mouseY - dragOffsetY + scaledH / 2;
            // % に変換して 0–100 に収める
            tempX = Math.max(0, Math.min(100, (int)(newAnchorX * 100.0 / width)));
            tempY = Math.max(0, Math.min(100, (int)(newAnchorY * 100.0 / height)));
            return true;
        }
        return false;
    }

    // 画面が閉じられるとき親画面へ戻る。
    // 1.18.2 で Screen.onClose() が close() にリネームされたため分岐する。
    //? if >=1.18.2 {
    @Override
    public void close() {
        returnToParent();
    }
    //?} else {
    /*@Override
    public void onClose() {
        returnToParent();
    }
    *///?}

    // 親画面へ戻る（バージョン非依存。内部からはこちらを呼ぶこと）
    private void returnToParent() {
        if (client != null) ScreenCompat.setScreen(client, parent);
    }

    // ゲームを一時停止しないようにする（背景のゲームが見えたほうが位置調整しやすい）。
    // 1.18 で Screen.isPauseScreen() が shouldPause() にリネームされた。
    //? if >=1.18 {
    @Override
    public boolean shouldPause() { return false; }
    //?} else {
    /*@Override
    public boolean isPauseScreen() { return false; }
    *///?}

    // ── ヘルパー ─────────────────────────────────────────────

    // プレビューのボックス高さを計算する（ダミーデータは 5 行固定）
    private static int calcPreviewH() {
        return (5 + 1) * LINE_H + 2;
    }

    // ── スケールスライダー ────────────────────────────────────

    // スケール値を tempScale に反映するスライダーの内部クラス
    private class ScaleSlider extends SliderWidget {
        ScaleSlider(int x, int y, int w, int h, double initValue) {
            super(x, y, w, h, TextCompat.literal(""), initValue);
            updateMessage();
        }

        // スライダーラベルを現在のスケール値で更新する
        @Override
        protected void updateMessage() {
            setMessage(TextCompat.literal(
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
