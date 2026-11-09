package com.hikariserver.hikaritweaks.restock;

import com.hikariserver.hikaritweaks.compat.InventoryCompat;
import com.hikariserver.hikaritweaks.compat.ItemCompat;
import com.hikariserver.hikaritweaks.compat.RegistryCompat;
import com.hikariserver.hikaritweaks.compat.ScreenCompat;
import com.hikariserver.hikaritweaks.config.TweaksOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.List;

public final class HandRestockHandler {
    private static final int RESTOCK_THRESHOLD = 5;

    private static final int OFFHAND_SCREEN_SLOT = 45;

    private static final int RESTOCK_INTERVAL_TICKS = 5;

    private static int ticksSinceLastRestock = 0;

    private HandRestockHandler() {}

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;
        if (!TweaksOptions.HAND_RESTOCK.getBooleanValue()) return;

        ticksSinceLastRestock++;
        if (ticksSinceLastRestock < RESTOCK_INTERVAL_TICKS) return;

        List<String> targetIds = TweaksOptions.HAND_RESTOCK_LIST.getStrings();
        if (targetIds.isEmpty()) return;

        Inventory inventory = player.getInventory();
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            ItemStack stack = inventory.getItem(hotbarSlot);
            if (stack.isEmpty()) continue;

            String itemId = RegistryCompat.itemId(stack.getItem());
            if (!targetIds.contains(itemId)) continue;

            if (stack.getCount() <= RESTOCK_THRESHOLD) {
                boolean restocked = tryRestock(client, player, hotbarSlot);
                if (restocked) {
                    ticksSinceLastRestock = 0;
                    return;
                }
            }
        }
    }

    private static boolean tryRestock(
            Minecraft client,
            LocalPlayer player,
            int hotbarSlot
    ) {
        MultiPlayerGameMode im = client.gameMode;
        if (im == null) return false;

        if (ScreenCompat.currentScreen(client) != null) return false;

        if (!(player.containerMenu instanceof InventoryMenu)) return false;

        int syncId = player.containerMenu.containerId;
        int hotbarScreenSlot = 36 + hotbarSlot;

        Inventory inventory = player.getInventory();
        ItemStack hotbarStack = inventory.getItem(hotbarSlot);
        if (hotbarStack.isEmpty()) return false;

        for (int invSlot = 35; invSlot >= 9; invSlot--) {
            ItemStack source = inventory.getItem(invSlot);
            if (source.isEmpty()) continue;

            if (!ItemCompat.canCombine(source, hotbarStack)) continue;

            int take = RestockRules.plannedMoveCount(
                    hotbarStack.getCount(), hotbarStack.getMaxStackSize(), source.getCount());
            if (take <= 0) break;

            int invScreenSlot = invSlot;

            InventoryCompat.pickup(im, syncId, invScreenSlot, 0, player);
            for (int i = 0; i < take; i++) {
                InventoryCompat.pickup(im, syncId, hotbarScreenSlot, 1, player);
            }
            InventoryCompat.pickup(im, syncId, invScreenSlot, 0, player);

            return true;
        }

        return false;
    }
}
