package com.hikariserver.hikaritweaks.mixin;

import com.hikariserver.hikaritweaks.compat.DrawCtx;
import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import com.hikariserver.hikaritweaks.scoreboard.ScoreboardHudRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Hud.class)
*///?} else {
@Mixin(net.minecraft.client.gui.Gui.class)
//?}
public class MixinInGameHud {
    //? if >=26.1 {
    /*
    @Inject(
        method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hikariTweaks$replaceScoreboardSidebar(
            net.minecraft.client.gui.GuiGraphicsExtractor context,
            Objective objective,
            CallbackInfo ci) {
        if (ClientConfigManager.config.scoreboardHideVanilla) {
            ci.cancel();
        }
    }

    @Inject(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("TAIL")
    )
    private void hikariTweaks$renderCustomHud(
            net.minecraft.client.gui.GuiGraphicsExtractor context,
            net.minecraft.client.DeltaTracker tickCounter,
            CallbackInfo ci) {
        ScoreboardHudRenderer.render(new DrawCtx(context));
    }
    *///?} elif >=1.21 {
    /*
    @Inject(
        method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/scores/Objective;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hikariTweaks$replaceScoreboardSidebar(
            net.minecraft.client.gui.GuiGraphics context,
            Objective objective,
            CallbackInfo ci) {
        if (ClientConfigManager.config.scoreboardHideVanilla) {
            ci.cancel();
        }
    }

    @Inject(
        method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
        at = @At("TAIL")
    )
    private void hikariTweaks$renderCustomHud(
            net.minecraft.client.gui.GuiGraphics context,
            net.minecraft.client.DeltaTracker tickCounter,
            CallbackInfo ci) {
        ScoreboardHudRenderer.render(new DrawCtx(context));
    }
    *///?} elif >=1.20 {
    /*
    @Inject(
        method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/scores/Objective;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hikariTweaks$replaceScoreboardSidebar(
            net.minecraft.client.gui.GuiGraphics context,
            Objective objective,
            CallbackInfo ci) {
        if (ClientConfigManager.config.scoreboardHideVanilla) {
            ci.cancel();
        }
    }

    @Inject(
        method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V",
        at = @At("TAIL")
    )
    private void hikariTweaks$renderCustomHud(
            net.minecraft.client.gui.GuiGraphics context,
            float tickDelta,
            CallbackInfo ci) {
        ScoreboardHudRenderer.render(new DrawCtx(context));
    }
    *///?} else {
    @Inject(
        method = "displayScoreboardSidebar(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/scores/Objective;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hikariTweaks$replaceScoreboardSidebar(
            PoseStack matrices,
            Objective objective,
            CallbackInfo ci) {
        if (ClientConfigManager.config.scoreboardHideVanilla) {
            ci.cancel();
        }
    }

    @Inject(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
        at = @At("TAIL")
    )
    private void hikariTweaks$renderCustomHud(
            PoseStack matrices,
            float tickDelta,
            CallbackInfo ci) {
        ScoreboardHudRenderer.render(new DrawCtx(matrices));
    }
    //?}
}
