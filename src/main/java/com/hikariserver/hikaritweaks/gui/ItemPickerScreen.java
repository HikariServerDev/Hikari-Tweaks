package com.hikariserver.hikaritweaks.gui;

import com.hikariserver.hikaritweaks.compat.DrawCtx;
import com.hikariserver.hikaritweaks.compat.MaliLibConfigCompat;
import com.hikariserver.hikaritweaks.compat.RegistryCompat;
import com.hikariserver.hikaritweaks.compat.ScreenCompat;
import com.hikariserver.hikaritweaks.compat.TextCompat;
import com.hikariserver.hikaritweaks.compat.WidgetCompat;
import com.hikariserver.hikaritweaks.config.TweaksOptions;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ItemPickerScreen extends Screen {
    public enum Target {
        HOTBAR_RESTOCK(TweaksOptions.HOTBAR_RESTOCK_LIST),
        HAND_RESTOCK(TweaksOptions.HAND_RESTOCK_LIST);

        private final ConfigStringList option;

        Target(ConfigStringList option) { this.option = option; }

        String title() { return I18n.get(MaliLibConfigCompat.nameKey(option.getName())); }
    }

    private record Entry(String id, ItemStack stack, String name) {}

    private record Row(String header, List<Entry> items) {}

    private static final int CELL            = ItemPickerRules.CELL;
    private static final int MARGIN          = 10;
    private static final int TITLE_Y         = 8;
    private static final int SEARCH_Y        = 22;
    private static final int SEARCH_H        = 16;
    private static final int SEARCH_MAX_W    = 200;
    private static final int STATUS_Y        = 44;
    private static final int GRID_TOP        = 56;
    private static final int SCROLLBAR_W     = 6;
    private static final int REGISTERED_ROWS = 2;
    private static final int LABEL_H         = 12;
    private static final int BUTTON_W        = 100;
    private static final int BUTTON_H        = 20;
    private static final int ROWS_PER_NOTCH  = 2;

    private static final int REGISTERED_BG = 0x6640C040;
    private static final int HOVER_BG      = 0x50FFFFFF;
    private static final int SLOT_BG       = 0x40000000;
    private static final int REMOVE_BG     = 0x80FF4040;

    private final Screen parent;
    private final Target target;
    private List<String> ids;

    private final List<Entry> allItems = new ArrayList<>();
    private final Map<String, Entry> byId = new HashMap<>();
    private final List<Entry> inventoryItems = new ArrayList<>();
    private List<Row> rows = new ArrayList<>();

    private String query = "";
    private int scrollRow = 0;
    private boolean draggingScrollbar = false;
    private double dragStartMouseY = 0;
    private int dragStartScrollPx = 0;

    private Entry hoveredEntry;
    private String hoveredRegisteredId;

    private int columns;
    private int visibleRows;
    private int registeredTop;
    private int registeredColumns;

    public ItemPickerScreen(Screen parent, Target target) {
        super(TextCompat.literal(target.title()));
        this.parent = parent;
        this.target = target;
        this.ids = new ArrayList<>(target.option.getStrings());
    }

    @Override
    protected void init() {
        super.init();
        if (allItems.isEmpty()) collectItems();
        collectInventory();
        computeLayout();

        String label = I18n.get("hikaritweaks.item_picker.search");
        int fieldX = MARGIN + this.font.width(label) + 4;
        EditBox searchField = new EditBox(this.font, fieldX, SEARCH_Y,
                Math.min(SEARCH_MAX_W, this.width - fieldX - MARGIN), SEARCH_H, TextCompat.literal(label));
        searchField.setMaxLength(64);
        searchField.setValue(query);
        searchField.setResponder(this::onQueryChanged);
        addRenderableWidget(searchField);
        setInitialFocus(searchField);

        addRenderableWidget(WidgetCompat.button(
                this.width / 2 - BUTTON_W / 2, this.height - BUTTON_H - 4, BUTTON_W, BUTTON_H,
                I18n.get("hikaritweaks.button.done"),
                btn -> returnToParent()));

        rebuildRows();
    }

    private void collectItems() {
        for (Item item : RegistryCompat.items()) {
            if (item == Items.AIR) continue;
            ItemStack stack = new ItemStack(item);
            String id = RegistryCompat.itemId(item);
            Entry entry = new Entry(id, stack, stack.getHoverName().getString());
            allItems.add(entry);
            byId.put(id, entry);
        }
    }

    private void collectInventory() {
        inventoryItems.clear();
        if (this.minecraft == null || this.minecraft.player == null) return;
        Inventory inventory = this.minecraft.player.getInventory();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            String id = RegistryCompat.itemId(stack.getItem());
            Entry entry = byId.get(id);
            if (entry != null && seen.add(id)) inventoryItems.add(entry);
        }
    }

    private void computeLayout() {
        columns = ItemPickerRules.columns(this.width - MARGIN * 2 - SCROLLBAR_W - 4);
        registeredColumns = ItemPickerRules.columns(this.width - MARGIN * 2);
        registeredTop = this.height - BUTTON_H - 8 - REGISTERED_ROWS * CELL - LABEL_H;
        visibleRows = Math.max(1, (registeredTop - 6 - GRID_TOP) / CELL);
    }

    private void onQueryChanged(String text) {
        query = text;
        scrollRow = 0;
        rebuildRows();
    }

    private void rebuildRows() {
        List<Row> out = new ArrayList<>();
        addSection(out, "hikaritweaks.item_picker.inventory", inventoryItems);
        addSection(out, "hikaritweaks.item_picker.all_items", allItems);
        rows = out;
        scrollRow = ScoreboardListLayout.clampScroll(scrollRow, maxScrollRows());
    }

    private void addSection(List<Row> out, String langKey, List<Entry> source) {
        List<Entry> hits = new ArrayList<>();
        for (Entry entry : source) {
            if (ItemPickerRules.matches(query, entry.id(), entry.name())) hits.add(entry);
        }
        if (hits.isEmpty()) return;
        out.add(new Row(I18n.get(langKey), List.of()));
        for (int i = 0; i < hits.size(); i += columns) {
            out.add(new Row(null, hits.subList(i, Math.min(i + columns, hits.size()))));
        }
    }

    private int maxScrollRows() {
        return ScoreboardListLayout.maxScroll(rows.size(), visibleRows);
    }

    private void apply(List<String> next) {
        ids = next;
        target.option.setStrings(new ArrayList<>(next));
    }

    //? if >=26.1 {
    /*@Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor context,
                                   int mouseX, int mouseY, float delta) {
        renderContent(new DrawCtx(context), mouseX, mouseY);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
    *///?} elif >=1.21.6 {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderContent(new DrawCtx(context), mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
    }
    *///?} elif >=1.20.2 {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        renderContent(new DrawCtx(context), mouseX, mouseY);
    }
    *///?} elif >=1.20 {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        renderContent(new DrawCtx(context), mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
    }
    *///?} else {
    @Override
    public void render(PoseStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        renderContent(new DrawCtx(matrices), mouseX, mouseY);
        super.render(matrices, mouseX, mouseY, delta);
    }
    //?}

    private void renderContent(DrawCtx ctx, int mouseX, int mouseY) {
        Font tr = this.font;
        hoveredEntry = null;
        hoveredRegisteredId = null;

        String title = "§e" + target.title();
        ctx.drawTextWithShadow(tr, title, (this.width - tr.width(title)) / 2f, TITLE_Y, 0xFFFFFF);
        ctx.drawTextWithShadow(tr, I18n.get("hikaritweaks.item_picker.search"), MARGIN, SEARCH_Y + 4, 0xFFFFFF);

        renderGrid(ctx, tr, mouseX, mouseY);
        renderRegistered(ctx, tr, mouseX, mouseY);
        ctx.drawTextWithShadow(tr, statusText(), MARGIN, STATUS_Y, 0xFFFFFF);
    }

    private void renderGrid(DrawCtx ctx, Font tr, int mouseX, int mouseY) {
        int maxScroll = maxScrollRows();
        scrollRow = ScoreboardListLayout.clampScroll(scrollRow, maxScroll);

        if (rows.isEmpty()) {
            ctx.drawTextWithShadow(tr, "§7" + I18n.get("hikaritweaks.item_picker.no_results"),
                    MARGIN, GRID_TOP + 5, 0xAAAAAA);
            return;
        }

        for (int r = 0; r < visibleRows; r++) {
            int index = scrollRow + r;
            if (index >= rows.size()) break;
            Row row = rows.get(index);
            int y = GRID_TOP + r * CELL;
            if (row.header() != null) {
                ctx.drawTextWithShadow(tr, "§7" + row.header(), MARGIN, y + 5, 0xAAAAAA);
                continue;
            }
            for (int c = 0; c < row.items().size(); c++) {
                Entry entry = row.items().get(c);
                int x = MARGIN + c * CELL;
                if (ids.contains(entry.id())) ctx.fill(x, y, x + CELL, y + CELL, REGISTERED_BG);
                if (isInside(mouseX, mouseY, x, y)) {
                    ctx.fill(x, y, x + CELL, y + CELL, HOVER_BG);
                    hoveredEntry = entry;
                }
                ctx.drawItem(entry.stack(), x + 1, y + 1);
            }
        }

        if (maxScroll > 0) renderScrollbar(ctx, mouseX, mouseY, maxScroll);
    }

    private void renderScrollbar(DrawCtx ctx, int mouseX, int mouseY, int maxScroll) {
        int visibleH = visibleRows * CELL;
        int top      = GRID_TOP;
        int bottom   = GRID_TOP + visibleH;
        int barX     = scrollbarX();
        int barH     = ScoreboardListLayout.scrollbarHeight(visibleH, rows.size() * CELL);
        int barY     = ScoreboardListLayout.scrollbarY(top, bottom, barH, visibleH,
                scrollRow * CELL, maxScroll * CELL);
        boolean hovered = mouseX >= barX && mouseX < barX + SCROLLBAR_W && mouseY >= barY && mouseY < barY + barH;
        ctx.fill(barX, top, barX + SCROLLBAR_W, bottom, 0x55000000);
        ctx.fill(barX, barY, barX + SCROLLBAR_W, barY + barH,
                draggingScrollbar ? 0xFFFFFFFF : hovered ? 0xCCFFFFFF : 0x99AAAAAA);
    }

    private void renderRegistered(DrawCtx ctx, Font tr, int mouseX, int mouseY) {
        ctx.drawTextWithShadow(tr, I18n.get("hikaritweaks.item_picker.registered", ids.size()),
                MARGIN, registeredTop + 2, 0xFFFFFF);

        int shown = registeredShownCount();
        for (int i = 0; i < shown; i++) {
            String id = ids.get(i);
            int x = registeredX(i);
            int y = registeredY(i);
            boolean hovered = isInside(mouseX, mouseY, x, y);
            ctx.fill(x, y, x + CELL, y + CELL, hovered ? REMOVE_BG : SLOT_BG);
            Entry entry = byId.get(id);
            if (entry != null) {
                ctx.drawItem(entry.stack(), x + 1, y + 1);
            } else {
                ctx.drawTextWithShadow(tr, "§c?", x + 6, y + 5, 0xFFFFFF);
            }
            if (hovered) hoveredRegisteredId = id;
        }
        if (shown < ids.size()) {
            ctx.drawTextWithShadow(tr, "§7+" + (ids.size() - shown),
                    registeredX(shown) + 2, registeredY(shown) + 5, 0xAAAAAA);
        }
    }

    private String statusText() {
        if (hoveredEntry != null) {
            boolean registered = ids.contains(hoveredEntry.id());
            return hoveredEntry.name() + "  §8" + hoveredEntry.id() + "  "
                    + (registered
                        ? "§c" + I18n.get("hikaritweaks.item_picker.click_to_remove")
                        : "§a" + I18n.get("hikaritweaks.item_picker.click_to_add"));
        }
        if (hoveredRegisteredId != null) {
            Entry entry = byId.get(hoveredRegisteredId);
            String name = entry != null ? entry.name() : I18n.get("hikaritweaks.item_picker.unknown_item");
            return name + "  §8" + hoveredRegisteredId + "  §c"
                    + I18n.get("hikaritweaks.item_picker.click_to_remove");
        }
        return "§7" + I18n.get("hikaritweaks.item_picker.hint");
    }

    private int scrollbarX() {
        return MARGIN + columns * CELL + 4;
    }

    private int registeredShownCount() {
        int capacity = registeredColumns * REGISTERED_ROWS;
        return ids.size() <= capacity ? ids.size() : capacity - 1;
    }

    private int registeredX(int index) {
        return MARGIN + (index % registeredColumns) * CELL;
    }

    private int registeredY(int index) {
        return registeredTop + LABEL_H + (index / registeredColumns) * CELL;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
    }

    private Entry entryAt(double mouseX, double mouseY) {
        int r = ItemPickerRules.rowAt(mouseY, GRID_TOP, visibleRows);
        int c = ItemPickerRules.columnAt(mouseX, MARGIN, columns);
        if (r < 0 || c < 0) return null;
        int index = scrollRow + r;
        if (index >= rows.size()) return null;
        Row row = rows.get(index);
        if (row.header() != null || c >= row.items().size()) return null;
        return row.items().get(c);
    }

    private int registeredIndexAt(double mouseX, double mouseY) {
        int r = ItemPickerRules.rowAt(mouseY, registeredTop + LABEL_H, REGISTERED_ROWS);
        int c = ItemPickerRules.columnAt(mouseX, MARGIN, registeredColumns);
        if (r < 0 || c < 0) return -1;
        int index = r * registeredColumns + c;
        return index < registeredShownCount() ? index : -1;
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (handleClick(click.x(), click.y(), click.button())) return true;
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        if (handleRelease()) return true;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double offsetX, double offsetY) {
        if (handleDrag(click.y())) return true;
        return super.mouseDragged(click, offsetX, offsetY);
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleClick(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (handleRelease()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (handleDrag(mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    //?}

    //? if >=1.20.2 {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (handleScroll(mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    *///?} else {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (handleScroll(mouseY, amount)) return true;
        return super.mouseScrolled(mouseX, mouseY, amount);
    }
    //?}

    private boolean handleClick(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (maxScrollRows() > 0
                && mouseX >= scrollbarX() && mouseX < scrollbarX() + SCROLLBAR_W
                && mouseY >= GRID_TOP && mouseY < GRID_TOP + visibleRows * CELL) {
            draggingScrollbar = true;
            dragStartMouseY   = mouseY;
            dragStartScrollPx = scrollRow * CELL;
            return true;
        }

        Entry entry = entryAt(mouseX, mouseY);
        if (entry != null) {
            apply(ItemPickerRules.toggle(ids, entry.id()));
            return true;
        }

        int registered = registeredIndexAt(mouseX, mouseY);
        if (registered >= 0) {
            apply(ItemPickerRules.toggle(ids, ids.get(registered)));
            return true;
        }
        return false;
    }

    private boolean handleRelease() {
        if (!draggingScrollbar) return false;
        draggingScrollbar = false;
        return true;
    }

    private boolean handleDrag(double mouseY) {
        if (!draggingScrollbar) return false;
        int visibleH = visibleRows * CELL;
        int barH     = ScoreboardListLayout.scrollbarHeight(visibleH, rows.size() * CELL);
        int trackH   = ScoreboardListLayout.trackHeight(visibleH, barH);
        int px = ScoreboardListLayout.scrollFromDrag(
                dragStartScrollPx, mouseY - dragStartMouseY, maxScrollRows() * CELL, trackH);
        scrollRow = Math.round(px / (float) CELL);
        return true;
    }

    private boolean handleScroll(double mouseY, double amount) {
        if (mouseY < GRID_TOP || mouseY >= GRID_TOP + visibleRows * CELL) return false;
        int step = amount > 0 ? -ROWS_PER_NOTCH : amount < 0 ? ROWS_PER_NOTCH : 0;
        scrollRow = ScoreboardListLayout.clampScroll(scrollRow + step, maxScrollRows());
        return true;
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    private void returnToParent() {
        if (this.minecraft != null) ScreenCompat.setScreen(this.minecraft, parent);
    }
}
