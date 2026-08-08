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

// スコアボードの色を個別に設定できる画面。
//
// 設定項目:
//   - ヘッダー背景色 (ARGB)
//   - 本文背景色    (ARGB)
//   - テキスト色    (ARGB)
//   - スコア色      (ARGB)
//   - 自分強調色    (ARGB)
//
// 各色は A/R/G/B 4スライダーで調整し、プレビューをリアルタイムで確認できる。
public final class ColorPickerScreen extends Screen {

    // 編集対象の色を表す列挙型
    public enum Target {
        HEADER ("hikaritweaks.color.header_bg"),
        BODY   ("hikaritweaks.color.body_bg"),
        TEXT   ("hikaritweaks.color.text"),
        SCORE  ("hikaritweaks.color.score"),
        SELF   ("hikaritweaks.color.self_highlight");

        private final String langKey;
        Target(String langKey) { this.langKey = langKey; }
        public String label() { return I18n.translate(langKey); }
    }

    // スライダーの横幅
    private static final int SLIDER_W = 200;
    // スライダーの高さ
    private static final int SLIDER_H = 16;
    // プレビューパネルのサイズ
    private static final int PREVIEW_SIZE = 40;

    // 前の画面への参照
    private final Screen parent;
    // 現在選択中の編集対象（デフォルトはヘッダー）
    private Target activeTarget = Target.HEADER;

    // 現在の各色（未保存）。INDEX は Target.ordinal() に対応する。
    private int[] colors; // INDEX: Header, Body, Text, Score, Self

    // A/R/G/B 各チャンネルのスライダー
    private ComponentSlider sliderA, sliderR, sliderG, sliderB;

    // 親画面を受け取り、設定から初期色を読み込む
    public ColorPickerScreen(Screen parent) {
        super(new LiteralText(I18n.translate("hikaritweaks.color.screen_title")));
        this.parent = parent;
        ClientConfig cfg = ClientConfigManager.config;
        // 設定から各色をコピーして編集用バッファを作る
        this.colors = new int[]{
                cfg.scoreboardHeaderColor,
                cfg.scoreboardBodyColor,
                cfg.scoreboardTextColor,
                cfg.scoreboardScoreColor,
                cfg.scoreboardSelfColor
        };
    }

    // 画面初期化時に呼ばれる
    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    // ウィジェットを再構築する（ターゲット切り替え時にも呼ぶ）
    private void rebuildWidgets() {
        clearChildren();

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int cx = width / 2;

        // ターゲット選択ボタンを横並びで生成する
        Target[] targets = Target.values();
        int tabW = (SLIDER_W + 4) / targets.length;
        for (int i = 0; i < targets.length; i++) {
            final Target t = targets[i];
            int bx = cx - (SLIDER_W + 4) / 2 + i * tabW;
            boolean active = (t == activeTarget);
            // アクティブなターゲットは黄色文字で強調する
            addDrawableChild(new ButtonWidget(
                    bx, 30, tabW - 2, 14,
                    new LiteralText(active ? "§e" + t.label() : t.label()),
                    btn -> { activeTarget = t; rebuildWidgets(); }
            ));
        }

        // 現在選択中のターゲットの色からスライダー初期値を取得する
        int currentColor = colors[activeTarget.ordinal()];
        int startY = 58;
        int step   = SLIDER_H + 6;

        // A/R/G/B スライダーを縦並びで生成する
        sliderA = new ComponentSlider(cx - SLIDER_W / 2, startY,          SLIDER_W, SLIDER_H, "A", (currentColor >> 24) & 0xFF);
        sliderR = new ComponentSlider(cx - SLIDER_W / 2, startY + step,   SLIDER_W, SLIDER_H, "R", (currentColor >> 16) & 0xFF);
        sliderG = new ComponentSlider(cx - SLIDER_W / 2, startY + step*2, SLIDER_W, SLIDER_H, "G", (currentColor >> 8)  & 0xFF);
        sliderB = new ComponentSlider(cx - SLIDER_W / 2, startY + step*3, SLIDER_W, SLIDER_H, "B",  currentColor        & 0xFF);
        addDrawableChild(sliderA);
        addDrawableChild(sliderR);
        addDrawableChild(sliderG);
        addDrawableChild(sliderB);

        // プリセット色ボタンを横並びで生成する
        String[] presetLabels  = {
                I18n.translate("hikaritweaks.color.preset.white"),
                I18n.translate("hikaritweaks.color.preset.black"),
                I18n.translate("hikaritweaks.color.preset.translucent"),
                I18n.translate("hikaritweaks.color.preset.red"),
                I18n.translate("hikaritweaks.color.preset.green"),
                I18n.translate("hikaritweaks.color.preset.yellow"),
                I18n.translate("hikaritweaks.color.preset.cyan")
        };
        int[]    presetColors  = {0xFFFFFFFF, 0xFF000000, 0x66000000, 0xFFFF5555, 0xFF55FF55, 0xFFFFFF55, 0xFF55FFFF};
        int pw = 24, ph = 14, gap = 4;
        int totalW = presetLabels.length * (pw + gap) - gap;
        int px0 = cx - totalW / 2;
        int py  = startY + step * 4 + 4;
        for (int i = 0; i < presetLabels.length; i++) {
            final int color = presetColors[i];
            // プリセットを選択したら色を適用してウィジェットを再構築する
            addDrawableChild(new ButtonWidget(
                    px0 + i * (pw + gap), py, pw, ph,
                    new LiteralText(presetLabels[i]),
                    btn -> { applyColor(color); rebuildWidgets(); }
            ));
        }

        // 保存ボタン：スライダー値を保存して親画面へ戻る
        addDrawableChild(new ButtonWidget(
                cx - 105, height - 28, 100, 20,
                new LiteralText("§a" + I18n.translate("hikaritweaks.button.save_back")),
                btn -> {
                    saveAll();
                    if (client != null) client.setScreen(parent);
                }
        ));
        // キャンセルボタン：保存せず親画面へ戻る
        addDrawableChild(new ButtonWidget(
                cx + 5, height - 28, 100, 20,
                new LiteralText("§c" + I18n.translate("hikaritweaks.button.cancel")),
                btn -> { if (client != null) client.setScreen(parent); }
        ));
    }

    // 指定した色を現在のターゲットに適用する
    private void applyColor(int color) {
        colors[activeTarget.ordinal()] = color;
    }

    // スライダーの現在値から ARGB 色値を組み立てる
    private int buildColor() {
        int a = sliderA != null ? sliderA.getComponentValue() : 0xFF;
        int r = sliderR != null ? sliderR.getComponentValue() : 0xFF;
        int g = sliderG != null ? sliderG.getComponentValue() : 0xFF;
        int b = sliderB != null ? sliderB.getComponentValue() : 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // 現在のスライダー値を colors バッファに反映してから設定ファイルへ保存する
    private void saveAll() {
        // アクティブターゲットの色をスライダーの現在値で更新する
        colors[activeTarget.ordinal()] = buildColor();
        ClientConfig cfg = ClientConfigManager.config;
        cfg.scoreboardHeaderColor = colors[Target.HEADER.ordinal()];
        cfg.scoreboardBodyColor   = colors[Target.BODY.ordinal()];
        cfg.scoreboardTextColor   = colors[Target.TEXT.ordinal()];
        cfg.scoreboardScoreColor  = colors[Target.SCORE.ordinal()];
        cfg.scoreboardSelfColor   = colors[Target.SELF.ordinal()];
        ClientConfigManager.save();
    }

    // 毎フレーム呼ばれる描画メソッド
    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);

        // タイトルを中央揃えで描画する
        TextRenderer tr2 = MinecraftClient.getInstance().textRenderer;
        String titleStr = "§e" + I18n.translate("hikaritweaks.color.title_prefix") + activeTarget.label();
        tr2.drawWithShadow(matrices, titleStr, (width - tr2.getWidth(titleStr)) / 2f, 14, 0xFFFFFF);

        // カラープレビューパネルを描画する（上: 保存済み色、下: 現在のスライダー値）
        int previewX = width / 2 + SLIDER_W / 2 + 12;
        int previewY = 58;
        // 保存済みの色を表示する
        DrawableHelper.fill(matrices, previewX, previewY,
                previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE,
                colors[activeTarget.ordinal()]);
        // スライダーのリアルタイム色を表示する
        int liveColor = buildColor();
        DrawableHelper.fill(matrices, previewX, previewY + PREVIEW_SIZE + 2,
                previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE * 2 + 2, liveColor);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        tr.drawWithShadow(matrices, I18n.translate("hikaritweaks.color.saved"),   previewX, previewY + PREVIEW_SIZE + PREVIEW_SIZE + 6, 0xAAAAAA);
        tr.drawWithShadow(matrices, I18n.translate("hikaritweaks.color.current"), previewX, previewY + PREVIEW_SIZE * 2 + 10, 0xFFFFFF);

        // 現在の ARGB 値を16進数で表示する
        tr.drawWithShadow(matrices,
                String.format("#%08X", liveColor),
                width / 2 - SLIDER_W / 2, 58 + (SLIDER_H + 6) * 4 + 22, 0xCCCCCC);

        super.render(matrices, mouseX, mouseY, delta);
    }

    // 画面が閉じられるとき親画面へ戻る
    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    // A/R/G/B 各チャンネル用スライダーの内部クラス
    private static class ComponentSlider extends SliderWidget {
        // チャンネル名（"A", "R", "G", "B"）
        private final String label;
        // 現在の 0–255 値
        private int componentValue;

        ComponentSlider(int x, int y, int w, int h, String label, int initialValue) {
            // value は 0–1 の相対値として SliderWidget に渡す
            super(x, y, w, h, new LiteralText(""), initialValue / 255.0);
            this.label          = label;
            this.componentValue = initialValue;
            updateMessage();
        }

        // スライダーラベルを "チャンネル名: 値" で更新する
        @Override
        protected void updateMessage() {
            setMessage(new LiteralText(label + ": " + componentValue));
        }

        // スライダーの相対値を 0–255 の整数に変換して保持する
        @Override
        protected void applyValue() {
            componentValue = (int)Math.round(value * 255);
            updateMessage();
        }

        // 現在の 0–255 値を返す
        public int getComponentValue() { return componentValue; }
    }
}
