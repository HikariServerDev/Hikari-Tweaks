package com.hikariserver.hikaritweaks.compat;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import net.minecraft.client.resources.language.I18n;

import java.util.Locale;

public final class MaliLibConfigCompat {
    private MaliLibConfigCompat() {}

    public static String commentKey(String name) {
        return "config.comment." + name.toLowerCase(Locale.ROOT);
    }

    public static String nameKey(String name) {
        return "config.name." + name.toLowerCase(Locale.ROOT);
    }

    private static String displayName(String name) {
        String key = nameKey(name);
        String translated = I18n.get(key);
        return key.equals(translated) ? name : translated;
    }

    public static String escapeForSecondFormatPass(String resolved) {
        return resolved == null ? null : resolved.replace("%", "%%");
    }

    private static String comment(String name) {
        return escapeForSecondFormatPass(I18n.get(commentKey(name)));
    }

    public static final class BooleanHotkeyed extends ConfigBooleanHotkeyed {
        public BooleanHotkeyed(String name, boolean defaultValue, String defaultHotkey) {
            super(name, defaultValue, defaultHotkey, commentKey(name), nameKey(name));
        }

        @Override
        public String getConfigGuiDisplayName() {
            return displayName(this.getName());
        }

        @Override
        public String getComment() {
            return comment(this.getName());
        }

        //? if >=1.21 {
        /*@Override
        public String getTranslatedName() {
            return displayName(this.getName());
        }
        *///?}
    }

    public static final class Hotkey extends ConfigHotkey {
        public Hotkey(String name, String defaultStorageString) {
            super(name, defaultStorageString, commentKey(name), nameKey(name));
        }

        @Override
        public String getConfigGuiDisplayName() {
            return displayName(this.getName());
        }

        @Override
        public String getComment() {
            return comment(this.getName());
        }

        //? if >=1.21 {
        /*@Override
        public String getTranslatedName() {
            return displayName(this.getName());
        }
        *///?}
    }

    public static final class StringList extends ConfigStringList {
        public StringList(String name, ImmutableList<String> defaultValue) {
            super(name, defaultValue, commentKey(name));
        }

        @Override
        public String getConfigGuiDisplayName() {
            return displayName(this.getName());
        }

        @Override
        public String getComment() {
            return comment(this.getName());
        }

        //? if >=1.21 {
        /*@Override
        public String getTranslatedName() {
            return displayName(this.getName());
        }
        *///?}
    }
}
