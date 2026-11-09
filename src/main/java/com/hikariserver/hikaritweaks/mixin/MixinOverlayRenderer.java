package com.hikariserver.hikaritweaks.mixin;

import com.hikariserver.hikaritweaks.config.TweaksOptions;
import fi.dy.masa.malilib.util.EntityUtils;
import fi.dy.masa.minihud.renderer.OverlayRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = OverlayRenderer.class, remap = false)
public class MixinOverlayRenderer {
    //? if >=26.1 {
    /*@Redirect(
        method = "renderOverlays",
        at = @At(
            value = "INVOKE",
            target = "Lfi/dy/masa/malilib/util/EntityUtils;getCameraEntity()Lnet/minecraft/world/entity/Entity;"
        ),
        require = 0
    )
    *///?} else {
    @Redirect(
        method = "renderOverlays",
        at = @At(
            value = "INVOKE",
            target = "Lfi/dy/masa/malilib/util/EntityUtils;getCameraEntity()Lnet/minecraft/class_1297;"
        ),
        require = 0
    )
    //?}
    private static Entity hikariTweaks$fixBeaconCamera() {
        if (!TweaksOptions.FIX_BEACON_RANGE_FREE_CAM.getBooleanValue()) {
            return EntityUtils.getCameraEntity();
        }
        Minecraft client = Minecraft.getInstance();
        return client.player != null ? client.player : EntityUtils.getCameraEntity();
    }
}
