package com.hikariserver.hikaritweaks.mixin;

import com.hikariserver.hikaritweaks.config.TweaksOptions;
import fi.dy.masa.malilib.util.EntityUtils;
import fi.dy.masa.minihud.renderer.OverlayRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// MiniHUD の OverlayRenderer に Redirect を挿し、
// フリーカメラ中でもビーコン範囲をプレイヤー位置基準で描画させる修正。
@Mixin(value = OverlayRenderer.class, remap = false)
public class MixinOverlayRenderer {

    // getCameraEntity() の呼び出しをリダイレクトしてプレイヤーエンティティを返す
    @Redirect(
        method = "renderOverlays",
        at = @At(
            value = "INVOKE",
            target = "Lfi/dy/masa/malilib/util/EntityUtils;getCameraEntity()Lnet/minecraft/class_1297;"
        )
    )
    private static Entity hikariTweaks$fixBeaconCamera() {
        // 修正が無効ならデフォルトのカメラエンティティをそのまま返す
        if (!TweaksOptions.FIX_BEACON_RANGE_FREE_CAM.getBooleanValue()) {
            return EntityUtils.getCameraEntity();
        }
        // フリーカメラ中もプレイヤー自身を基準にすることでビーコン範囲が正しく表示される
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null ? client.player : EntityUtils.getCameraEntity();
    }
}
