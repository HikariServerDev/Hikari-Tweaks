package com.hikariserver.hikaritweaks.mixin;

import com.hikariserver.hikaritweaks.compat.DrawCtx;
import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import com.hikariserver.hikaritweaks.scoreboard.ScoreboardHudRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// バニラのサイドバー描画をインターセプトし、
// カスタム HUD 描画に差し替える。
//
// ┌─ 制御マトリクス ──────────────────────────────────────────┐
// │  scoreboardCustomHud  │ scoreboardHideVanilla │ 動作            │
// │  true                 │ true                  │ バニラ非表示・カスタムHUD表示 │
// │  true                 │ false                 │ バニラ表示・カスタムHUD表示（両方） │
// │  false                │ true                  │ バニラ非表示・カスタムHUD非表示 │
// │  false                │ false                 │ バニラ表示・カスタムHUD非表示 │
// └──────────────────────────────────────────────────────────┘
//
// ★ method には必ず**完全な記述子**を書くこと。
//   1.20 以降 InGameHud には renderScoreboardSidebar のオーバーロードが 2 つある:
//     renderScoreboardSidebar(DrawContext, RenderTickCounter|float)  ← 呼び出し元
//     renderScoreboardSidebar(DrawContext, ScoreboardObjective)      ← 実際の描画（こちらが目的）
//   メソッド名だけを書くと Mixin AP が前者を拾ってしまい、
//   **ビルドは通るのに実行時に mixin 適用が失敗する**。
//
// シグネチャの変遷:
//   〜1.19.4 : (MatrixStack, ...)
//   1.20〜1.20.6 : (DrawContext, float)
//   1.21〜   : (DrawContext, RenderTickCounter)
@Mixin(InGameHud.class)
public class MixinInGameHud {

    //? if >=1.21 {
    /*// バニラのサイドバー描画を制御する。
    // scoreboardHideVanilla が true の場合のみキャンセル。
    // scoreboardCustomHud とは独立した設定。
    @Inject(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hikariTweaks$replaceScoreboardSidebar(
            net.minecraft.client.gui.DrawContext context,
            ScoreboardObjective objective,
            CallbackInfo ci) {
        // バニラ非表示設定が有効な場合のみキャンセル（カスタムHUDの有無に関係なく）
        if (ClientConfigManager.config.scoreboardHideVanilla) {
            ci.cancel();
        }
    }

    // render の末尾でカスタム HUD を描画する
    @Inject(
        method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At("TAIL")
    )
    private void hikariTweaks$renderCustomHud(
            net.minecraft.client.gui.DrawContext context,
            net.minecraft.client.render.RenderTickCounter tickCounter,
            CallbackInfo ci) {
        // カスタムスコアボード HUD を毎フレーム描画する
        ScoreboardHudRenderer.render(new DrawCtx(context));
    }
    *///?} elif >=1.20 {
    /*// バニラのサイドバー描画を制御する。
    // scoreboardHideVanilla が true の場合のみキャンセル。
    // scoreboardCustomHud とは独立した設定。
    @Inject(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hikariTweaks$replaceScoreboardSidebar(
            net.minecraft.client.gui.DrawContext context,
            ScoreboardObjective objective,
            CallbackInfo ci) {
        // バニラ非表示設定が有効な場合のみキャンセル（カスタムHUDの有無に関係なく）
        if (ClientConfigManager.config.scoreboardHideVanilla) {
            ci.cancel();
        }
    }

    // render の末尾でカスタム HUD を描画する
    @Inject(
        method = "render(Lnet/minecraft/client/gui/DrawContext;F)V",
        at = @At("TAIL")
    )
    private void hikariTweaks$renderCustomHud(
            net.minecraft.client.gui.DrawContext context,
            float tickDelta,
            CallbackInfo ci) {
        // カスタムスコアボード HUD を毎フレーム描画する
        ScoreboardHudRenderer.render(new DrawCtx(context));
    }
    *///?} else {
    // バニラのサイドバー描画を制御する。
    // scoreboardHideVanilla が true の場合のみキャンセル。
    // scoreboardCustomHud とは独立した設定。
    @Inject(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hikariTweaks$replaceScoreboardSidebar(
            MatrixStack matrices,
            ScoreboardObjective objective,
            CallbackInfo ci) {
        // バニラ非表示設定が有効な場合のみキャンセル（カスタムHUDの有無に関係なく）
        if (ClientConfigManager.config.scoreboardHideVanilla) {
            ci.cancel();
        }
    }

    // render の末尾でカスタム HUD を描画する
    @Inject(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;F)V",
        at = @At("TAIL")
    )
    private void hikariTweaks$renderCustomHud(
            MatrixStack matrices,
            float tickDelta,
            CallbackInfo ci) {
        // カスタムスコアボード HUD を毎フレーム描画する
        ScoreboardHudRenderer.render(new DrawCtx(matrices));
    }
    //?}
}
