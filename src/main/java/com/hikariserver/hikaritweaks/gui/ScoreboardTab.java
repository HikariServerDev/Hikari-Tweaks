package com.hikariserver.hikaritweaks.gui;

import com.hikariserver.hikaritweaks.compat.ScreenCompat;
import com.hikariserver.hikaritweaks.compat.DrawCtx;
import com.hikariserver.hikaritweaks.config.ClientConfig;
import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import com.hikariserver.hikaritweaks.scoreboard.PlayerListEntry;
import com.hikariserver.hikaritweaks.scoreboard.ScoreboardHudRenderer;
import com.hikariserver.hikaritweaks.scoreboard.ScoreboardPacketClient;
import com.hikariserver.hikaritweaks.scoreboard.ScoreboardView;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.ISliderCallback;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ScoreboardTab {
    private enum SubTab {
        PLAYERS("hikaritweaks.tab.players"), DISPLAY("hikaritweaks.tab.display");
        private final String langKey;
        SubTab(String langKey) { this.langKey = langKey; }
        String label() { return I18n.get(langKey); }
    }

    public interface WidgetHost {
        <T extends ButtonBase>  T addButton(T button, IButtonActionListener listener);
        <T extends WidgetBase>  T addWidget(T widget);
    }

    private static final int ROW_HEIGHT  = 20;
    private static final int LIST_X      = 10;
    private static final int BUTTON_W    = 70;
    private static final int SCROLL_SPEED = 4;
    private static final int SCROLLBAR_W = 8;
    private static final int SUBTAB_H    = 16;
    private static final int OFFSCREEN   = -2000;
    private static final int CATEGORY_H  = 14;
    private static final long REQUEST_TIMEOUT_MS = 3000L;

    private final Screen     parent;
    private final WidgetHost host;
    private SubTab activeSubTab = SubTab.PLAYERS;

    private int x, y, width, height;

    private List<PlayerListEntry> entries = new ArrayList<>();
    private final java.util.Set<String> selectedUuids = new java.util.LinkedHashSet<>();
    private int     scrollOffset  = 0;
    private boolean loaded        = false;
    private boolean waiting       = false;
    private long    waitingSince  = 0L;
    private boolean draggingScrollbar = false;
    private double  dragStartMouseY   = 0;
    private int     dragStartScroll   = 0;

    private HideableButton refreshBtn;
    private HideableButton selectAllVisibleBtn;
    private HideableButton selectAllBlockedBtn;
    private HideableButton bulkShowBtn;
    private HideableButton bulkHideBtn;
    private HideableButton clearSelectionBtn;
    private final List<RowButton> rowButtons = new ArrayList<>();
    private final List<RowButton> selectButtons = new ArrayList<>();
    private int activeRowCount = 0;

    private HideableSlider pageSizeSlider;
    private HideableButton prevPageBtn;
    private HideableButton nextPageBtn;
    private HideableButton resetPageBtn;
    private HideableButton posEditorBtn;
    private HideableButton colorPickerBtn;
    private HideableButton markBotsBtn;
    private HideableButton hideBotsBtn;
    private HideableSlider scaleSlider;

    public ScoreboardTab(Screen parent, WidgetHost host) {
        this.parent = parent;
        this.host   = host;
    }

    public void init(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;

        loaded = false;

        refreshBtn = host.addButton(
                new HideableButton(x + width - BUTTON_W - 4, y + SUBTAB_H + 4, BUTTON_W, 16,
                        I18n.get("hikaritweaks.button.refresh")),
                (btn, mb) -> requestList());

        int bulkY = y + SUBTAB_H + 4;
        selectAllVisibleBtn = host.addButton(
                new HideableButton(x, bulkY, 68, 16, "§a" + I18n.get("hikaritweaks.button.select_shown")),
                (btn, mb) -> {
                    entries.stream().filter(e -> !e.isBlocked()).forEach(e -> selectedUuids.add(e.uuid()));
                    rebuildRowButtons();
                });
        selectAllBlockedBtn = host.addButton(
                new HideableButton(x + 72, bulkY, 68, 16, "§c" + I18n.get("hikaritweaks.button.select_hidden")),
                (btn, mb) -> {
                    entries.stream().filter(PlayerListEntry::isBlocked).forEach(e -> selectedUuids.add(e.uuid()));
                    rebuildRowButtons();
                });
        bulkShowBtn = host.addButton(
                new HideableButton(x + 144, bulkY, 50, 16, "§a" + I18n.get("hikaritweaks.button.show_all")),
                (btn, mb) -> {
                    if (selectedUuids.isEmpty()) return;
                    for (String uuid : selectedUuids) ScoreboardPacketClient.toggleBlockIfNeeded(uuid, false);
                    selectedUuids.clear();
                    setWaiting(true);
                });
        bulkHideBtn = host.addButton(
                new HideableButton(x + 198, bulkY, 50, 16, "§c" + I18n.get("hikaritweaks.button.hide_all")),
                (btn, mb) -> {
                    if (selectedUuids.isEmpty()) return;
                    for (String uuid : selectedUuids) ScoreboardPacketClient.toggleBlockIfNeeded(uuid, true);
                    selectedUuids.clear();
                    setWaiting(true);
                });
        clearSelectionBtn = host.addButton(
                new HideableButton(x + 252, bulkY, 44, 16, "§7" + I18n.get("hikaritweaks.button.clear_selection")),
                (btn, mb) -> {
                    selectedUuids.clear();
                    rebuildRowButtons();
                });

        int lx = x + 8;

        pageSizeSlider = host.addWidget(
                new HideableSlider(lx + 100, dispRowY(0), 120, ROW_HEIGHT,
                        new PageSizeCallback()));

        prevPageBtn = host.addButton(new HideableButton(
                        lx,       dispRowY(1), 58, 16, I18n.get("hikaritweaks.button.prev_page")),
                (btn, mb) -> ScoreboardHudRenderer.prevPage());

        nextPageBtn = host.addButton(new HideableButton(
                        lx + 62,  dispRowY(1), 58, 16, I18n.get("hikaritweaks.button.next_page")),
                (btn, mb) -> ScoreboardHudRenderer.nextPage());

        resetPageBtn = host.addButton(new HideableButton(
                        lx + 124, dispRowY(1), 48, 16, I18n.get("hikaritweaks.button.first_page")),
                (btn, mb) -> ScoreboardHudRenderer.resetPage());

        posEditorBtn = host.addButton(new HideableButton(
                        lx,       dispRowY(2), 126, 18, I18n.get("hikaritweaks.button.open_position_editor")),
                (btn, mb) -> ScreenCompat.setScreen(Minecraft.getInstance(), new PositionEditorScreen(parent)));

        colorPickerBtn = host.addButton(new HideableButton(
                        lx + 130, dispRowY(2), 110, 18, I18n.get("hikaritweaks.button.open_color_editor")),
                (btn, mb) -> ScreenCompat.setScreen(Minecraft.getInstance(), new ColorPickerScreen(parent)));

        scaleSlider = host.addWidget(
                new HideableSlider(lx + 60, dispRowY(3), 160, ROW_HEIGHT,
                        new ScaleCallback()));

        markBotsBtn = host.addButton(new HideableButton(
                        lx,       dispRowY(4), 126, 18, ""),
                (btn, mb) -> {
                    ClientConfigManager.config.scoreboardMarkBots =
                            !ClientConfigManager.config.scoreboardMarkBots;
                    ClientConfigManager.saveDeferred();
                });

        hideBotsBtn = host.addButton(new HideableButton(
                        lx + 130, dispRowY(4), 110, 18, ""),
                (btn, mb) -> {
                    ClientConfigManager.config.scoreboardHideBots =
                            !ClientConfigManager.config.scoreboardHideBots;
                    ClientConfigManager.saveDeferred();
                    ScoreboardHudRenderer.resetPage();
                });

        ScoreboardPacketClient.setOnListUpdated(list -> {
            this.entries = new ArrayList<>(list);
            this.waiting = false;
            rebuildRowButtons();
            updateSubTabVisibility();
        });
        ScoreboardPacketClient.setOnRankingUpdated(() -> {
        });

        if (!loaded) {
            loaded = true;
            List<PlayerListEntry> cached = ScoreboardPacketClient.getCachedList();
            if (!cached.isEmpty()) {
                entries = new ArrayList<>(cached);
                rebuildRowButtons();
            } else {
                requestList();
            }
        }

        updateSubTabVisibility();
    }

    public void onClose() {
        ScoreboardPacketClient.setOnListUpdated(null);
        ScoreboardPacketClient.setOnRankingUpdated(null);
        selectedUuids.clear();
        loaded = false;
        ClientConfigManager.flushPendingSave();
    }

    private void switchSubTab(SubTab tab) {
        activeSubTab = tab;
        updateSubTabVisibility();
    }

    private void updateSubTabVisibility() {
        boolean players = activeSubTab == SubTab.PLAYERS;

        setShown(refreshBtn, players);
        setShown(selectAllVisibleBtn, players);
        setShown(selectAllBlockedBtn, players);
        setShown(bulkShowBtn, players);
        setShown(bulkHideBtn, players);
        setShown(clearSelectionBtn, players);
        applyRowVisibility();

        boolean display = !players;
        if (pageSizeSlider != null) pageSizeSlider.setShown(display);
        if (scaleSlider    != null) scaleSlider.setShown(display);
        setShown(prevPageBtn,    display);
        setShown(nextPageBtn,    display);
        setShown(resetPageBtn,   display);
        setShown(posEditorBtn,   display);
        setShown(colorPickerBtn, display);
        setShown(markBotsBtn,    display);
        setShown(hideBotsBtn,    display);
    }

    private static void setShown(HideableButton btn, boolean shown) {
        if (btn != null) btn.setShown(shown);
    }

    private void applyRowVisibility() {
        boolean show = activeSubTab == SubTab.PLAYERS && !waiting;
        for (int i = 0; i < rowButtons.size(); i++) {
            boolean shown = show && i < activeRowCount;
            rowButtons.get(i).setShown(shown);
            selectButtons.get(i).setShown(shown);
        }
    }

    private void setWaiting(boolean value) {
        waiting = value;
        if (value) waitingSince = System.currentTimeMillis();
        applyRowVisibility();
    }

    private void rebuildRowButtons() {
        List<PlayerListEntry> visible  = entries.stream().filter(e -> !e.isBlocked()).collect(Collectors.toList());
        List<PlayerListEntry> blocked  = entries.stream().filter(PlayerListEntry::isBlocked).collect(Collectors.toList());
        List<PlayerListEntry> ordered  = new ArrayList<>(visible.size() + blocked.size());
        ordered.addAll(visible);
        ordered.addAll(blocked);

        ensureRowCapacity(ordered.size());

        for (int i = 0; i < ordered.size(); i++) {
            PlayerListEntry entry = ordered.get(i);
            RowButton btn = rowButtons.get(i);
            btn.bind(entry.uuid());
            btn.setDisplayString(entry.isBlocked()
                    ? "§c" + I18n.get("hikaritweaks.status.hidden")
                    : "§a" + I18n.get("hikaritweaks.status.shown"));

            RowButton selBtn = selectButtons.get(i);
            selBtn.bind(entry.uuid());
            selBtn.setDisplayString(selectedUuids.contains(entry.uuid()) ? "§e☑" : "§7☐");
        }
        for (int i = ordered.size(); i < rowButtons.size(); i++) {
            rowButtons.get(i).bind(null);
            selectButtons.get(i).bind(null);
        }
        activeRowCount = ordered.size();

        applyRowVisibility();
    }

    private void ensureRowCapacity(int required) {
        while (rowButtons.size() < required) {
            RowButton btn = new RowButton(LIST_X + 26, OFFSCREEN, BUTTON_W, ROW_HEIGHT - 2, "");
            host.addButton(btn, (b, mb) -> {
                String uuid = btn.uuid();
                if (uuid == null) return;
                ScoreboardPacketClient.toggleBlock(uuid);
                setWaiting(true);
            });
            rowButtons.add(btn);

            RowButton selBtn = new RowButton(LIST_X, OFFSCREEN, 22, ROW_HEIGHT - 2, "");
            host.addButton(selBtn, (b, mb) -> {
                String uuid = selBtn.uuid();
                if (uuid == null) return;
                if (selectedUuids.contains(uuid)) {
                    selectedUuids.remove(uuid);
                    selBtn.setDisplayString("§7☐");
                } else {
                    selectedUuids.add(uuid);
                    selBtn.setDisplayString("§e☑");
                }
            });
            selectButtons.add(selBtn);
        }
    }

    public void render(DrawCtx ctx, int mouseX, int mouseY, float delta) {
        Font tr = Minecraft.getInstance().font;

        if (waiting && System.currentTimeMillis() - waitingSince >= REQUEST_TIMEOUT_MS) {
            setWaiting(false);
        }

        renderSubTabButton(ctx, tr, x,      y, 90, SUBTAB_H, SubTab.PLAYERS, mouseX, mouseY);
        renderSubTabButton(ctx, tr, x + 94, y, 90, SUBTAB_H, SubTab.DISPLAY, mouseX, mouseY);
        ctx.fill(x, y + SUBTAB_H + 2, x + width, y + SUBTAB_H + 3, 0x66FFFFFF);

        if (activeSubTab == SubTab.PLAYERS) {
            renderPlayersContent(ctx, tr, mouseX, mouseY);
        } else {
            renderDisplayContent(ctx, tr);
        }
    }

    private void renderSubTabButton(DrawCtx ctx, Font tr,
                                    int bx, int by, int bw, int bh, SubTab tab, int mouseX, int mouseY) {
        boolean active  = (activeSubTab == tab);
        boolean hovered = !active && mouseX >= bx && mouseX < bx + bw
                && mouseY >= by && mouseY < by + bh;
        int bg = active ? 0xFF555555 : hovered ? 0xFF3A3A5A : 0xFF222244;
        ctx.fill(bx, by, bx + bw, by + bh, bg);
        ctx.fill(bx, by, bx + bw, by + 1, active ? 0xFFFFFFFF : 0x88FFFFFF);
        int textColor = active ? 0xFFFFFF55 : 0xFFCCCCCC;
        String label = tab.label();
        float tx = bx + (bw - tr.width(label)) / 2f;
        float ty = by + (bh - 8) / 2f;
        ctx.drawTextWithShadow(tr, label, tx, ty, textColor);
    }

    private void renderPlayersContent(DrawCtx ctx, Font tr, int mouseX, int mouseY) {
        int headerY = y + SUBTAB_H + 4;
        ctx.drawTextWithShadow(tr, "§e" + I18n.get("hikaritweaks.scoreboard_tab.name_header"), LIST_X + BUTTON_W + 30,   headerY + 22, 0xFFFFFF);
        ctx.drawTextWithShadow(tr, "§7" + I18n.get("hikaritweaks.scoreboard_tab.type_header"), LIST_X + BUTTON_W + 156, headerY + 22, 0xFFFFFF);
        if (!selectedUuids.isEmpty()) {
            ctx.drawTextWithShadow(tr, "§e" + I18n.get("hikaritweaks.scoreboard_tab.selected_count", selectedUuids.size()),
                    x + width - BUTTON_W - 60, headerY + 4, 0xFFFFAA);
        }

        if (waiting) {
            drawCentered(ctx, tr, "§7" + I18n.get("hikaritweaks.scoreboard_tab.loading"), x + width / 2, y + height / 2, 0xAAAAAA);
            return;
        }
        if (entries.isEmpty()) {
            drawCentered(ctx, tr, "§7" + I18n.get("hikaritweaks.scoreboard_tab.no_data"), x + width / 2, y + height / 2, 0xAAAAAA);
            return;
        }

        List<PlayerListEntry> visibleEntries = entries.stream().filter(e -> !e.isBlocked()).collect(Collectors.toList());
        List<PlayerListEntry> blockedEntries = entries.stream().filter(PlayerListEntry::isBlocked).collect(Collectors.toList());

        int totalVirtualH = ScoreboardListLayout.totalVirtualHeight(
                visibleEntries.size(), blockedEntries.size(), CATEGORY_H, ROW_HEIGHT);

        int listTop    = listTop();
        int listBottom = listBottom();
        int visibleH   = listBottom - listTop;
        int maxScroll  = ScoreboardListLayout.maxScroll(totalVirtualH, visibleH);
        scrollOffset   = ScoreboardListLayout.clampScroll(scrollOffset, maxScroll);

        int listWidth = width - SCROLLBAR_W - 4;
        ctx.enableScissor(x, listTop, x + listWidth, listBottom);

        int drawY = listTop - scrollOffset;
        int rowBtnIndex = 0;

        if (!visibleEntries.isEmpty()) {
            int catY = drawY;
            if (catY + CATEGORY_H >= listTop && catY <= listBottom) {
                ctx.fill(x, catY, x + width, catY + CATEGORY_H, 0x44AAFFAA);
                String head = "§a" + I18n.get("hikaritweaks.scoreboard_tab.shown_group", visibleEntries.size());
                ctx.drawTextWithShadow(tr, head, LIST_X + 2, catY + 3, 0xAAFFAA);
                drawKindBreakdown(ctx, tr, visibleEntries, LIST_X + 6 + tr.width(head), catY + 3);
            }
            drawY += CATEGORY_H;

            for (int i = 0; i < visibleEntries.size(); i++) {
                PlayerListEntry entry = visibleEntries.get(i);
                int rowY = drawY + i * ROW_HEIGHT;
                boolean inView = rowY + ROW_HEIGHT >= listTop && rowY <= listBottom;

                if (rowBtnIndex < activeRowCount) {
                    HideableButton btn = rowButtons.get(rowBtnIndex);
                    HideableButton selBtn = selectButtons.size() > rowBtnIndex ? selectButtons.get(rowBtnIndex) : null;
                    if (inView) {
                        btn.setY(rowY + 1);
                        btn.setDisplayString("§a" + I18n.get("hikaritweaks.status.shown"));
                        if (selBtn != null) {
                            selBtn.setY(rowY + 1);
                            selBtn.setDisplayString(selectedUuids.contains(entry.uuid()) ? "§e☑" : "§7☐");
                        }
                    } else {
                        btn.setY(OFFSCREEN);
                        if (selBtn != null) selBtn.setY(OFFSCREEN);
                    }
                }
                rowBtnIndex++;

                if (!inView) continue;
                if (i % 2 == 0) ctx.fill(x, rowY, x + width, rowY + ROW_HEIGHT, 0x18FFFFFF);
                String name = truncate(entry.displayName(), 20);
                ctx.drawTextWithShadow(tr, name, LIST_X + BUTTON_W + 30, rowY + 6,
                        entry.isBot() ? 0xFFAA00 : 0xFFFFFF);
                ctx.drawTextWithShadow(tr, kindLabel(entry), LIST_X + BUTTON_W + 156, rowY + 6, 0xFFFFFF);
            }
            drawY += visibleEntries.size() * ROW_HEIGHT;
        }

        if (!blockedEntries.isEmpty()) {
            int catY = drawY;
            if (catY + CATEGORY_H >= listTop && catY <= listBottom) {
                ctx.fill(x, catY, x + width, catY + CATEGORY_H, 0x44FF8888);
                String head = "§c" + I18n.get("hikaritweaks.scoreboard_tab.hidden_group", blockedEntries.size());
                ctx.drawTextWithShadow(tr, head, LIST_X + 2, catY + 3, 0xFFAAAA);
                drawKindBreakdown(ctx, tr, blockedEntries, LIST_X + 6 + tr.width(head), catY + 3);
            }
            drawY += CATEGORY_H;

            for (int i = 0; i < blockedEntries.size(); i++) {
                PlayerListEntry entry = blockedEntries.get(i);
                int rowY = drawY + i * ROW_HEIGHT;
                boolean inView = rowY + ROW_HEIGHT >= listTop && rowY <= listBottom;

                if (rowBtnIndex < activeRowCount) {
                    HideableButton btn = rowButtons.get(rowBtnIndex);
                    HideableButton selBtn = selectButtons.size() > rowBtnIndex ? selectButtons.get(rowBtnIndex) : null;
                    if (inView) {
                        btn.setY(rowY + 1);
                        btn.setDisplayString("§c" + I18n.get("hikaritweaks.status.hidden"));
                        if (selBtn != null) {
                            selBtn.setY(rowY + 1);
                            selBtn.setDisplayString(selectedUuids.contains(entry.uuid()) ? "§e☑" : "§7☐");
                        }
                    } else {
                        btn.setY(OFFSCREEN);
                        if (selBtn != null) selBtn.setY(OFFSCREEN);
                    }
                }
                rowBtnIndex++;

                if (!inView) continue;
                if (i % 2 == 0) ctx.fill(x, rowY, x + width, rowY + ROW_HEIGHT, 0x18FFFFFF);
                String name = truncate(entry.displayName(), 20);
                ctx.drawTextWithShadow(tr, name, LIST_X + BUTTON_W + 30, rowY + 6, 0x666666);
                ctx.drawTextWithShadow(tr, kindLabel(entry), LIST_X + BUTTON_W + 156, rowY + 6, 0xFFFFFF);
            }
        }

        while (rowBtnIndex < activeRowCount) {
            rowButtons.get(rowBtnIndex).setY(OFFSCREEN);
            if (selectButtons.size() > rowBtnIndex) selectButtons.get(rowBtnIndex).setY(OFFSCREEN);
            rowBtnIndex++;
        }

        ctx.disableScissor();

        if (maxScroll > 0) {
            int barH = ScoreboardListLayout.scrollbarHeight(visibleH, totalVirtualH);
            int barY = ScoreboardListLayout.scrollbarY(listTop, listBottom, barH, visibleH, scrollOffset, maxScroll);
            int barX = x + width - SCROLLBAR_W - 2;
            boolean hov = mouseX >= barX && mouseX <= barX + SCROLLBAR_W
                    && mouseY >= barY && mouseY <= barY + barH;
            ctx.fill(barX, listTop,  barX + SCROLLBAR_W, listBottom, 0x55000000);
            ctx.fill(barX, barY, barX + SCROLLBAR_W, barY + barH,
                    draggingScrollbar ? 0xFFFFFFFF : hov ? 0xCCFFFFFF : 0x99AAAAAA);
        }
    }

    private void renderDisplayContent(DrawCtx ctx, Font tr) {
        ClientConfig cfg = ClientConfigManager.config;
        int lx = x + 8;

        ctx.drawTextWithShadow(tr, I18n.get("hikaritweaks.scoreboard_tab.page_size_label"), lx, dispRowY(0) + 4, 0xFFFFFF);

        int infoY = dispRowY(0) + ROW_HEIGHT + 4;
        ScoreboardView.Data data = ScoreboardView.current();
        int total = data != null ? data.size() : 0;
        int page  = ScoreboardHudRenderer.getCurrentPage();
        int maxP  = ScoreboardHudRenderer.getMaxPage(total, cfg.scoreboardPageSize);
        ctx.drawTextWithShadow(tr,
                I18n.get("hikaritweaks.scoreboard_tab.ranking_summary", total, page + 1, maxP + 1),
                lx, infoY, 0xAAAAAA);

        int divY = dispRowY(1) + 22;
        ctx.fill(x, divY, x + width, divY + 1, 0x44FFFFFF);

        ctx.drawTextWithShadow(tr, I18n.get("hikaritweaks.scoreboard_tab.scale_label"), lx, dispRowY(3) + 4, 0xFFFFFF);
        ctx.drawTextWithShadow(tr,
                String.format("X:%d%%  Y:%d%%",
                        cfg.scoreboardPositionX, cfg.scoreboardPositionY),
                lx, dispRowY(3) + 26, 0xAAAAAA);

        if (markBotsBtn != null) {
            markBotsBtn.setDisplayString(I18n.get("hikaritweaks.scoreboard_tab.mark_bots",
                    onOff(cfg.scoreboardMarkBots)));
        }
        if (hideBotsBtn != null) {
            hideBotsBtn.setDisplayString(I18n.get("hikaritweaks.scoreboard_tab.hide_bots",
                    onOff(cfg.scoreboardHideBots)));
        }
    }

    private static String kindLabel(PlayerListEntry entry) {
        return entry.isBot()
                ? "§6" + I18n.get("hikaritweaks.scoreboard_tab.bot")
                : "§7" + I18n.get("hikaritweaks.scoreboard_tab.player");
    }

    private void drawKindBreakdown(DrawCtx ctx, Font tr,
                                   List<PlayerListEntry> group, int tx, int ty) {
        int bots = 0;
        for (PlayerListEntry entry : group) {
            if (entry.isBot()) bots++;
        }
        if (bots == 0) return;
        ctx.drawTextWithShadow(tr,
                "§7" + I18n.get("hikaritweaks.scoreboard_tab.kind_breakdown",
                        group.size() - bots, bots),
                tx, ty, 0xAAAAAA);
    }

    private static String onOff(boolean value) {
        return value ? "§aON" : "§7OFF";
    }

    private int listTop() {
        return y + SUBTAB_H + ROW_HEIGHT + 20 + 4;
    }

    private int listBottom() {
        return y + height - 4;
    }

    private int dispRowY(int index) {
        int base = y + SUBTAB_H + 8;
        return switch (index) {
            case 0 -> base;
            case 1 -> base + ROW_HEIGHT + 4 + 12;
            case 2 -> base + ROW_HEIGHT + 4 + 12 + 22 + 1 + 6;
            case 3 -> base + ROW_HEIGHT + 4 + 12 + 22 + 1 + 6 + 24;
            case 4 -> base + ROW_HEIGHT + 4 + 12 + 22 + 1 + 6 + 24 + 40;
            default -> base;
        };
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (my >= y && my < y + SUBTAB_H) {
                if (mx >= x && mx < x + 90 && activeSubTab != SubTab.PLAYERS) {
                    switchSubTab(SubTab.PLAYERS);
                    return true;
                }
                if (mx >= x + 94 && mx < x + 94 + 90 && activeSubTab != SubTab.DISPLAY) {
                    switchSubTab(SubTab.DISPLAY);
                    return true;
                }
            }
        }
        if (button != 0 || activeSubTab != SubTab.PLAYERS) return false;

        int totalVirtualH = currentTotalVirtualHeight();

        int listTop    = listTop();
        int listBottom = listBottom();
        int visibleH   = listBottom - listTop;
        int maxScroll  = ScoreboardListLayout.maxScroll(totalVirtualH, visibleH);
        if (maxScroll > 0) {
            int barX = x + width - SCROLLBAR_W - 2;
            if (mx >= barX && mx <= barX + SCROLLBAR_W && my >= listTop && my <= listBottom) {
                draggingScrollbar = true;
                dragStartMouseY   = my;
                dragStartScroll   = scrollOffset;
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingScrollbar) { draggingScrollbar = false; return true; }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!draggingScrollbar) return false;
        int totalVirtualH = currentTotalVirtualHeight();

        int visibleH  = listBottom() - listTop();
        int maxScroll = ScoreboardListLayout.maxScroll(totalVirtualH, visibleH);
        if (maxScroll > 0) {
            int barH   = ScoreboardListLayout.scrollbarHeight(visibleH, totalVirtualH);
            int trackH = ScoreboardListLayout.trackHeight(visibleH, barH);
            scrollOffset = ScoreboardListLayout.scrollFromDrag(
                    dragStartScroll, my - dragStartMouseY, maxScroll, trackH);
        }
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (activeSubTab != SubTab.PLAYERS) return false;
        int listTop    = listTop();
        int listBottom = listBottom();
        if (my < listTop || my > listBottom) return false;
        int totalVirtualH = currentTotalVirtualHeight();
        int maxScroll = ScoreboardListLayout.maxScroll(totalVirtualH, listBottom - listTop);
        scrollOffset  = ScoreboardListLayout.scrollFromWheel(scrollOffset, amount, SCROLL_SPEED, maxScroll);
        return true;
    }

    private int currentTotalVirtualHeight() {
        int visibleCount = (int) entries.stream().filter(e -> !e.isBlocked()).count();
        int blockedCount = entries.size() - visibleCount;
        return ScoreboardListLayout.totalVirtualHeight(visibleCount, blockedCount, CATEGORY_H, ROW_HEIGHT);
    }

    private void requestList() {
        setWaiting(ScoreboardPacketClient.requestPlayerList());
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private static void drawCentered(DrawCtx ctx, Font tr, String text, int cx, int cy, int color) {
        ctx.drawTextWithShadow(tr, text, cx - tr.width(text) / 2f, cy, color);
    }

    private static class ScaleCallback implements ISliderCallback {
        private static ClientConfig cfg() { return ClientConfigManager.config; }

        @Override public int getMaxSteps() { return 50; }

        @Override
        public double getValueRelative() {
            return (cfg().scoreboardScale - 0.5f) / 2.5f;
        }

        @Override
        public void setValueRelative(double rel) {
            float v = (float)(Math.round(rel * 50) * 0.05 + 0.5);
            v = Math.max(0.5f, Math.min(3.0f, v));
            ClientConfig cfg = cfg();
            if (Math.abs(cfg.scoreboardScale - v) > 0.001f) {
                cfg.scoreboardScale = v;
                ClientConfigManager.saveDeferred();
            }
        }

        @Override
        public String getFormattedDisplayValue() {
            return String.format("%.2fx", cfg().scoreboardScale);
        }
    }

    public static class HideableButton extends ButtonGeneric {
        private int shownY = OFFSCREEN;

        public HideableButton(int x, int y, int width, int height, String text) {
            super(x, OFFSCREEN, width, height, text);
            this.shownY = y;
        }

        public void setShown(boolean shown) {
            this.y       = shown ? shownY : OFFSCREEN;
            this.visible = shown;
            this.enabled = shown;
        }

        public void setShownY(int y) {
            this.shownY = y;
            if (this.visible) this.y = y;
        }
    }

    private static class RowButton extends HideableButton {
        private String uuid;

        RowButton(int x, int y, int width, int height, String text) {
            super(x, y, width, height, text);
        }

        void bind(String uuid) { this.uuid = uuid; }

        String uuid() { return this.uuid; }
    }

    public static class HideableSlider extends WidgetSlider {
        private int shownY;

        public HideableSlider(int x, int y, int width, int height, ISliderCallback callback) {
            super(x, OFFSCREEN, width, height, callback);
            this.shownY = y;
        }

        public void setShown(boolean shown) {
            this.y = shown ? shownY : OFFSCREEN;
        }
    }

    private static class PageSizeCallback implements ISliderCallback {
        private static ClientConfig cfg() { return ClientConfigManager.config; }

        @Override public int getMaxSteps() { return 49; }

        @Override
        public double getValueRelative() {
            return (cfg().scoreboardPageSize - 1) / 49.0;
        }

        @Override
        public void setValueRelative(double rel) {
            int v = Math.max(1, Math.min(50, (int) Math.round(rel * 49) + 1));
            ClientConfig cfg = cfg();
            if (cfg.scoreboardPageSize != v) {
                cfg.scoreboardPageSize = v;
                ClientConfigManager.saveDeferred();
                ScoreboardHudRenderer.resetPage();
            }
        }

        @Override
        public String getFormattedDisplayValue() {
            return I18n.get("hikaritweaks.scoreboard_tab.page_size_value", cfg().scoreboardPageSize);
        }
    }
}
