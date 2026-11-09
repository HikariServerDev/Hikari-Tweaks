package com.hikariserver.hikaritweaks.mixin;

import com.hikariserver.hikaritweaks.trade.VillagerTradeFix;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
//? if <26.1 {
import net.minecraft.world.inventory.ClickType;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinClientPlayerInteractionManager {
    //? if >=26.1 {
    /*@Inject(
        method = "handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hikariTweaks$villagerTradeFix(int syncId, int slotId, int button,
                                               net.minecraft.world.inventory.ContainerInput actionType,
                                               Player player,
                                               CallbackInfo ci) {
        if (VillagerTradeFix.onClickSlot((MultiPlayerGameMode) (Object) this, syncId, slotId,
                actionType == net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player)) {
            ci.cancel();
        }
    }
    *///?} else {
    @Inject(
        method = "handleInventoryMouseClick(IIILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hikariTweaks$villagerTradeFix(int syncId, int slotId, int button,
                                               ClickType actionType, Player player,
                                               CallbackInfo ci) {
        if (VillagerTradeFix.onClickSlot((MultiPlayerGameMode) (Object) this, syncId, slotId,
                actionType == ClickType.QUICK_MOVE, player)) {
            ci.cancel();
        }
    }
    //?}
}
