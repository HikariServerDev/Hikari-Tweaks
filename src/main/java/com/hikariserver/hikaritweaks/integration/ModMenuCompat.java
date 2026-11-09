package com.hikariserver.hikaritweaks.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.hikariserver.hikaritweaks.gui.HikariTweaksConfigScreen;

public class ModMenuCompat implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return HikariTweaksConfigScreen::new;
    }
}
