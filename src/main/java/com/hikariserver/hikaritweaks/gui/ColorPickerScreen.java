package com.hikariserver.hikaritweaks.gui;

import com.hikariserver.hikaritweaks.compat.ScreenCompat;
import com.hikariserver.hikaritweaks.config.ClientConfig;
import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import com.hikariserver.hikaritweaks.compat.DrawCtx;
import com.hikariserver.hikaritweaks.compat.TextCompat;
import com.hikariserver.hikaritweaks.compat.WidgetCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.resources.language.I18n;
import com.mojang.blaze3d.vertex.PoseStack;

public final class ColorPickerScreen extends Screen {
    public enum Target {
        HEADER ("hikaritweaks.color.header_bg"),
        BODY   ("hikaritweaks.color.body_bg"),
        TEXT   ("hikaritweaks.color.text"),
        SCORE  ("hikaritweaks.color.score"),
        SELF   ("hikaritweaks.color.self_highlight");

        private final String langKey;
        Target(String langKey) { this.langKey = langKey; }
        public String label() { return I18n.get(langKey); }
    }

    private static final int SLIDER_W = 200;
    private static final int SLIDER_H = 16;
    private static final int PREVIEW_SIZE = 40;

    private final Screen parent;
    private Target activeTarget = Target.HEADER;

    private int[] colors;

    private ComponentSlider sliderA, sliderR, sliderG, sliderB;

    public ColorPickerScreen(Screen parent) {
        super(TextCompat.literal(I18n.get("hikaritweaks.color.screen_title")));
        this.parent = parent;
        ClientConfig cfg = ClientConfigManager.config;
        this.colors = new int[]{
                cfg.scoreboardHeaderColor,
                cfg.scoreboardBodyColor,
                cfg.scoreboardTextColor,
                cfg.scoreboardScoreColor,
                cfg.scoreboardSelfColor
        };
    }

    @Override
    protected void init() {
        super.init();
        rebuildColorWidgets();
    }

    private void rebuildColorWidgets() {
        clearWidgets();

        Font tr = Minecraft.getInstance().font;
        int cx = width / 2;

        Target[] targets = Target.values();
        int tabW = (SLIDER_W + 4) / targets.length;
        for (int i = 0; i < targets.length; i++) {
            final Target t = targets[i];
            int bx = cx - (SLIDER_W + 4) / 2 + i * tabW;
            boolean active = (t == activeTarget);
            addRenderableWidget(WidgetCompat.button(
                    bx, 30, tabW - 2, 14,
                    TextCompat.literal(active ? "§e" + t.label() : t.label()),
                    btn -> {
                        colors[activeTarget.ordinal()] = buildColor();
                        activeTarget = t;
                        rebuildColorWidgets();
                    }
            ));
        }

        int currentColor = colors[activeTarget.ordinal()];
        int startY = 58;
        int step   = SLIDER_H + 6;

        sliderA = new ComponentSlider(cx - SLIDER_W / 2, startY,          SLIDER_W, SLIDER_H, "A", (currentColor >> 24) & 0xFF);
        sliderR = new ComponentSlider(cx - SLIDER_W / 2, startY + step,   SLIDER_W, SLIDER_H, "R", (currentColor >> 16) & 0xFF);
        sliderG = new ComponentSlider(cx - SLIDER_W / 2, startY + step*2, SLIDER_W, SLIDER_H, "G", (currentColor >> 8)  & 0xFF);
        sliderB = new ComponentSlider(cx - SLIDER_W / 2, startY + step*3, SLIDER_W, SLIDER_H, "B",  currentColor        & 0xFF);
        addRenderableWidget(sliderA);
        addRenderableWidget(sliderR);
        addRenderableWidget(sliderG);
        addRenderableWidget(sliderB);

        String[] presetLabels  = {
                I18n.get("hikaritweaks.color.preset.white"),
                I18n.get("hikaritweaks.color.preset.black"),
                I18n.get("hikaritweaks.color.preset.translucent"),
                I18n.get("hikaritweaks.color.preset.red"),
                I18n.get("hikaritweaks.color.preset.green"),
                I18n.get("hikaritweaks.color.preset.yellow"),
                I18n.get("hikaritweaks.color.preset.cyan")
        };
        int[]    presetColors  = {0xFFFFFFFF, 0xFF000000, 0x66000000, 0xFFFF5555, 0xFF55FF55, 0xFFFFFF55, 0xFF55FFFF};
        int pw = 24, ph = 14, gap = 4;
        int totalW = presetLabels.length * (pw + gap) - gap;
        int px0 = cx - totalW / 2;
        int py  = startY + step * 4 + 4;
        for (int i = 0; i < presetLabels.length; i++) {
            final int color = presetColors[i];
            addRenderableWidget(WidgetCompat.button(
                    px0 + i * (pw + gap), py, pw, ph,
                    TextCompat.literal(presetLabels[i]),
                    btn -> { applyColor(color); rebuildColorWidgets(); }
            ));
        }

        addRenderableWidget(WidgetCompat.button(
                cx - 105, height - 28, 100, 20,
                TextCompat.literal("§a" + I18n.get("hikaritweaks.button.save_back")),
                btn -> {
                    saveAll();
                    if (minecraft != null) ScreenCompat.setScreen(minecraft, parent);
                }
        ));
        addRenderableWidget(WidgetCompat.button(
                cx + 5, height - 28, 100, 20,
                TextCompat.literal("§c" + I18n.get("hikaritweaks.button.cancel")),
                btn -> { if (minecraft != null) ScreenCompat.setScreen(minecraft, parent); }
        ));
    }

    private void applyColor(int color) {
        colors[activeTarget.ordinal()] = color;
    }

    private int buildColor() {
        int a = sliderA != null ? sliderA.getComponentValue() : 0xFF;
        int r = sliderR != null ? sliderR.getComponentValue() : 0xFF;
        int g = sliderG != null ? sliderG.getComponentValue() : 0xFF;
        int b = sliderB != null ? sliderB.getComponentValue() : 0xFF;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void saveAll() {
        colors[activeTarget.ordinal()] = buildColor();
        ClientConfig cfg = ClientConfigManager.config;
        cfg.scoreboardHeaderColor = colors[Target.HEADER.ordinal()];
        cfg.scoreboardBodyColor   = colors[Target.BODY.ordinal()];
        cfg.scoreboardTextColor   = colors[Target.TEXT.ordinal()];
        cfg.scoreboardScoreColor  = colors[Target.SCORE.ordinal()];
        cfg.scoreboardSelfColor   = colors[Target.SELF.ordinal()];
        ClientConfigManager.save();
    }

    //? if >=26.1 {
    /*@Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor context,
                                   int mouseX, int mouseY, float delta) {
        renderContent(new DrawCtx(context), mouseX, mouseY, delta);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
    *///?} elif >=1.21.6 {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderContent(new DrawCtx(context), mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }
    *///?} elif >=1.20.2 {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        renderContent(new DrawCtx(context), mouseX, mouseY, delta);
    }
    *///?} elif >=1.20 {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        renderContent(new DrawCtx(context), mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }
    *///?} else {
    @Override
    public void render(PoseStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        renderContent(new DrawCtx(matrices), mouseX, mouseY, delta);
        super.render(matrices, mouseX, mouseY, delta);
    }
    //?}

    private void renderContent(DrawCtx ctx, int mouseX, int mouseY, float delta) {
        Font tr2 = Minecraft.getInstance().font;
        String titleStr = "§e" + I18n.get("hikaritweaks.color.title_prefix") + activeTarget.label();
        ctx.drawTextWithShadow(tr2, titleStr, (width - tr2.width(titleStr)) / 2f, 14, 0xFFFFFF);

        int previewX = width / 2 + SLIDER_W / 2 + 12;
        int previewY = 58;
        ctx.fill(previewX, previewY,
                previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE,
                colors[activeTarget.ordinal()]);
        int liveColor = buildColor();
        ctx.fill(previewX, previewY + PREVIEW_SIZE + 2,
                previewX + PREVIEW_SIZE, previewY + PREVIEW_SIZE * 2 + 2, liveColor);

        Font tr = Minecraft.getInstance().font;
        ctx.drawTextWithShadow(tr, I18n.get("hikaritweaks.color.saved"),   previewX, previewY + PREVIEW_SIZE + PREVIEW_SIZE + 6, 0xAAAAAA);
        ctx.drawTextWithShadow(tr, I18n.get("hikaritweaks.color.current"), previewX, previewY + PREVIEW_SIZE * 2 + 10, 0xFFFFFF);

        ctx.drawTextWithShadow(tr,
                String.format("#%08X", liveColor),
                width / 2 - SLIDER_W / 2, 58 + (SLIDER_H + 6) * 4 + 22, 0xCCCCCC);
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    private void returnToParent() {
        if (minecraft != null) ScreenCompat.setScreen(minecraft, parent);
    }

    private static class ComponentSlider extends AbstractSliderButton {
        private final String label;
        private int componentValue;

        ComponentSlider(int x, int y, int w, int h, String label, int initialValue) {
            super(x, y, w, h, TextCompat.literal(""), initialValue / 255.0);
            this.label          = label;
            this.componentValue = initialValue;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(TextCompat.literal(label + ": " + componentValue));
        }

        @Override
        protected void applyValue() {
            componentValue = (int)Math.round(value * 255);
            updateMessage();
        }

        public int getComponentValue() { return componentValue; }
    }
}
