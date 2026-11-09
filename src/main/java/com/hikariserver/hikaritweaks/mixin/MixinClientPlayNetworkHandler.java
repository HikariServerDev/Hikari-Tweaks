package com.hikariserver.hikaritweaks.mixin;

import com.hikariserver.hikaritweaks.restock.TotemRestockHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPlayNetworkHandler {
    @Shadow
    private ClientLevel level;

    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void hikariTweaks$onEntityStatus(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (packet.getEventId() != 35) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || this.level == null) {
            return;
        }

        Entity entity = packet.getEntity(this.level);
        if (entity != null && entity.getId() == client.player.getId()) {
            TotemRestockHandler.onLocalTotemPopped(client);
        }
    }
}
