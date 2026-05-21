package com.hikariserver.hikaritweaks.mixin;

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
@Mixin(InGameHud.class)
public class MixinInGameHud {

    // バニラのサイドバー描画を制御する。
    // scoreboardHideVanilla が true の場合のみキャンセル。
    // scoreboardCustomHud とは独立した設定。
    @Inject(
        method = "renderScoreboardSidebar",
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

    // renderHud の末尾でカスタム HUD を描画する
    @Inject(
        method = "render",
        at = @At("TAIL")
    )
    private void hikariTweaks$renderCustomHud(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        // カスタムスコアボード HUD を毎フレーム描画する
        ScoreboardHudRenderer.render(matrices);
    }
}
