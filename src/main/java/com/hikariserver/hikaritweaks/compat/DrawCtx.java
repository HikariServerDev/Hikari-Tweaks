package com.hikariserver.hikaritweaks.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.world.item.ItemStack;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} elif >=1.20 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?} else {
import net.minecraft.client.gui.GuiComponent;
import com.mojang.blaze3d.vertex.PoseStack;
//?}

public final class DrawCtx {
    //? if >=26.1 {
    /*private final GuiGraphicsExtractor ctx;

    public DrawCtx(GuiGraphicsExtractor ctx) {
        this.ctx = ctx;
    }

    public GuiGraphicsExtractor raw() {
        return ctx;
    }
    *///?} elif >=1.20 {
    /*private final GuiGraphics ctx;

    public DrawCtx(GuiGraphics ctx) {
        this.ctx = ctx;
    }

    public GuiGraphics raw() {
        return ctx;
    }
    *///?} else {
    private final PoseStack matrices;

    public DrawCtx(PoseStack matrices) {
        this.matrices = matrices;
    }

    public PoseStack raw() {
        return matrices;
    }
    //?}

    public void fill(int x1, int y1, int x2, int y2, int color) {
        //? if >=1.20 {
        /*ctx.fill(x1, y1, x2, y2, color);
        *///?} else {
        GuiComponent.fill(matrices, x1, y1, x2, y2, color);
        //?}
    }

    public void drawTextWithShadow(Font tr, String text, float x, float y, int color) {
        int c = ColorCompat.opaqueIfNoAlpha(color);
        //? if >=26.1 {
        /*
        ctx.text(tr, text, (int) x, (int) y, c);
        *///?} elif >=1.20 {
        /*ctx.drawString(tr, text, (int) x, (int) y, c);
        *///?} else {
        tr.drawShadow(matrices, text, x, y, c);
        //?}
    }

    public void drawItem(ItemStack stack, int x, int y) {
        //? if >=26.1 {
        /*ctx.item(stack, x, y);
        *///?} elif >=1.20 {
        /*ctx.renderItem(stack, x, y);
        *///?} elif >=1.19.4 {
        /*Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(matrices, stack, x, y);
        *///?} else {
        Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(stack, x, y);
        //?}
    }

    public void push() {
        //? if >=1.21.6 {
        /*ctx.pose().pushMatrix();
        *///?} elif >=1.20 {
        /*ctx.pose().pushPose();
        *///?} else {
        matrices.pushPose();
        //?}
    }

    public void pop() {
        //? if >=1.21.6 {
        /*ctx.pose().popMatrix();
        *///?} elif >=1.20 {
        /*ctx.pose().popPose();
        *///?} else {
        matrices.popPose();
        //?}
    }

    public void translate(float x, float y, float z) {
        //? if >=1.21.6 {
        /*ctx.pose().translate(x, y);
        *///?} elif >=1.20 {
        /*ctx.pose().translate(x, y, z);
        *///?} else {
        matrices.translate(x, y, z);
        //?}
    }

    public void scale(float x, float y, float z) {
        //? if >=1.21.6 {
        /*ctx.pose().scale(x, y);
        *///?} elif >=1.20 {
        /*ctx.pose().scale(x, y, z);
        *///?} else {
        matrices.scale(x, y, z);
        //?}
    }

    public void enableScissor(int left, int top, int right, int bottom) {
        //? if >=1.20 {
        /*ctx.enableScissor(left, top, right, bottom);
        *///?} else {
        com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int fbHeight = window.getHeight();
        com.mojang.blaze3d.systems.RenderSystem.enableScissor(
                (int) (left * scale),
                (int) (fbHeight - bottom * scale),
                Math.max(0, (int) ((right - left) * scale)),
                Math.max(0, (int) ((bottom - top) * scale)));
        //?}
    }

    public void disableScissor() {
        //? if >=1.20 {
        /*ctx.disableScissor();
        *///?} else {
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
        //?}
    }
}
