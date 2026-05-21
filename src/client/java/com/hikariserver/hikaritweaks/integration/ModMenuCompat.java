package com.hikariserver.hikaritweaks.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.hikariserver.hikaritweaks.gui.HikariTweaksConfigScreen;

// ModMenu との互換レイヤー。
// ModMenu が存在するときにこのクラスがロードされ、
// MOD の設定画面として HikariTweaksConfigScreen を開けるようになる。
public class ModMenuCompat implements ModMenuApi {

    // ModMenu からこの MOD の設定画面ファクトリを返す
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // HikariTweaksConfigScreen::new を渡して親画面付きで生成させる
        return HikariTweaksConfigScreen::new;
    }
}
