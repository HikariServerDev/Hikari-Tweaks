package com.hikariserver.hikaritweaks.gui;

import com.hikariserver.hikaritweaks.HikariTweaksClient;
import com.hikariserver.hikaritweaks.compat.DrawCtx;
import com.hikariserver.hikaritweaks.compat.ScreenCompat;
import com.hikariserver.hikaritweaks.config.ClientConfigManager;
import com.hikariserver.hikaritweaks.config.TweaksOptions;
//? if >=1.18 {
import fi.dy.masa.malilib.config.options.BooleanHotkeyGuiWrapper;
//?}
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.Collections;
import java.util.List;

public class HikariTweaksConfigScreen extends GuiConfigsBase
        implements ScoreboardTab.WidgetHost {
    private static final int PICKER_BUTTON_ROW_H = 26;

    private ConfigGuiTab tab = ConfigGuiTab.TWEAKS;
    private ScoreboardTab scoreboardTab;

    public HikariTweaksConfigScreen(Screen parent) {
        super(10, 50, "hikari-tweaks", parent,
                HikariTweaksClient.MOD_NAME + " " + HikariTweaksClient.getModVersion());
        this.setParent(parent);
    }

    @Override
    public <T extends ButtonBase> T addButton(T button, IButtonActionListener listener) {
        return super.addButton(button, listener);
    }

    @Override
    public <T extends WidgetBase> T addWidget(T widget) {
        return super.addWidget(widget);
    }

    @Override
    public void initGui() {
        if (scoreboardTab != null) {
            scoreboardTab.onClose();
            scoreboardTab = null;
        }
        super.initGui();
        this.clearOptions();

        int x = 10, y = 26;
        for (ConfigGuiTab t : ConfigGuiTab.values()) {
            x += createTabButton(x, y, -1, t);
        }

        if (tab == ConfigGuiTab.LISTS) {
            int bx = 10;
            int by = this.height - PICKER_BUTTON_ROW_H + 2;
            bx += createPickerButton(bx, by, ItemPickerScreen.Target.HOTBAR_RESTOCK,
                    "hikaritweaks.button.edit_hotbar_restock_list");
            createPickerButton(bx, by, ItemPickerScreen.Target.HAND_RESTOCK,
                    "hikaritweaks.button.edit_hand_restock_list");
        }

        if (tab == ConfigGuiTab.SCOREBOARD) {
            scoreboardTab = new ScoreboardTab(this, this);
            int contentY = 50;
            scoreboardTab.init(10, contentY, this.width - 20, this.height - contentY - 4);
        }
    }

    private int createPickerButton(int x, int y, ItemPickerScreen.Target target, String langKey) {
        ButtonGeneric btn = new ButtonGeneric(x, y, -1, 20, I18n.get(langKey));
        this.addButton(btn, (button, mouseButton) ->
                ScreenCompat.setScreen(Minecraft.getInstance(), new ItemPickerScreen(this, target)));
        return btn.getWidth() + 4;
    }

    private int createTabButton(int x, int y, int width, ConfigGuiTab targetTab) {
        ButtonGeneric btn = new ButtonGeneric(x, y, width, 20, targetTab.getDisplayName());
        btn.setEnabled(tab != targetTab);
        this.addButton(btn, new TabButtonListener(targetTab, this));
        return btn.getWidth() + 2;
    }

    @Override protected int getConfigWidth() {
        return switch (tab) {
            case LISTS      -> 220;
            case HOTKEYS    -> 240;
            case SCOREBOARD -> 0;
            default         -> 260;
        };
    }

    @Override protected int getBrowserWidth()  {
        return tab == ConfigGuiTab.SCOREBOARD ? 0 : super.getBrowserWidth();
    }
    @Override protected int getBrowserHeight() {
        if (tab == ConfigGuiTab.SCOREBOARD) return 0;
        int height = super.getBrowserHeight();
        return tab == ConfigGuiTab.LISTS ? height - PICKER_BUTTON_ROW_H : height;
    }
    @Override protected boolean useKeybindSearch() {
        return tab == ConfigGuiTab.TWEAKS || tab == ConfigGuiTab.HOTKEYS;
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return switch (tab) {
            case TWEAKS -> ConfigOptionWrapper.createFor(List.of(
                    wrapConfig(TweaksOptions.FIX_BEACON_RANGE_FREE_CAM),
                    wrapConfig(TweaksOptions.DURABILITY_WARNING_ENABLED),
                    wrapConfig(TweaksOptions.AUTO_RESTOCK_HOTBAR),
                    wrapConfig(TweaksOptions.TOTEM_RESTOCK),
                    wrapConfig(TweaksOptions.HAND_RESTOCK),
                    wrapConfig(TweaksOptions.VILLAGER_TRADE_FIX)
            ));
            case LISTS      -> ConfigOptionWrapper.createFor(TweaksOptions.lists());
            case HOTKEYS    -> ConfigOptionWrapper.createFor(TweaksOptions.hotkeys());
            case SCOREBOARD -> Collections.emptyList();
        };
    }

    //? if >=26.1 {
    /*@Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor context,
                                   int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        renderContent(new DrawCtx(context), mouseX, mouseY, delta);
    }
    *///?} elif >=1.20 {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        renderContent(new DrawCtx(context), mouseX, mouseY, delta);
    }
    *///?} else {
    @Override
    public void render(PoseStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        renderContent(new DrawCtx(matrices), mouseX, mouseY, delta);
    }
    //?}

    private void renderContent(DrawCtx ctx, int mouseX, int mouseY, float delta) {
        if (tab == ConfigGuiTab.SCOREBOARD && scoreboardTab != null) {
            scoreboardTab.render(ctx, mouseX, mouseY, delta);
        }
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (tab == ConfigGuiTab.SCOREBOARD && scoreboardTab != null) {
            if (scoreboardTab.mouseClicked(click.x(), click.y(), click.button())) return true;
        }
        this.onMouseClicked(click, doubled);
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        if (tab == ConfigGuiTab.SCOREBOARD && scoreboardTab != null) {
            if (scoreboardTab.mouseReleased(click.x(), click.y(), click.button())) return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double dx, double dy) {
        if (tab == ConfigGuiTab.SCOREBOARD && scoreboardTab != null) {
            if (scoreboardTab.mouseDragged(click.x(), click.y(), click.button(), dx, dy)) return true;
        }
        return super.mouseDragged(click, dx, dy);
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == ConfigGuiTab.SCOREBOARD && scoreboardTab != null) {
            if (scoreboardTab.mouseClicked(mouseX, mouseY, button)) return true;
        }
        this.onMouseClicked((int) mouseX, (int) mouseY, button);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (tab == ConfigGuiTab.SCOREBOARD && scoreboardTab != null) {
            if (scoreboardTab.mouseReleased(mouseX, mouseY, button)) return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (tab == ConfigGuiTab.SCOREBOARD && scoreboardTab != null) {
            if (scoreboardTab.mouseDragged(mouseX, mouseY, button, dx, dy)) return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }
    //?}

    //? if >=1.20.2 {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == ConfigGuiTab.SCOREBOARD && scoreboardTab != null) {
            if (scoreboardTab.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    *///?} else {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (tab == ConfigGuiTab.SCOREBOARD && scoreboardTab != null) {
            if (scoreboardTab.mouseScrolled(mouseX, mouseY, amount)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }
    //?}

    @Override
    public void removed() {
        if (scoreboardTab != null) {
            scoreboardTab.onClose();
            scoreboardTab = null;
        }
        super.removed();
        TweaksOptions.writeToConfig(ClientConfigManager.config);
        ClientConfigManager.save();
    }

    @Override
    protected void onSettingsChanged() {
        super.onSettingsChanged();
        TweaksOptions.writeToConfig(ClientConfigManager.config);
        ClientConfigManager.save();
    }

    //? if >=1.18 {
    private BooleanHotkeyGuiWrapper wrapConfig(fi.dy.masa.malilib.config.IHotkeyTogglable config) {
        return new BooleanHotkeyGuiWrapper(config.getPrettyName(), config, config.getKeybind());
    }
    //?} else {
    /*private fi.dy.masa.malilib.config.IConfigBase wrapConfig(fi.dy.masa.malilib.config.IHotkeyTogglable config) {
        return config;
    }
    *///?}

    private static class TabButtonListener implements IButtonActionListener {
        private final ConfigGuiTab targetTab;
        private final HikariTweaksConfigScreen gui;

        TabButtonListener(ConfigGuiTab targetTab, HikariTweaksConfigScreen gui) {
            this.targetTab = targetTab;
            this.gui       = gui;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            this.gui.tab = this.targetTab;
            this.gui.reCreateListWidget();
            if (this.gui.getListWidget() != null) {
                this.gui.getListWidget().resetScrollbarPosition();
            }
            this.gui.initGui();
        }
    }

    private enum ConfigGuiTab {
        TWEAKS("hikaritweaks.tab.tweaks"),
        LISTS("hikaritweaks.tab.lists"),
        HOTKEYS("hikaritweaks.tab.hotkeys"),
        SCOREBOARD("hikaritweaks.tab.scoreboard");
        private final String langKey;
        ConfigGuiTab(String langKey) { this.langKey = langKey; }
        public String getDisplayName() { return net.minecraft.client.resources.language.I18n.get(langKey); }
    }
}
