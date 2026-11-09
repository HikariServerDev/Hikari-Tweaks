package com.hikariserver.hikaritweaks.hotkey;

import com.hikariserver.hikaritweaks.config.TweaksOptions;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

public final class HikariTweaksHotkeyProvider implements IKeybindProvider {
    public static final HikariTweaksHotkeyProvider INSTANCE = new HikariTweaksHotkeyProvider();

    private HikariTweaksHotkeyProvider() {}

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : TweaksOptions.allHotkeys()) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory("HikariTweaks", "hikariTweaks.hotkeys.category", TweaksOptions.allHotkeys());
    }
}
