package com.hikariserver.hikaritweaks.restock;

import com.hikariserver.hikaritweaks.compat.InventoryCompat;
import com.hikariserver.hikaritweaks.compat.ItemCompat;
import com.hikariserver.hikaritweaks.compat.RegistryCompat;
import com.hikariserver.hikaritweaks.compat.ScreenCompat;
import com.hikariserver.hikaritweaks.compat.TextCompat;
import com.hikariserver.hikaritweaks.config.TweaksOptions;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AutoRestockHotbarHandler {
    private static final int MAX_SYNC_WAIT_TICKS = 20;

    private static Screen lastProcessedScreen;
    private static Screen pendingScreen;
    private static int pendingTicks;

    private AutoRestockHotbarHandler() {}

    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            clearState();
            return;
        }

        if (!TweaksOptions.AUTO_RESTOCK_HOTBAR.getBooleanValue()) {
            clearState();
            return;
        }

        Screen screen = ScreenCompat.currentScreen(client);
        if (!(screen instanceof AbstractContainerScreen<?> handledScreen)) {
            clearState();
            return;
        }

        if (screen == lastProcessedScreen) {
            return;
        }

        if (screen != pendingScreen) {
            pendingScreen = screen;
            pendingTicks = 0;
        }

        if (screen instanceof InventoryScreen || handledScreen.getMenu() instanceof InventoryMenu) {
            markProcessed(screen);
            return;
        }

        if (process(client, handledScreen)) {
            markProcessed(screen);
        } else if (++pendingTicks >= MAX_SYNC_WAIT_TICKS) {
            markProcessed(screen);
        }
    }

    private static void markProcessed(Screen screen) {
        lastProcessedScreen = screen;
        pendingScreen = null;
        pendingTicks = 0;
    }

    private static void clearState() {
        lastProcessedScreen = null;
        pendingScreen = null;
        pendingTicks = 0;
    }

    private static boolean process(Minecraft client, AbstractContainerScreen<?> handledScreen) {
        AbstractContainerMenu handler = handledScreen.getMenu();
        List<Slot> containerSlots = new ArrayList<>();
        List<Slot> hotbarSlots = new ArrayList<>();

        for (Slot slot : handler.slots) {
            if (slot.container instanceof Inventory && slot.getContainerSlot() >= 0 && slot.getContainerSlot() < 9) {
                hotbarSlots.add(slot);
            } else if (!(slot.container instanceof Inventory)) {
                containerSlots.add(slot);
            }
        }

        if (containerSlots.isEmpty() || hotbarSlots.isEmpty()) {
            return true;
        }

        if (!isTrustedContainer(client, containerSlots.size())) {
            return true;
        }

        if (!hasAnyItem(containerSlots)) {
            return false;
        }

        Map<String, Integer> movedAmounts = new LinkedHashMap<>();
        for (Slot hotbarSlot : hotbarSlots) {
            ItemStack hotbarStack = hotbarSlot.getItem();
            if (hotbarStack.isEmpty() || hotbarStack.getCount() >= hotbarStack.getMaxStackSize()) {
                continue;
            }

            String itemId = RegistryCompat.itemId(hotbarStack.getItem());
            if (!TweaksOptions.HOTBAR_RESTOCK_LIST.getStrings().contains(itemId)) {
                continue;
            }

            int moved = restockSlot(client, handledScreen, hotbarSlot, containerSlots);
            if (moved <= 0) {
                continue;
            }

            String displayName = hotbarStack.getHoverName().copy().withStyle(getItemFormatting(hotbarStack)).getString();
            movedAmounts.merge(displayName, moved, Integer::sum);
        }

        if (!movedAmounts.isEmpty()) {
            TextCompat.sendActionBar(client.player, Component.nullToEmpty(buildActionbarMessage(movedAmounts)));
            client.player.closeContainer();
        }
        return true;
    }

    private static boolean isTrustedContainer(Minecraft client, int containerSlotCount) {
        if (client.level == null) {
            return false;
        }

        HitResult hit = client.hitResult;
        if (!(hit instanceof BlockHitResult blockHitResult)) {
            return false;
        }

        BlockEntity blockEntity = client.level.getBlockEntity(blockHitResult.getBlockPos());
        if (blockEntity == null) {
            return false;
        }

        if (blockEntity instanceof EnderChestBlockEntity) {
            return false;
        }

        if (!(blockEntity instanceof Container blockInventory)) {
            return false;
        }

        boolean allowDouble = blockEntity instanceof ChestBlockEntity;
        return RestockRules.matchesContainerSize(containerSlotCount, blockInventory.getContainerSize(), allowDouble);
    }

    private static boolean hasAnyItem(List<Slot> containerSlots) {
        for (Slot slot : containerSlots) {
            if (!slot.getItem().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int restockSlot(Minecraft client, AbstractContainerScreen<?> handledScreen, Slot hotbarSlot, List<Slot> containerSlots) {
        int movedTotal = 0;

        for (int i = containerSlots.size() - 1; i >= 0; i--) {
            Slot containerSlot = containerSlots.get(i);
            ItemStack containerStack = containerSlot.getItem().copy();
            ItemStack hotbarStack = hotbarSlot.getItem().copy();

            if (containerStack.isEmpty() || hotbarStack.isEmpty()) {
                continue;
            }

            if (!ItemCompat.canCombine(containerStack, hotbarStack)) {
                continue;
            }

            int moveAmount = RestockRules.plannedMoveCount(
                    hotbarStack.getCount(), hotbarStack.getMaxStackSize(), containerStack.getCount());
            if (moveAmount <= 0) {
                break;
            }

            moveItems(client, handledScreen, containerSlot, hotbarSlot, moveAmount);
            movedTotal += moveAmount;
        }

        return movedTotal;
    }

    private static void moveItems(Minecraft client, AbstractContainerScreen<?> handledScreen, Slot containerSlot, Slot hotbarSlot, int moveAmount) {
        MultiPlayerGameMode interactionManager = client.gameMode;
        if (interactionManager == null || client.player == null || moveAmount <= 0) {
            return;
        }

        int syncId = handledScreen.getMenu().containerId;
        InventoryCompat.pickup(interactionManager, syncId, containerSlot.index, 0, client.player);
        for (int i = 0; i < moveAmount; i++) {
            InventoryCompat.pickup(interactionManager, syncId, hotbarSlot.index, 1, client.player);
        }
        InventoryCompat.pickup(interactionManager, syncId, containerSlot.index, 0, client.player);
    }

    private static ChatFormatting getItemFormatting(ItemStack stack) {
        return ItemCompat.rarityFormatting(stack);
    }

    private static String buildActionbarMessage(Map<String, Integer> movedAmounts) {
        List<String> contents = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : movedAmounts.entrySet()) {
            contents.add(entry.getKey() + " +" + entry.getValue());
        }
        return I18n.get("hikaritweaks.message.hotbar_restock", String.join(", ", contents));
    }
}
