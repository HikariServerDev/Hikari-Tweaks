package com.hikariserver.hikaritweaks.mixin;

import com.hikariserver.hikaritweaks.config.TweaksOptions;
import com.hikariserver.hikaritweaks.litematica.LitematicaAutoRefreshHandler;
import fi.dy.masa.litematica.util.SchematicWorldRefresher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Litematica のレイヤー変更メソッドにフックし、マテリアルリストの自動 Refresh を実行する。
// hikari-tweaks-litematica.mixins.json で required=false として登録されているため
// Litematica が存在しない環境ではこの Mixin は静かに無視される。
@Mixin(value = SchematicWorldRefresher.class, remap = false)
public abstract class MixinSchematicWorldRefresher {

    // X/Y/Z いずれかのレイヤーが変更されたとき Refresh を予約する
    @Inject(
            method = {"updateBetweenX", "updateBetweenY", "updateBetweenZ"},
            at = @At("HEAD"),
            remap = false
    )
    private void hikariTweaks$autoRefreshMaterialList(int min, int max, CallbackInfo ci) {
        // 自動 Refresh が有効なときのみ予約する
        if (TweaksOptions.AUTO_LITEMATICA_REFRESH.getBooleanValue()) {
            LitematicaAutoRefreshHandler.scheduleRefresh();
        }
    }
}
