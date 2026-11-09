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

public final class PositionEditorScreen extends Screen {
    private static final int PREVIEW_W = 130;
    private static final int LINE_H    = 9;

    private final Screen parent;

    private int   tempX;
    private int   tempY;
    private float tempScale;

    private boolean dragging    = false;
    private int     dragOffsetX = 0;
    private int     dragOffsetY = 0;

    private ScaleSlider scaleSlider;

    public PositionEditorScreen(Screen parent) {
        super(TextCompat.literal(I18n.get("hikaritweaks.position.title")));
        this.parent    = parent;
        ClientConfig cfg = ClientConfigManager.config;
        this.tempX     = cfg.scoreboardPositionX;
        this.tempY     = cfg.scoreboardPositionY;
        this.tempScale = cfg.scoreboardScale;
    }

    @Override
    protected void init() {
        super.init();

        scaleSlider = new ScaleSlider(
                PositionEditorLayout.sliderX(width), PositionEditorLayout.sliderY(height),
                PositionEditorLayout.SLIDER_W, PositionEditorLayout.SLIDER_H,
                (tempScale - 0.5f) / 2.5f
        );
        addRenderableWidget(scaleSlider);

        addRenderableWidget(WidgetCompat.button(
                PositionEditorLayout.confirmX(width), PositionEditorLayout.buttonY(height),
                PositionEditorLayout.BUTTON_W, PositionEditorLayout.BUTTON_H,
                TextCompat.literal("§a" + I18n.get("hikaritweaks.button.confirm")),
                btn -> {
                    ClientConfig cfg = ClientConfigManager.config;
                    cfg.scoreboardPositionX = tempX;
                    cfg.scoreboardPositionY = tempY;
                    cfg.scoreboardScale     = tempScale;
                    ClientConfigManager.save();
                    returnToParent();
                }
        ));

        addRenderableWidget(WidgetCompat.button(
                PositionEditorLayout.cancelX(width), PositionEditorLayout.buttonY(height),
                PositionEditorLayout.BUTTON_W, PositionEditorLayout.BUTTON_H,
                TextCompat.literal("§c" + I18n.get("hikaritweaks.button.cancel")),
                btn -> returnToParent()
        ));
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
        Font tr = Minecraft.getInstance().font;
        String msg1 = "§e" + I18n.get("hikaritweaks.position.drag_hint");
        ctx.drawTextWithShadow(tr, msg1, (width - tr.width(msg1)) / 2f, 12, 0xFFFFFF);
        String msg2 = "§7" + I18n.get("hikaritweaks.position.scale_prefix") + String.format("%.2f", tempScale) + "x";
        ctx.drawTextWithShadow(tr, msg2, width / 2f + 110, height - 63, 0xFFFFFF);

        renderPreview(ctx, mouseX, mouseY);
    }

    private void renderPreview(DrawCtx ctx, int mouseX, int mouseY) {
        String title = I18n.get("hikaritweaks.position.scoreboard_label");
        String[] names  = {"Player1", "Player2", "Player3", "Player4", "Player5"};
        int[]    scores = {1234, 987, 756, 543, 210};

        int lines   = names.length;
        int lineH_  = LINE_H;
        int boxW    = PREVIEW_W;
        int boxH    = (lines + 1) * lineH_ + 2;

        float sc = tempScale;
        int scaledW = PositionEditorLayout.scaled(boxW, sc);
        int scaledH = PositionEditorLayout.scaled(boxH, sc);

        int anchorX = PositionEditorLayout.anchorX(width,  tempX);
        int anchorY = PositionEditorLayout.anchorY(height, tempY);

        int drawX = anchorX - scaledW;
        int drawY = anchorY - scaledH / 2;

        ctx.push();
        ctx.translate(anchorX, anchorY, 0);
        ctx.scale(sc, sc, 1.0f);
        ctx.translate(-anchorX / sc, -anchorY / sc, 0);

        int lX = (int)(anchorX / sc) - boxW;
        int lY = (int)(anchorY / sc) - boxH / 2;

        ClientConfig cfg = ClientConfigManager.config;

        ctx.fill(lX, lY, lX + boxW, lY + lineH_ + 1,
                cfg.scoreboardHeaderColor);
        Font tr = Minecraft.getInstance().font;
        int titleW = tr.width(title);
        ctx.drawTextWithShadow(tr, title, lX + (boxW - titleW) / 2, lY + 1,
                cfg.scoreboardTextColor);

        for (int i = 0; i < lines; i++) {
            int rowY = lY + (i + 1) * lineH_ + 1;
            ctx.fill(lX, rowY, lX + boxW, rowY + lineH_,
                    cfg.scoreboardBodyColor);

            String rank   = String.format("%2d ", i + 1);
            int rankW     = tr.width(rank);
            ctx.drawTextWithShadow(tr, rank,     lX + 2,                   rowY, 0xFFAAAAAA);
            ctx.drawTextWithShadow(tr, names[i], lX + 2 + rankW,           rowY, cfg.scoreboardTextColor);
            String sc_str = String.valueOf(scores[i]);
            ctx.drawTextWithShadow(tr, sc_str, lX + boxW - tr.width(sc_str) - 2,
                    rowY, cfg.scoreboardScoreColor);
        }

        boolean hovered = mouseX >= drawX && mouseX <= drawX + scaledW
                && mouseY >= drawY && mouseY <= drawY + scaledH;
        if (hovered || dragging) {
            int bx = lX - 1;
            int by = lY - 1;
            int bxe = lX + boxW + 1;
            int bye = lY + boxH + 1;
            int c = 0xFFFFFF00;
            ctx.fill(bx,  by,  bxe, by + 1, c);
            ctx.fill(bx,  bye - 1, bxe, bye, c);
            ctx.fill(bx,  by,  bx + 1, bye, c);
            ctx.fill(bxe - 1, by,  bxe, bye, c);
        }

        ctx.pop();
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (handleDragStart(click.x(), click.y(), click.button())) return true;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        if (click.button() == 0) dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double offsetX, double offsetY) {
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
            dragOffsetX = (int)mouseX - drawX;
            dragOffsetY = (int)mouseY - drawY;
            return true;
        }
        return false;
    }

    private boolean handleDrag(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            float sc    = tempScale;
            int scaledW = PositionEditorLayout.scaled(PREVIEW_W, sc);
            int scaledH = PositionEditorLayout.scaled(calcPreviewH(), sc);
            int newAnchorX = (int)mouseX - dragOffsetX + scaledW;
            int newAnchorY = (int)mouseY - dragOffsetY + scaledH / 2;
            tempX = Math.max(0, Math.min(100, (int)(newAnchorX * 100.0 / width)));
            tempY = Math.max(0, Math.min(100, (int)(newAnchorY * 100.0 / height)));
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    private void returnToParent() {
        if (minecraft != null) ScreenCompat.setScreen(minecraft, parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static int calcPreviewH() {
        return (5 + 1) * LINE_H + 2;
    }

    private class ScaleSlider extends AbstractSliderButton {
        ScaleSlider(int x, int y, int w, int h, double initValue) {
            super(x, y, w, h, TextCompat.literal(""), initValue);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(TextCompat.literal(
                    I18n.get("hikaritweaks.position.scale_prefix") + String.format("%.2f", tempScale) + "x"));
        }

        @Override
        protected void applyValue() {
            tempScale = (float)(0.5 + value * 2.5);
            updateMessage();
        }
    }
}
