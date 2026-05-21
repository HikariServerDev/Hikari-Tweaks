package com.hikariserver.hikaritweaks.hotkey;

import com.hikariserver.hikaritweaks.config.TweaksOptions;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

// malilib のキーバインドマネージャに Hikari-Tweaks のホットキーを登録するプロバイダ。
// シングルトンパターンで INSTANCE を公開し、初期化時に一度だけ登録する。
public final class HikariTweaksHotkeyProvider implements IKeybindProvider {

    // シングルトンインスタンス
    public static final HikariTweaksHotkeyProvider INSTANCE = new HikariTweaksHotkeyProvider();

    // インスタンス化を禁止するプライベートコンストラクタ
    private HikariTweaksHotkeyProvider() {}

    // 全ホットキーをキーバインドマップに追加する（重複入力検知に使われる）
    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : TweaksOptions.allHotkeys()) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    // カテゴリ名付きで全ホットキーをマネージャに登録する（設定画面の表示に使われる）
    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory("HikariTweaks", "hikariTweaks.hotkeys.category", TweaksOptions.allHotkeys());
    }
}
