package com.hikariserver.hikaritweaks.gui;

import com.hikariserver.hikaritweaks.HikariTweaksClient;
import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import com.hikariserver.hikaritweaks.config.TweaksOptions;
import fi.dy.masa.malilib.config.options.BooleanHotkeyGuiWrapper;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;

import java.util.Collections;
import java.util.List;

// Hikari-Tweaks のメイン設定画面。
//
// ScoreboardTab.WidgetHost を実装し、ScoreboardTab が生成した
// ButtonGeneric / WidgetSlider をこの GUI の malilib リストに登録する。
// これにより malilib の onMouseClicked ループのみがクリック音を鳴らし、
// バニラ Screen.mouseClicked() による二重再生を防ぐ。
public class HikariTweaksConfigScreen extends GuiConfigsBase
        implements ScoreboardTab.WidgetHost {

    // 現在表示中のタブ（デフォルトは TWEAKS）
    private ConfigGuiTab tab = ConfigGuiTab.TWEAKS;
    // スコアボードタブのコンテンツを管理するオブジェクト
    private ScoreboardTab scoreboardTab;

    // 親画面を受け取ってタイトルとともに malilib ベースクラスを初期化する
    public HikariTweaksConfigScreen(Screen parent) {
        super(10, 50, "hikari-tweaks", parent,
                HikariTweaksClient.MOD_NAME + " " + HikariTweaksClient.getModVersion());
        this.setParent(parent);
    }

    // ── WidgetHost ──────────────────────────────────────────────────────────────

    // ScoreboardTab からボタンをこの画面の malilib ウィジェットリストに追加する
    @Override
    public <T extends ButtonBase> T addButton(T button, IButtonActionListener listener) {
        return super.addButton(button, listener);
    }

    // ScoreboardTab からウィジェットをこの画面の malilib ウィジェットリストに追加する
    @Override
    public <T extends WidgetBase> T addWidget(T widget) {
        return super.addWidget(widget);
    }

    // ── 初期化 ──────────────────────────────────────────────────────────────────

    // 画面を開いたとき・タブ切り替え時に呼ばれる初期化メソッド
    @Override
    public void initGui() {
        // 旧スコアボードタブを破棄してリソースを解放する
        if (scoreboardTab != null) {
            scoreboardTab.onClose();
            scoreboardTab = null;
        }
        super.initGui();
        this.clearOptions();

        // タブボタンを横並びで生成する
        int x = 10, y = 26;
        for (ConfigGuiTab t : ConfigGuiTab.values()) {
            x += createTabButton(x, y, -1, t);
        }

        // スコアボードタブが選択されているときのみ ScoreboardTab を生成する
        if (tab == ConfigGuiTab.SCOREBOARD) {
            scoreboardTab = new ScoreboardTab(this, this);
            int contentY = 50;
            scoreboardTab.init(10, contentY, this.width - 20, this.height - contentY - 4);
        }
    }

    // タブボタンを1つ生成して横幅を返す
    private int createTabButton(int x, int y, int width, ConfigGuiTab targetTab) {
        ButtonGeneric btn = new ButtonGeneric(x, y, width, 20, targetTab.getDisplayName());
        // 現在のタブは押せないよう無効化する
        btn.setEnabled(tab != targetTab);
        this.addButton(btn, new TabButtonListener(targetTab, this));
        return btn.getWidth() + 2;
    }

    // ── config ──────────────────────────────────────────────────────────────────

    // タブに応じた設定欄の幅を返す
    @Override protected int getConfigWidth() {
        return switch (tab) {
            case LISTS      -> 220;
            case HOTKEYS    -> 240;
            case SCOREBOARD -> 0;
            default         -> 260;
        };
    }

    // スコアボードタブはリストブラウザを使わないため幅と高さを 0 にする
    @Override protected int getBrowserWidth()  {
        return tab == ConfigGuiTab.SCOREBOARD ? 0 : super.getBrowserWidth();
    }
    @Override protected int getBrowserHeight() {
        return tab == ConfigGuiTab.SCOREBOARD ? 0 : super.getBrowserHeight();
    }
    // TWEAKS / HOTKEYS タブではキーバインド検索を有効にする
    @Override protected boolean useKeybindSearch() {
        return tab == ConfigGuiTab.TWEAKS || tab == ConfigGuiTab.HOTKEYS;
    }

    // タブに対応する設定オプションのリストを返す
    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return switch (tab) {
            case TWEAKS -> ConfigOptionWrapper.createFor(List.of(
                    wrapConfig(TweaksOptions.FIX_BEACON_RANGE_FREE_CAM),
                    wrapConfig(TweaksOptions.DURABILITY_WARNING_ENABLED),
                    wrapConfig(TweaksOptions.AUTO_RESTOCK_HOTBAR),
                    wrapConfig(TweaksOptions.TOTEM_RESTOCK),
                    wrapConfig(TweaksOptions.HAND_RESTOCK)
            ));
            case LISTS      -> ConfigOptionWrapper.createFor(TweaksOptions.lists());
            case HOTKEYS    -> ConfigOptionWrapper.createFor(TweaksOptions.hotkeys());
            // スコアボードタブは専用描画のためオプションリストは空にする
            case SCOREBOARD -> Collections.emptyList();
        };
    }

    // ── 描画 ────────────────────────────────────────────────────────────────────

    // 毎フレーム呼ばれる描画メソッド。スコアボードタブは追加描画を行う。
    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        // スコアボードタブのコンテンツを描画する
        if (tab == ConfigGuiTab.SCOREBOARD && scoreboardTab != null) {
            scoreboardTab.render(matrices, mouseX, mouseY, delta);
        }
    }

    // ── 入力処理 ────────────────────────────────────────────────────────────────

    // mouseClicked をオーバーライドして「malilib の onMouseClicked のみ呼ぶ」ようにする。
    //
    // GuiBase のデフォルト実装は onMouseClicked が false を返すと
    // super.mouseClicked()（バニラ Screen）を呼ぶ。バニラ Screen は children を走査するが、
    // malilib は addDrawableChild を使わないため現状は二重にならない。
    // ただし将来の安全のため onMouseClicked を直接呼び出し、バニラ側を呼ばない。
    //
    // ScoreboardTab のスクロールバー（malilib 管理外）は先に処理する。
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // スコアボードタブのスクロールバーなどを優先処理する
        if (tab == ConfigGuiTab.SCOREBOARD && scoreboardTab != null) {
            if (scoreboardTab.mouseClicked(mouseX, mouseY, button)) return true;
        }
        // malilib のボタン／ウィジェットループのみ実行（バニラ Screen は呼ばない）
        this.onMouseClicked((int) mouseX, (int) mouseY, button);
        return true;
    }

    // マウスボタンリリースをスコアボードタブへ伝える
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (tab == ConfigGuiTab.SCOREBOARD && scoreboardTab != null) {
            if (scoreboardTab.mouseReleased(mouseX, mouseY, button)) return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // マウスドラッグをスコアボードタブへ伝える
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (tab == ConfigGuiTab.SCOREBOARD && scoreboardTab != null) {
            if (scoreboardTab.mouseDragged(mouseX, mouseY, button, dx, dy)) return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    // マウスホイールをスコアボードタブへ伝える
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (tab == ConfigGuiTab.SCOREBOARD && scoreboardTab != null) {
            if (scoreboardTab.mouseScrolled(mouseX, mouseY, amount)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    // ── ライフサイクル ──────────────────────────────────────────────────────────

    // 画面が閉じられるときに設定を保存してスコアボードタブを破棄する
    @Override
    public void removed() {
        // スコアボードタブのリソースを解放する
        if (scoreboardTab != null) {
            scoreboardTab.onClose();
            scoreboardTab = null;
        }
        super.removed();
        // 設定変更を確実にファイルへ保存する
        TweaksOptions.writeToConfig(ClientConfigManager.config);
        ClientConfigManager.save();
    }

    // 設定が変更されたときに自動保存する
    @Override
    protected void onSettingsChanged() {
        super.onSettingsChanged();
        TweaksOptions.writeToConfig(ClientConfigManager.config);
        ClientConfigManager.save();
    }

    // ConfigBooleanHotkeyed を GUI 用ラッパーに変換するヘルパー
    private BooleanHotkeyGuiWrapper wrapConfig(fi.dy.masa.malilib.config.IHotkeyTogglable config) {
        return new BooleanHotkeyGuiWrapper(config.getPrettyName(), config, config.getKeybind());
    }

    // ── 内部クラス ───────────────────────────────────────────────────────────────

    // タブボタンが押されたときにタブを切り替えるリスナー
    private static class TabButtonListener implements IButtonActionListener {
        private final ConfigGuiTab targetTab;
        private final HikariTweaksConfigScreen gui;

        TabButtonListener(ConfigGuiTab targetTab, HikariTweaksConfigScreen gui) {
            this.targetTab = targetTab;
            this.gui       = gui;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            // タブを切り替えてリストウィジェットを再構築する
            this.gui.tab = this.targetTab;
            this.gui.reCreateListWidget();
            if (this.gui.getListWidget() != null) {
                this.gui.getListWidget().resetScrollbarPosition();
            }
            this.gui.initGui();
        }
    }

    // タブの列挙型。表示名を保持する。
    private enum ConfigGuiTab {
        TWEAKS("hikaritweaks.tab.tweaks"),
        LISTS("hikaritweaks.tab.lists"),
        HOTKEYS("hikaritweaks.tab.hotkeys"),
        SCOREBOARD("hikaritweaks.tab.scoreboard");
        private final String langKey;
        ConfigGuiTab(String langKey) { this.langKey = langKey; }
        public String getDisplayName() { return net.minecraft.client.resource.language.I18n.translate(langKey); }
    }
}
