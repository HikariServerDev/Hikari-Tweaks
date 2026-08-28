package com.hikariserver.hikaritweaks.mixin;

import com.hikariserver.hikaritweaks.restock.TotemRestockHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// ClientPlayNetworkHandler にインジェクトして EntityStatus パケットを傍受する。
// トーテムが使われたことを示すステータス（35）を検知して補充処理へ橋渡しする。
@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandler {

    // ワールドの参照をシャドウして Mixin 内から利用できるようにする
    @Shadow
    private ClientWorld world;

    // onEntityStatus の末尾に注入し、トーテム使用パケットを検出する
    @Inject(method = "onEntityStatus", at = @At("TAIL"))
    private void hikariTweaks$onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        // ステータス 35 はトーテムの使用を示す
        if (packet.getStatus() != 35) {
            return;
        }

        // プレイヤーとワールドの null チェック
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || this.world == null) {
            return;
        }

        // パケットのエンティティがローカルプレイヤー自身かどうか確認する
        Entity entity = packet.getEntity(this.world);
        if (entity != null && entity.getId() == client.player.getId()) {
            // ローカルプレイヤーのトーテムが使われたので補充処理を呼ぶ
            TotemRestockHandler.onLocalTotemPopped(client);
        }
    }
}
