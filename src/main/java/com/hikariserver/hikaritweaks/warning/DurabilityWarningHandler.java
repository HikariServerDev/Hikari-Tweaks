package com.hikariserver.hikaritweaks.warning;

import com.hikariserver.hikaritweaks.compat.RegistryCompat;
import com.hikariserver.hikaritweaks.compat.SoundCompat;
import com.hikariserver.hikaritweaks.compat.TextCompat;
import com.hikariserver.hikaritweaks.config.TweaksOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundSource;

public final class DurabilityWarningHandler {
    private static final DurabilityWarningState STATE = new DurabilityWarningState();

    private DurabilityWarningHandler() {}

    public static void tick(Minecraft mc) {
        if (!TweaksOptions.DURABILITY_WARNING_ENABLED.getBooleanValue()) {
            STATE.clear();
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        STATE.beginTick();

        boolean playedSound = false;

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);

            if (stack.isEmpty() || !stack.isDamageableItem()) continue;

            int maxDamage = stack.getMaxDamage();
            int damage = stack.getDamageValue();
            int remaining = maxDamage - damage;

            if (!DurabilityWarningState.inWarningState(maxDamage, damage)) continue;

            if (!STATE.offer(slot, identity(stack), damage)) continue;

            int percent = DurabilityWarningState.remainingPercent(remaining, maxDamage);
            TextCompat.sendChat(player,
                    TextCompat.literal(I18n.get("hikaritweaks.message.durability_warning",
                            stack.getHoverName().getString(), remaining, percent)));

            if (playedSound) continue;
            playedSound = true;
            mc.level.playSound(
                    player,
                    player.blockPosition(),
                    SoundCompat.noteBlockPling(),
                    SoundSource.MASTER,
                    1.0F,
                    1.2F
            );
        }

        STATE.endTick();
    }

    public static void reset() {
        STATE.clear();
    }

    private static String identity(ItemStack stack) {
        return RegistryCompat.itemId(stack.getItem()) + "|" + stack.getHoverName().getString();
    }
}
