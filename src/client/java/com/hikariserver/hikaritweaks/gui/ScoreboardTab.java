package com.hikariserver.hikaritweaks.gui;

import com.hikariserver.hikaritweaks.config.ClientConfig;
import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import com.hikariserver.hikaritweaks.scoreboard.PlayerListEntry;
import com.hikariserver.hikaritweaks.scoreboard.ScoreboardHudRenderer;
import com.hikariserver.hikaritweaks.scoreboard.ScoreboardPacketClient;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.ISliderCallback;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import fi.dy.masa.malilib.gui.widgets.WidgetSlider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// Hikari-Tweaks 設定画面の「スコアボード」タブ。
public final class ScoreboardTab {

    // サブタブの列挙型（プレイヤー一覧 / 表示設定）
    private enum SubTab {
        PLAYERS("プレイヤー"), DISPLAY("表示設定");
        final String label;
        SubTab(String l) { label = l; }
    }

    // WidgetHost インターフェース：設定画面がボタン・ウィジェットを受け取るための窓口
    public interface WidgetHost {
        <T extends ButtonBase>  T addButton(T button, IButtonActionListener listener);
        <T extends WidgetBase>  T addWidget(T widget);
    }

    // ───── 定数 ─────
    // 各行の高さ
    private static final int ROW_HEIGHT  = 20;
    // リストの左端 X 座標
    private static final int LIST_X      = 10;
    // 行ボタンの横幅
    private static final int BUTTON_W    = 70;
    // マウスホイール 1 ノッチのスクロール量（px）
    private static final int SCROLL_SPEED = 4;
    // スクロールバーの幅
    private static final int SCROLLBAR_W = 8;
    // サブタブバーの高さ
    private static final int SUBTAB_H    = 16;
    // 非表示にするときの退避 Y 座標
    private static final int OFFSCREEN   = -2000;
    // カテゴリヘッダーの高さ
    private static final int CATEGORY_H  = 14;

    // ───── 状態 ─────
    // 親画面への参照
    private final Screen     parent;
    // ボタン・ウィジェットを登録するホスト
    private final WidgetHost host;
    // 現在選択中のサブタブ
    private SubTab activeSubTab = SubTab.PLAYERS;

    // このタブが占める領域
    private int x, y, width, height;

    // PLAYERS サブタブ用フィールド
    private List<PlayerListEntry> entries = new ArrayList<>();
    // 一括操作用に選択状態を管理するセット（UUID文字列）
    private final java.util.Set<String> selectedUuids = new java.util.LinkedHashSet<>();
    // スクロール位置（ピクセル）
    private int     scrollOffset  = 0;
    // リストが一度でも読み込まれたかどうかのフラグ
    private boolean loaded        = false;
    // サーバーへリクエスト中かどうかのフラグ
    private boolean waiting       = false;
    // スクロールバーをドラッグ中かどうか
    private boolean draggingScrollbar = false;
    // ドラッグ開始時のマウス Y 座標
    private double  dragStartMouseY   = 0;
    // ドラッグ開始時のスクロール位置
    private int     dragStartScroll   = 0;

    // ───── malilib ボタン／ウィジェット ─────
    // プレイヤータブ用ボタン群
    private HideableButton refreshBtn;
    private HideableButton selectAllVisibleBtn;
    private HideableButton selectAllBlockedBtn;
    private HideableButton bulkShowBtn;
    private HideableButton bulkHideBtn;
    private HideableButton clearSelectionBtn;
    // 各行の表示切り替えボタン（rowButtons と同じ順序）
    private final List<HideableButton> rowButtons = new ArrayList<>();
    // チェックボックス代わりの選択トグルボタン（rowButtons と同じ順序）
    private final List<HideableButton> selectButtons = new ArrayList<>();

    // 表示設定タブ専用ウィジェット
    private HideableSlider pageSizeSlider;
    private HideableButton prevPageBtn;
    private HideableButton nextPageBtn;
    private HideableButton resetPageBtn;
    private HideableButton posEditorBtn;
    private HideableButton colorPickerBtn;
    private HideableSlider scaleSlider;

    // ───── コンストラクタ ─────

    // 親画面とウィジェットホストを受け取ってタブを構築する
    public ScoreboardTab(Screen parent, WidgetHost host) {
        this.parent = parent;
        this.host   = host;
    }

    // ───── 初期化 ─────

    // 占有領域を設定しボタン・ウィジェットを生成する
    public void init(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;

        loaded = false;

        // ── プレイヤータブ ──
        // リストを再取得するリフレッシュボタンを右上に配置する
        refreshBtn = host.addButton(
                new HideableButton(x + width - BUTTON_W - 4, y + SUBTAB_H + 4, BUTTON_W, 16, "Refresh"),
                (btn, mb) -> requestList());

        // 一括操作ボタン群を横並びで配置する
        int bulkY = y + SUBTAB_H + 4;
        // 表示中エントリを全選択するボタン
        selectAllVisibleBtn = host.addButton(
                new HideableButton(x, bulkY, 68, 16, "§a表示を全選択"),
                (btn, mb) -> {
                    entries.stream().filter(e -> !e.isBlocked()).forEach(e -> selectedUuids.add(e.uuid()));
                    rebuildRowButtons();
                });
        // 非表示エントリを全選択するボタン
        selectAllBlockedBtn = host.addButton(
                new HideableButton(x + 72, bulkY, 68, 16, "§c非表示を全選択"),
                (btn, mb) -> {
                    entries.stream().filter(PlayerListEntry::isBlocked).forEach(e -> selectedUuids.add(e.uuid()));
                    rebuildRowButtons();
                });
        // 選択エントリを一括表示するボタン
        bulkShowBtn = host.addButton(
                new HideableButton(x + 144, bulkY, 50, 16, "§a一括表示"),
                (btn, mb) -> {
                    if (selectedUuids.isEmpty()) return;
                    for (String uuid : selectedUuids) ScoreboardPacketClient.toggleBlockIfNeeded(uuid, false);
                    selectedUuids.clear();
                    waiting = true;
                });
        // 選択エントリを一括非表示にするボタン
        bulkHideBtn = host.addButton(
                new HideableButton(x + 198, bulkY, 50, 16, "§c一括非表示"),
                (btn, mb) -> {
                    if (selectedUuids.isEmpty()) return;
                    for (String uuid : selectedUuids) ScoreboardPacketClient.toggleBlockIfNeeded(uuid, true);
                    selectedUuids.clear();
                    waiting = true;
                });
        // 選択をすべて解除するボタン
        clearSelectionBtn = host.addButton(
                new HideableButton(x + 252, bulkY, 44, 16, "§7選択解除"),
                (btn, mb) -> {
                    selectedUuids.clear();
                    rebuildRowButtons();
                });

        // ── 表示設定タブ ──
        ClientConfig cfg = ClientConfigManager.config;
        int lx = x + 8;

        // ページサイズスライダー（1–50）
        pageSizeSlider = host.addWidget(
                new HideableSlider(lx + 100, dispRowY(0), 120, ROW_HEIGHT,
                        new PageSizeCallback(cfg)));

        // 前ページ・次ページ・先頭へのボタン
        prevPageBtn = host.addButton(new HideableButton(
                        lx,       dispRowY(1), 58, 16, "◀ 前ページ"),
                (btn, mb) -> ScoreboardHudRenderer.prevPage());

        nextPageBtn = host.addButton(new HideableButton(
                        lx + 62,  dispRowY(1), 58, 16, "次ページ ▶"),
                (btn, mb) -> ScoreboardHudRenderer.nextPage());

        resetPageBtn = host.addButton(new HideableButton(
                        lx + 124, dispRowY(1), 48, 16, "先頭へ"),
                (btn, mb) -> ScoreboardHudRenderer.resetPage());

        // 位置調整画面を開くボタン
        posEditorBtn = host.addButton(new HideableButton(
                        lx,       dispRowY(2), 126, 18, "位置調整画面を開く"),
                (btn, mb) -> MinecraftClient.getInstance().setScreen(new PositionEditorScreen(parent)));

        // 色設定画面を開くボタン
        colorPickerBtn = host.addButton(new HideableButton(
                        lx + 130, dispRowY(2), 110, 18, "色設定画面を開く"),
                (btn, mb) -> MinecraftClient.getInstance().setScreen(new ColorPickerScreen(parent)));

        // スケールスライダー（0.5–3.0）
        scaleSlider = host.addWidget(
                new HideableSlider(lx + 60, dispRowY(3), 160, ROW_HEIGHT,
                        new ScaleCallback(cfg)));

        // ── サーバーコールバック ──
        // プレイヤーリスト更新時にエントリを差し替えてボタンを再構築する
        ScoreboardPacketClient.setOnListUpdated(list -> {
            // スクロール位置を保持するためリセットしない
            this.entries = new ArrayList<>(list);
            this.waiting = false;
            rebuildRowButtons();
            updateSubTabVisibility();
        });
        // ランキング更新時はページをリセットしない（現在ページを維持）
        ScoreboardPacketClient.setOnRankingUpdated(() -> {
            // ページの clamp は ScoreboardHudRenderer.render() 内で行うため何もしない
        });

        // 初回表示時にキャッシュ済みリストがあれば使い、なければサーバーへリクエストする
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

        // サブタブの表示状態を初期化する
        updateSubTabVisibility();
    }

    // タブが閉じられるときにコールバックを解除して状態をリセットする
    public void onClose() {
        ScoreboardPacketClient.setOnListUpdated(null);
        ScoreboardPacketClient.setOnRankingUpdated(null);
        selectedUuids.clear();
        loaded = false;
    }

    // ───── サブタブ切り替え ─────

    // 指定サブタブに切り替えて表示状態を更新する
    private void switchSubTab(SubTab tab) {
        activeSubTab = tab;
        updateSubTabVisibility();
    }

    // アクティブなサブタブに応じて各ボタン・ウィジェットの表示を切り替える
    private void updateSubTabVisibility() {
        boolean players = activeSubTab == SubTab.PLAYERS;

        // プレイヤータブ専用ボタンの表示切り替え
        setShown(refreshBtn, players);
        setShown(selectAllVisibleBtn, players);
        setShown(selectAllBlockedBtn, players);
        setShown(bulkShowBtn, players);
        setShown(bulkHideBtn, players);
        setShown(clearSelectionBtn, players);
        for (HideableButton b : rowButtons)    setShown(b, players);
        for (HideableButton b : selectButtons) setShown(b, players);

        // 表示設定タブ専用ウィジェットの表示切り替え
        boolean display = !players;
        if (pageSizeSlider != null) pageSizeSlider.setShown(display);
        if (scaleSlider    != null) scaleSlider.setShown(display);
        setShown(prevPageBtn,    display);
        setShown(nextPageBtn,    display);
        setShown(resetPageBtn,   display);
        setShown(posEditorBtn,   display);
        setShown(colorPickerBtn, display);
    }

    // null チェック付きで HideableButton の表示を設定するヘルパー
    private static void setShown(HideableButton btn, boolean shown) {
        if (btn != null) btn.setShown(shown);
    }

    // ───── 行ボタン再構築 ─────

    // 現在のエントリリストに基づいて行ボタンを再生成する
    private void rebuildRowButtons() {
        // 既存ボタンを退避して一覧をクリアする
        for (HideableButton b : rowButtons)    b.setShown(false);
        for (HideableButton b : selectButtons) b.setShown(false);
        rowButtons.clear();
        selectButtons.clear();

        // プレイヤータブ以外は行ボタンが不要
        if (activeSubTab != SubTab.PLAYERS) return;

        // 表示中と非表示でカテゴリ分け
        List<PlayerListEntry> visible  = entries.stream().filter(e -> !e.isBlocked()).collect(Collectors.toList());
        List<PlayerListEntry> blocked  = entries.stream().filter(PlayerListEntry::isBlocked).collect(Collectors.toList());

        // 表示中カテゴリ
        // カテゴリヘッダーはボタンではなく render() 側で描画するので、
        // rowButtons にはエントリ数だけ登録し、描画時にオフセット計算する
        addEntryButtons(visible);
        addEntryButtons(blocked);
    }

    // エントリリストの各行に表示切り替えボタンと選択ボタンを追加する
    private void addEntryButtons(List<PlayerListEntry> list) {
        for (PlayerListEntry entry : list) {
            // 行表示/非表示トグルボタン（初期位置は OFFSCREEN で後から配置）
            HideableButton btn = host.addButton(
                    new HideableButton(LIST_X + 26, OFFSCREEN, BUTTON_W, ROW_HEIGHT - 2,
                            entry.isBlocked() ? "§c非表示" : "§a表示中"),
                    (b, mb) -> {
                        ScoreboardPacketClient.toggleBlock(entry.uuid());
                        waiting = true;
                    });
            btn.setShown(true);
            rowButtons.add(btn);

            // 選択チェックボックス相当（選択状態に応じてラベルを変える）
            boolean sel = selectedUuids.contains(entry.uuid());
            HideableButton selBtn = host.addButton(
                    new HideableButton(LIST_X, OFFSCREEN, 22, ROW_HEIGHT - 2,
                            sel ? "§e☑" : "§7☐"),
                    (b, mb) -> {
                        // クリックで選択・選択解除をトグルする
                        if (selectedUuids.contains(entry.uuid())) {
                            selectedUuids.remove(entry.uuid());
                            b.setDisplayString("§7☐");
                        } else {
                            selectedUuids.add(entry.uuid());
                            b.setDisplayString("§e☑");
                        }
                    });
            selBtn.setShown(true);
            selectButtons.add(selBtn);
        }
    }

    // ───── 描画 ─────

    // 毎フレーム呼ばれる描画メソッド
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // サブタブボタンを描画する
        renderSubTabButton(matrices, tr, x,      y, 90, SUBTAB_H, SubTab.PLAYERS, mouseX, mouseY);
        renderSubTabButton(matrices, tr, x + 94, y, 90, SUBTAB_H, SubTab.DISPLAY, mouseX, mouseY);
        // サブタブの下に区切り線を描画する
        Screen.fill(matrices, x, y + SUBTAB_H + 2, x + width, y + SUBTAB_H + 3, 0x66FFFFFF);

        // アクティブなサブタブのコンテンツを描画する
        if (activeSubTab == SubTab.PLAYERS) {
            renderPlayersContent(matrices, tr, mouseX, mouseY);
        } else {
            renderDisplayContent(matrices, tr);
        }
    }

    // サブタブボタンを1つ描画する（アクティブ・ホバー状態で色を変える）
    private void renderSubTabButton(MatrixStack m, TextRenderer tr,
                                    int bx, int by, int bw, int bh, SubTab tab, int mouseX, int mouseY) {
        boolean active  = (activeSubTab == tab);
        boolean hovered = !active && mouseX >= bx && mouseX < bx + bw
                && mouseY >= by && mouseY < by + bh;
        // 状態に応じた背景色を選択する
        int bg = active ? 0xFF555555 : hovered ? 0xFF3A3A5A : 0xFF222244;
        Screen.fill(m, bx, by, bx + bw, by + bh, bg);
        // アクティブタブは白のアンダーラインを描画する
        Screen.fill(m, bx, by, bx + bw, by + 1, active ? 0xFFFFFFFF : 0x88FFFFFF);
        int textColor = active ? 0xFFFFFF55 : 0xFFCCCCCC;
        float tx = bx + (bw - tr.getWidth(tab.label)) / 2f;
        float ty = by + (bh - 8) / 2f;
        tr.drawWithShadow(m, tab.label, tx, ty, textColor);
    }

    // プレイヤーリストのコンテンツを描画する
    private void renderPlayersContent(MatrixStack matrices, TextRenderer tr, int mouseX, int mouseY) {
        // 一括操作ボタン行のヘッダー
        int headerY = y + SUBTAB_H + 4;
        tr.drawWithShadow(matrices, "§e表示名", LIST_X + BUTTON_W + 30,   headerY + 22, 0xFFFFFF);
        tr.drawWithShadow(matrices, "§7種別",   LIST_X + BUTTON_W + 156, headerY + 22, 0xFFFFFF);
        // 選択数表示
        if (!selectedUuids.isEmpty()) {
            tr.drawWithShadow(matrices, "§e" + selectedUuids.size() + "件選択中",
                    x + width - BUTTON_W - 60, headerY + 4, 0xFFFFAA);
        }

        // サーバーリクエスト中は待機メッセージを表示して早期リターンする
        if (waiting) {
            drawCentered(matrices, tr, "§7サーバーから取得中...", x + width / 2, y + height / 2, 0xAAAAAA);
            return;
        }
        // データが空のときは案内メッセージを表示する
        if (entries.isEmpty()) {
            drawCentered(matrices, tr, "§7プレイヤーデータなし", x + width / 2, y + height / 2, 0xAAAAAA);
            return;
        }

        // カテゴリ分け（表示中 / 非表示）
        List<PlayerListEntry> visibleEntries = entries.stream().filter(e -> !e.isBlocked()).collect(Collectors.toList());
        List<PlayerListEntry> blockedEntries = entries.stream().filter(PlayerListEntry::isBlocked).collect(Collectors.toList());

        // 仮想行リスト（カテゴリヘッダー込み）を構築してスクロール計算に使う
        int totalVirtualH = 0;
        if (!visibleEntries.isEmpty()) totalVirtualH += CATEGORY_H + visibleEntries.size() * ROW_HEIGHT;
        if (!blockedEntries.isEmpty()) totalVirtualH += CATEGORY_H + blockedEntries.size() * ROW_HEIGHT;

        // 一括操作ボタン行(20px)分オフセット
        int listTop    = y + SUBTAB_H + ROW_HEIGHT + 20 + 4;
        int listBottom = y + height - 4;
        int visibleH   = listBottom - listTop;
        int maxScroll  = Math.max(0, totalVirtualH - visibleH);
        // スクロール位置を有効範囲に収める
        scrollOffset   = Math.max(0, Math.min(scrollOffset, maxScroll));

        int listWidth = width - SCROLLBAR_W - 4;
        // GPU スケールに合わせてシザー矩形を計算する
        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        com.mojang.blaze3d.systems.RenderSystem.enableScissor(
                (int)(x * scale),
                (int)(MinecraftClient.getInstance().getWindow().getHeight() - listBottom * scale),
                (int)(listWidth * scale), (int)(visibleH * scale));

        // スクロールを反映した描画開始 Y 座標
        int drawY = listTop - scrollOffset;
        int rowBtnIndex = 0;

        // ── 表示中カテゴリ ──
        if (!visibleEntries.isEmpty()) {
            int catY = drawY;
            // カテゴリヘッダーが表示領域内にあるときだけ描画する
            if (catY + CATEGORY_H >= listTop && catY <= listBottom) {
                Screen.fill(matrices, x, catY, x + width, catY + CATEGORY_H, 0x44AAFFAA);
                tr.drawWithShadow(matrices, "§a■ 表示中 (" + visibleEntries.size() + ")", LIST_X + 2, catY + 3, 0xAAFFAA);
            }
            drawY += CATEGORY_H;

            for (int i = 0; i < visibleEntries.size(); i++) {
                PlayerListEntry entry = visibleEntries.get(i);
                int rowY = drawY + i * ROW_HEIGHT;
                boolean inView = rowY + ROW_HEIGHT >= listTop && rowY <= listBottom;

                if (rowBtnIndex < rowButtons.size()) {
                    HideableButton btn = rowButtons.get(rowBtnIndex);
                    HideableButton selBtn = selectButtons.size() > rowBtnIndex ? selectButtons.get(rowBtnIndex) : null;
                    if (inView) {
                        // 可視行のボタンを実際の Y 座標へ移動する
                        btn.setY(rowY + 1);
                        btn.setDisplayString("§a表示中");
                        if (selBtn != null) {
                            selBtn.setY(rowY + 1);
                            selBtn.setDisplayString(selectedUuids.contains(entry.uuid()) ? "§e☑" : "§7☐");
                        }
                    } else {
                        // 非可視行のボタンを退避させて誤クリックを防ぐ
                        btn.setY(OFFSCREEN);
                        if (selBtn != null) selBtn.setY(OFFSCREEN);
                    }
                }
                rowBtnIndex++;

                if (!inView) continue;
                // 偶数行に薄い背景を描画して視認性を上げる
                if (i % 2 == 0) Screen.fill(matrices, x, rowY, x + width, rowY + ROW_HEIGHT, 0x18FFFFFF);
                String name = truncate(entry.displayName(), 20);
                tr.drawWithShadow(matrices, name, LIST_X + BUTTON_W + 30, rowY + 6, 0xFFFFFF);
                tr.drawWithShadow(matrices, entry.isBot() ? "§6[Bot]" : "§7[人]", LIST_X + BUTTON_W + 156, rowY + 6, 0xFFFFFF);
            }
            drawY += visibleEntries.size() * ROW_HEIGHT;
        }

        // ── 非表示カテゴリ ──
        if (!blockedEntries.isEmpty()) {
            int catY = drawY;
            if (catY + CATEGORY_H >= listTop && catY <= listBottom) {
                Screen.fill(matrices, x, catY, x + width, catY + CATEGORY_H, 0x44FF8888);
                tr.drawWithShadow(matrices, "§c■ 非表示 (" + blockedEntries.size() + ")", LIST_X + 2, catY + 3, 0xFFAAAA);
            }
            drawY += CATEGORY_H;

            for (int i = 0; i < blockedEntries.size(); i++) {
                PlayerListEntry entry = blockedEntries.get(i);
                int rowY = drawY + i * ROW_HEIGHT;
                boolean inView = rowY + ROW_HEIGHT >= listTop && rowY <= listBottom;

                if (rowBtnIndex < rowButtons.size()) {
                    HideableButton btn = rowButtons.get(rowBtnIndex);
                    HideableButton selBtn = selectButtons.size() > rowBtnIndex ? selectButtons.get(rowBtnIndex) : null;
                    if (inView) {
                        btn.setY(rowY + 1);
                        btn.setDisplayString("§c非表示");
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
                if (i % 2 == 0) Screen.fill(matrices, x, rowY, x + width, rowY + ROW_HEIGHT, 0x18FFFFFF);
                String name = truncate(entry.displayName(), 20);
                // 非表示エントリは名前をグレーで表示する
                tr.drawWithShadow(matrices, name, LIST_X + BUTTON_W + 30, rowY + 6, 0x666666);
                tr.drawWithShadow(matrices, entry.isBot() ? "§6[Bot]" : "§7[人]", LIST_X + BUTTON_W + 156, rowY + 6, 0xFFFFFF);
            }
        }

        // 退避（残像防止）：可視範囲外のボタンを画面外へ追いやる
        while (rowBtnIndex < rowButtons.size()) {
            rowButtons.get(rowBtnIndex).setY(OFFSCREEN);
            if (selectButtons.size() > rowBtnIndex) selectButtons.get(rowBtnIndex).setY(OFFSCREEN);
            rowBtnIndex++;
        }

        // シザーを終了する
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();

        // スクロールバーを描画する（スクロールが必要な場合のみ）
        if (maxScroll > 0) {
            int barH   = Math.max(24, visibleH * visibleH / Math.max(1, totalVirtualH));
            barH       = Math.min(barH, visibleH);
            int trackH = visibleH - barH;
            int barY   = listTop + (trackH > 0 ? trackH * scrollOffset / maxScroll : 0);
            barY       = Math.min(barY, listBottom - barH);
            int barX   = x + width - SCROLLBAR_W - 2;
            boolean hov = mouseX >= barX && mouseX <= barX + SCROLLBAR_W
                    && mouseY >= barY && mouseY <= barY + barH;
            // トラック背景を描画する
            Screen.fill(matrices, barX, listTop,  barX + SCROLLBAR_W, listBottom, 0x55000000);
            // ドラッグ中・ホバー中・通常で色を変えてスクロールバーを描画する
            Screen.fill(matrices, barX, barY, barX + SCROLLBAR_W, barY + barH,
                    draggingScrollbar ? 0xFFFFFFFF : hov ? 0xCCFFFFFF : 0x99AAAAAA);
        }
    }

    // 表示設定タブのコンテンツを描画する
    private void renderDisplayContent(MatrixStack matrices, TextRenderer tr) {
        ClientConfig cfg = ClientConfigManager.config;
        int lx = x + 8;

        // ページサイズのラベルを描画する
        tr.drawWithShadow(matrices, "ページサイズ:", lx, dispRowY(0) + 4, 0xFFFFFF);

        // ランキング情報行（合計人数とページ番号）を描画する
        int infoY = dispRowY(0) + ROW_HEIGHT + 4;
        ScoreboardPacketClient.RankingData data = ScoreboardPacketClient.getCachedRanking();
        int total = data != null ? data.full().size() : 0;
        int page  = ScoreboardHudRenderer.getCurrentPage();
        int maxP  = ScoreboardHudRenderer.getMaxPage(total, cfg.scoreboardPageSize);
        tr.drawWithShadow(matrices,
                "ランキング: " + total + "人   ページ: " + (page + 1) + "/" + (maxP + 1),
                lx, infoY, 0xAAAAAA);

        // 区切り線を描画する
        int divY = dispRowY(1) + 22;
        Screen.fill(matrices, x, divY, x + width, divY + 1, 0x44FFFFFF);

        // スケールと位置情報のラベルを描画する
        tr.drawWithShadow(matrices, "スケール:", lx, dispRowY(3) + 4, 0xFFFFFF);
        tr.drawWithShadow(matrices,
                String.format("X:%d%%  Y:%d%%",
                        cfg.scoreboardPositionX, cfg.scoreboardPositionY),
                lx, dispRowY(3) + 26, 0xAAAAAA);
    }

    // ───── 座標ヘルパー ─────

    // 表示設定タブの各行の Y 座標を返す
    private int dispRowY(int index) {
        int base = y + SUBTAB_H + 8;
        return switch (index) {
            case 0 -> base;                                              // ページサイズ
            case 1 -> base + ROW_HEIGHT + 4 + 12;                       // ページ操作
            case 2 -> base + ROW_HEIGHT + 4 + 12 + 22 + 1 + 6;          // 位置/色
            case 3 -> base + ROW_HEIGHT + 4 + 12 + 22 + 1 + 6 + 24;     // スケール
            default -> base;
        };
    }

    // ───── 入力処理 ─────

    // マウスクリックを処理する（サブタブ切り替えとスクロールバードラッグ開始）
    public boolean mouseClicked(double mx, double my, int button) {
        // サブタブバー領域のクリックを処理する
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
        // 左クリック以外またはプレイヤータブ以外は無視する
        if (button != 0 || activeSubTab != SubTab.PLAYERS) return false;

        // カテゴリ込みの totalVirtualH を再計算してスクロールバー領域を判定する
        List<PlayerListEntry> visibleEntries = entries.stream().filter(e -> !e.isBlocked()).collect(Collectors.toList());
        List<PlayerListEntry> blockedEntries = entries.stream().filter(PlayerListEntry::isBlocked).collect(Collectors.toList());
        int totalVirtualH = 0;
        if (!visibleEntries.isEmpty()) totalVirtualH += CATEGORY_H + visibleEntries.size() * ROW_HEIGHT;
        if (!blockedEntries.isEmpty()) totalVirtualH += CATEGORY_H + blockedEntries.size() * ROW_HEIGHT;

        // 一括操作ボタン行(20px)分オフセット
        int listTop    = y + SUBTAB_H + ROW_HEIGHT + 20 + 4;
        int listBottom = y + height - 4;
        int visibleH   = listBottom - listTop;
        int maxScroll  = Math.max(0, totalVirtualH - visibleH);
        if (maxScroll > 0) {
            int barH   = Math.max(24, visibleH * visibleH / Math.max(1, totalVirtualH));
            barH       = Math.min(barH, visibleH);
            int trackH = visibleH - barH;
            int barY   = listTop + (trackH > 0 ? trackH * scrollOffset / maxScroll : 0);
            barY       = Math.min(barY, listBottom - barH);
            int barX   = x + width - SCROLLBAR_W - 2;
            // スクロールバー上でクリックされたらドラッグ開始
            if (mx >= barX && mx <= barX + SCROLLBAR_W && my >= listTop && my <= listBottom) {
                draggingScrollbar = true;
                dragStartMouseY   = my;
                dragStartScroll   = scrollOffset;
                return true;
            }
        }
        return false;
    }

    // マウスボタンリリース時にドラッグ状態を解除する
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingScrollbar) { draggingScrollbar = false; return true; }
        return false;
    }

    // マウスドラッグ中にスクロールバーを追従させる
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!draggingScrollbar) return false;
        // 仮想高さを再計算してスクロール量を算出する
        List<PlayerListEntry> visibleEntries = entries.stream().filter(e -> !e.isBlocked()).collect(Collectors.toList());
        List<PlayerListEntry> blockedEntries = entries.stream().filter(PlayerListEntry::isBlocked).collect(Collectors.toList());
        int totalVirtualH = 0;
        if (!visibleEntries.isEmpty()) totalVirtualH += CATEGORY_H + visibleEntries.size() * ROW_HEIGHT;
        if (!blockedEntries.isEmpty()) totalVirtualH += CATEGORY_H + blockedEntries.size() * ROW_HEIGHT;

        // 一括操作ボタン行(20px)分オフセット
        int listTop    = y + SUBTAB_H + ROW_HEIGHT + 20 + 4;
        int listBottom = y + height - 4;
        int visibleH   = listBottom - listTop;
        int maxScroll  = Math.max(0, totalVirtualH - visibleH);
        if (maxScroll > 0) {
            int barH   = Math.max(24, visibleH * visibleH / Math.max(1, totalVirtualH));
            barH       = Math.min(barH, visibleH);
            int trackH = visibleH - barH;
            if (trackH > 0) {
                // マウス移動量をスクロール量に変換して適用する
                scrollOffset = Math.max(0, Math.min(
                        dragStartScroll + (int)((my - dragStartMouseY) * maxScroll / trackH),
                        maxScroll));
            }
        }
        return true;
    }

    // マウスホイールでリストをスクロールする
    public boolean mouseScrolled(double mx, double my, double amount) {
        // プレイヤータブ以外はスクロール不要
        if (activeSubTab != SubTab.PLAYERS) return false;
        // 一括操作ボタン行(20px)分オフセット
        int listTop    = y + SUBTAB_H + ROW_HEIGHT + 20 + 4;
        int listBottom = y + height - 4;
        // リスト領域外のスクロールは無視する
        if (my < listTop || my > listBottom) return false;
        List<PlayerListEntry> visibleEntries = entries.stream().filter(e -> !e.isBlocked()).collect(Collectors.toList());
        List<PlayerListEntry> blockedEntries = entries.stream().filter(PlayerListEntry::isBlocked).collect(Collectors.toList());
        int totalVirtualH = 0;
        if (!visibleEntries.isEmpty()) totalVirtualH += CATEGORY_H + visibleEntries.size() * ROW_HEIGHT;
        if (!blockedEntries.isEmpty()) totalVirtualH += CATEGORY_H + blockedEntries.size() * ROW_HEIGHT;
        int visibleH  = listBottom - listTop;
        int maxScroll = Math.max(0, totalVirtualH - visibleH);
        // ホイール量に SCROLL_SPEED を掛けてスクロール量を決定する
        scrollOffset  = Math.max(0, Math.min((int)(scrollOffset - amount * SCROLL_SPEED), maxScroll));
        return true;
    }

    // ───── ヘルパー ─────

    // サーバーへプレイヤーリストのリクエストを送る
    private void requestList() { waiting = true; ScoreboardPacketClient.requestPlayerList(); }

    // boolean を ON/OFF 文字列に変換するヘルパー（現在未使用だが将来のため残す）
    private static String onOff(boolean v) { return v ? "§aON" : "§cOFF"; }

    // 文字列を最大文字数に切り詰めるヘルパー
    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    // テキストを中央揃えで描画するヘルパー
    private static void drawCentered(MatrixStack m, TextRenderer tr, String text, int cx, int cy, int color) {
        tr.drawWithShadow(m, text, cx - tr.getWidth(text) / 2f, cy, color);
    }

    // ───── ISliderCallback 実装（スケール 0.5–3.0） ─────

    // スケールスライダーのコールバック実装
    private static class ScaleCallback implements ISliderCallback {
        private final ClientConfig cfg;

        ScaleCallback(ClientConfig cfg) { this.cfg = cfg; }

        // スライダーのステップ数（50 段階）
        @Override public int getMaxSteps() { return 50; }

        // 現在値を 0–1 の相対値に変換して返す
        @Override
        public double getValueRelative() {
            return (cfg.scoreboardScale - 0.5f) / 2.5f;
        }

        // 0–1 の相対値をスケール値（0.5–3.0）に変換して設定する
        @Override
        public void setValueRelative(double rel) {
            float v = (float)(Math.round(rel * 50) * 0.05 + 0.5);
            v = Math.max(0.5f, Math.min(3.0f, v));
            if (Math.abs(cfg.scoreboardScale - v) > 0.001f) {
                cfg.scoreboardScale = v;
                ClientConfigManager.save();
            }
        }

        // スライダーに表示する書式化済み文字列を返す
        @Override
        public String getFormattedDisplayValue() {
            return String.format("%.2fx", cfg.scoreboardScale);
        }
    }

    // ───── 内部クラス：表示切り替え可能な ButtonGeneric ─────

    // 表示/非表示を切り替えられる ButtonGeneric の拡張クラス
    public static class HideableButton extends ButtonGeneric {
        // 表示時の Y 座標（非表示時は OFFSCREEN に退避する）
        private int shownY = OFFSCREEN;

        public HideableButton(int x, int y, int width, int height, String text) {
            super(x, OFFSCREEN, width, height, text);
            this.shownY = y;
        }

        // 表示フラグに応じて Y 座標と visible / enabled を切り替える
        public void setShown(boolean shown) {
            this.y       = shown ? shownY : OFFSCREEN;
            this.visible = shown;
            this.enabled = shown;
        }

        // 表示時の Y 座標を更新する（すでに表示中の場合は即座に反映する）
        public void setShownY(int y) {
            this.shownY = y;
            if (this.visible) this.y = y;
        }
    }

    // ───── 内部クラス：表示切り替え可能な WidgetSlider ─────

    // 表示/非表示を切り替えられる WidgetSlider の拡張クラス
    public static class HideableSlider extends WidgetSlider {
        // 表示時の Y 座標
        private int shownY;

        public HideableSlider(int x, int y, int width, int height, ISliderCallback callback) {
            super(x, OFFSCREEN, width, height, callback);
            this.shownY = y;
        }

        // 表示フラグに応じて Y 座標を切り替える
        public void setShown(boolean shown) {
            this.y = shown ? shownY : OFFSCREEN;
        }
    }

    // ───── ISliderCallback 実装（ページサイズ 1–50） ─────

    // ページサイズスライダーのコールバック実装
    private static class PageSizeCallback implements ISliderCallback {
        private final ClientConfig cfg;

        PageSizeCallback(ClientConfig cfg) { this.cfg = cfg; }

        // スライダーのステップ数（49 段階 → 1〜50）
        @Override public int getMaxSteps() { return 49; }

        // 現在値を 0–1 の相対値に変換して返す
        @Override
        public double getValueRelative() {
            return (cfg.scoreboardPageSize - 1) / 49.0;
        }

        // 0–1 の相対値をページサイズ（1–50）に変換して設定する
        @Override
        public void setValueRelative(double rel) {
            int v = Math.max(1, Math.min(50, (int) Math.round(rel * 49) + 1));
            if (cfg.scoreboardPageSize != v) {
                cfg.scoreboardPageSize = v;
                ClientConfigManager.save();
                // ページサイズ変更時は先頭ページへ戻す
                ScoreboardHudRenderer.resetPage();
            }
        }

        // スライダーに表示する書式化済み文字列を返す
        @Override
        public String getFormattedDisplayValue() {
            return "ページサイズ: " + cfg.scoreboardPageSize;
        }
    }
}
