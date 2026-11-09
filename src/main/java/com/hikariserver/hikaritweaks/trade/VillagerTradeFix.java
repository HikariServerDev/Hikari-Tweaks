package com.hikariserver.hikaritweaks.trade;

import com.hikariserver.hikaritweaks.compat.InventoryCompat;
import com.hikariserver.hikaritweaks.compat.ItemCompat;
import com.hikariserver.hikaritweaks.config.TweaksOptions;
import com.hikariserver.hikaritweaks.mixin.MerchantInventoryAccessor;
import com.hikariserver.hikaritweaks.mixin.MerchantScreenHandlerAccessor;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

public final class VillagerTradeFix {
    private static final int INPUT_SLOT_FIRST   = 0;
    private static final int INPUT_SLOT_SECOND  = 1;
    private static final int OUTPUT_SLOT        = 2;
    private static final int PLAYER_SLOTS_START = 3;
    private static final int PLAYER_SLOTS_END   = 39;

    private static final int MAX_TRADES = 64;

    private static boolean running = false;

    private VillagerTradeFix() {}

    public static boolean onClickSlot(MultiPlayerGameMode interactionManager,
                                      int syncId, int slotId, boolean quickMove,
                                      Player player) {
        if (running) return false;
        if (!quickMove || slotId != OUTPUT_SLOT) return false;
        if (!TweaksOptions.VILLAGER_TRADE_FIX.getBooleanValue()) return false;
        if (!(player.containerMenu instanceof MerchantMenu handler)) return false;
        if (handler.containerId != syncId || handler.slots.size() != PLAYER_SLOTS_END) return false;
        if (!handler.getCarried().isEmpty()) return false;

        MerchantContainer inventory = ((MerchantScreenHandlerAccessor) handler).hikariTweaks$getMerchantInventory();
        MerchantOffer intended = inventory.getActiveOffer();
        if (intended == null || handler.getSlot(OUTPUT_SLOT).getItem().isEmpty()) return false;

        MerchantOffers offers = handler.getOffers();
        int offerIndex = ((MerchantInventoryAccessor) inventory).hikariTweaks$getOfferIndex();
        if (!VillagerTradeRules.scansAllOffers(offerIndex, offers.size())) return false;
        if (!hasRival(offers, intended)) return false;

        running = true;
        try {
            tradeOneByOne(interactionManager, handler, inventory, intended, player);
        } finally {
            running = false;
        }
        return true;
    }

    private static boolean hasRival(MerchantOffers offers, MerchantOffer intended) {
        Item sellItem = intended.getResult().getItem();
        for (MerchantOffer offer : offers) {
            if (offer != intended && !offer.isOutOfStock() && offer.getResult().getItem() == sellItem) {
                return true;
            }
        }
        return false;
    }

    private static void tradeOneByOne(MultiPlayerGameMode interactionManager,
                                      MerchantMenu handler, MerchantContainer inventory,
                                      MerchantOffer intended, Player player) {
        int syncId = handler.containerId;
        for (int i = 0; i < MAX_TRADES; i++) {
            ItemStack output = handler.getSlot(OUTPUT_SLOT).getItem();
            if (output.isEmpty()) return;
            if (inventory.getActiveOffer() != intended) {
                returnInputs(interactionManager, handler, player);
                return;
            }
            int[] plan = depositPlan(handler, output);
            if (plan == null) return;

            InventoryCompat.pickup(interactionManager, syncId, OUTPUT_SLOT, 0, player);
            if (handler.getCarried().isEmpty()) return;
            for (int index : plan) {
                InventoryCompat.pickup(interactionManager, syncId, PLAYER_SLOTS_START + index, 0, player);
            }
            if (!handler.getCarried().isEmpty()) return;
        }
    }

    private static void returnInputs(MultiPlayerGameMode interactionManager,
                                     MerchantMenu handler, Player player) {
        for (int slot : new int[] { INPUT_SLOT_FIRST, INPUT_SLOT_SECOND }) {
            if (!handler.getSlot(slot).getItem().isEmpty()) {
                InventoryCompat.quickMove(interactionManager, handler.containerId, slot, 0, player);
            }
        }
    }

    private static int[] depositPlan(MerchantMenu handler, ItemStack output) {
        int size = PLAYER_SLOTS_END - PLAYER_SLOTS_START;
        int[] counts = new int[size];
        boolean[] sameItem = new boolean[size];
        for (int i = 0; i < size; i++) {
            ItemStack stack = handler.getSlot(PLAYER_SLOTS_START + i).getItem();
            counts[i]   = stack.isEmpty() ? 0 : stack.getCount();
            sameItem[i] = !stack.isEmpty() && ItemCompat.canCombine(stack, output);
        }
        return VillagerTradeRules.depositPlan(counts, sameItem, output.getMaxStackSize(), output.getCount());
    }
}
