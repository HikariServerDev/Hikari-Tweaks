package com.hikariserver.hikaritweaks.lang;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hikariserver.hikaritweaks.compat.MaliLibConfigCompat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class LangFormatTest {
    private static final String[] LOCALES = { "en_us", "ja_jp" };

    private static final Map<String, Integer> EXPECTED_ARG_COUNT = new LinkedHashMap<>();
    static {
        EXPECTED_ARG_COUNT.put("hikaritweaks.scoreboard_tab.selected_count", 1);
        EXPECTED_ARG_COUNT.put("hikaritweaks.scoreboard_tab.shown_group",    1);
        EXPECTED_ARG_COUNT.put("hikaritweaks.scoreboard_tab.hidden_group",   1);
        EXPECTED_ARG_COUNT.put("hikaritweaks.scoreboard_tab.ranking_summary", 3);
        EXPECTED_ARG_COUNT.put("hikaritweaks.scoreboard_tab.page_size_value", 1);
        EXPECTED_ARG_COUNT.put("hikaritweaks.scoreboard_tab.mark_bots",      1);
        EXPECTED_ARG_COUNT.put("hikaritweaks.scoreboard_tab.hide_bots",      1);
        EXPECTED_ARG_COUNT.put("hikaritweaks.scoreboard_tab.kind_breakdown", 2);
        EXPECTED_ARG_COUNT.put("hikaritweaks.message.running",            2);
        EXPECTED_ARG_COUNT.put("hikaritweaks.message.hotbar_restock",     1);
        EXPECTED_ARG_COUNT.put("hikaritweaks.message.durability_warning", 3);
        EXPECTED_ARG_COUNT.put("hikaritweaks.item_picker.registered",     1);
    }

    private static final String[] CONFIG_NAMES = {
            "fixBeaconRangeFreeCam",
            "durabilityWarningEnabled",
            "autoRestockHotbar",
            "totemRestock",
            "handRestock",
            "villagerTradeFix",
            "hotbarRestockList",
            "handRestockList",
            "openConfig",
            "scoreboardNextPage",
            "scoreboardPrevPage",
    };

    private static final Object DUMMY_ARG = Integer.valueOf(1);

    private static final Pattern SPECIFIER =
            Pattern.compile("%(\\d+\\$)?([-#+ 0,(<]*)(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])");

    private static Map<String, String> load(String locale) {
        String path = "/assets/hikari-tweaks/lang/" + locale + ".json";
        Map<String, String> out = new LinkedHashMap<>();
        try (InputStream in = LangFormatTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "lang ファイルがクラスパス上に見つからない: " + path);
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                    out.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (IOException e) {
            throw new AssertionError("lang ファイルを読めない: " + path, e);
        }
        assertTrue(out.size() > 0, locale + ".json が空");
        return out;
    }

    private static int argCount(String locale, String key, String value) {
        int count = 0;
        Matcher m = SPECIFIER.matcher(value);
        while (m.find()) {
            if (m.group(1) != null) {
                fail(locale + " の " + key + " が引数 index 指定 (" + m.group() + ") を使っている。"
                        + "このテストは未対応なので、使うならテスト側も直すこと。");
            }
            if (m.group(2) != null && m.group(2).indexOf('<') >= 0) {
                fail(locale + " の " + key + " が再利用フラグ '<' を使っている。"
                        + "このテストは未対応なので、使うならテスト側も直すこと。");
            }
            String conversion = m.group(6);
            if (!"%".equals(conversion) && !"n".equals(conversion)) {
                count++;
            }
        }
        return count;
    }

    private static Object[] dummyArgs(int n) {
        Object[] args = new Object[n];
        for (int i = 0; i < n; i++) {
            args[i] = DUMMY_ARG;
        }
        return args;
    }

    @Test
    @DisplayName("全ロケールのキー集合が一致する")
    void allLocalesHaveTheSameKeys() {
        Map<String, String> base = load(LOCALES[0]);
        for (int i = 1; i < LOCALES.length; i++) {
            Map<String, String> other = load(LOCALES[i]);
            TreeSet<String> missing = new TreeSet<>(base.keySet());
            missing.removeAll(other.keySet());
            TreeSet<String> extra = new TreeSet<>(other.keySet());
            extra.removeAll(base.keySet());
            assertEquals("[]", missing.toString(),
                    LOCALES[i] + ".json に無いキー（" + LOCALES[0] + ".json にはある）");
            assertEquals("[]", extra.toString(),
                    LOCALES[i] + ".json にだけあるキー");
        }
    }

    @Test
    @DisplayName("書式引数の個数がロケール間で一致する")
    void argCountsAgreeAcrossLocales() {
        Map<String, String> base = load(LOCALES[0]);
        List<String> problems = new ArrayList<>();
        for (int i = 1; i < LOCALES.length; i++) {
            Map<String, String> other = load(LOCALES[i]);
            for (Map.Entry<String, String> e : base.entrySet()) {
                String value = other.get(e.getKey());
                if (value == null) {
                    continue;
                }
                int a = argCount(LOCALES[0], e.getKey(), e.getValue());
                int b = argCount(LOCALES[i], e.getKey(), value);
                if (a != b) {
                    problems.add(e.getKey() + ": " + LOCALES[0] + "=" + a + " / " + LOCALES[i] + "=" + b);
                }
            }
        }
        assertEquals("[]", problems.toString(), "ロケール間で書式引数の個数が食い違っている");
    }

    @Test
    @DisplayName("書式引数の個数が呼び出し側の期待と一致する（未登録キーは 0 個）")
    void argCountsMatchCallSites() {
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            for (Map.Entry<String, String> e : load(locale).entrySet()) {
                int expected = EXPECTED_ARG_COUNT.getOrDefault(e.getKey(), 0);
                int actual = argCount(locale, e.getKey(), e.getValue());
                if (expected != actual) {
                    problems.add(locale + "/" + e.getKey()
                            + ": 期待 " + expected + " 個, 実際 " + actual + " 個  → \"" + e.getValue() + "\"");
                }
            }
        }
        assertEquals("[]", problems.toString(),
                "lang の指定子と呼び出し側の引数の数が合っていない。"
                        + "リテラルの % は %% にエスケープすること。");
    }

    @Test
    @DisplayName("期待個数の引数を渡した String.format が例外を投げない")
    void everyValueSurvivesStringFormat() {
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            for (Map.Entry<String, String> e : load(locale).entrySet()) {
                int expected = EXPECTED_ARG_COUNT.getOrDefault(e.getKey(), 0);
                try {
                    String.format(e.getValue(), dummyArgs(expected));
                } catch (IllegalFormatException ex) {
                    problems.add(locale + "/" + e.getKey()
                            + ": " + ex.getClass().getSimpleName()
                            + "  → \"" + e.getValue() + "\"");
                }
            }
        }
        assertEquals("[]", problems.toString(),
                "String.format が失敗する訳文がある。実機では \"Format error: ...\" と表示される。");
    }

    @Test
    @DisplayName("設定コメントが malilib の二重 String.format を生き延びる")
    void configCommentsSurviveMaliLibDoubleFormat() {
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            for (Map.Entry<String, String> e : load(locale).entrySet()) {
                String key = e.getKey();
                if (!key.startsWith("config.comment.") && !key.startsWith("config.name.")) {
                    continue;
                }
                String once;
                try {
                    once = String.format(e.getValue());
                } catch (IllegalFormatException ex) {
                    continue;
                }
                String handedToMaliLib = MaliLibConfigCompat.escapeForSecondFormatPass(once);
                String shown;
                try {
                    shown = String.format(handedToMaliLib);
                } catch (IllegalFormatException ex) {
                    problems.add(locale + "/" + key + ": 2 回目の String.format が "
                            + ex.getClass().getSimpleName() + "  → \"" + once + "\"");
                    continue;
                }
                if (!shown.equals(once)) {
                    problems.add(locale + "/" + key + ": 2 回目で内容が変わる  \""
                            + once + "\" → \"" + shown + "\"");
                }
            }
        }
        assertEquals("[]", problems.toString(),
                "malilib は設定コメントを 2 回 String.format する。"
                        + "リテラルの % を含む訳文は MaliLibConfigCompat.escapeForSecondFormatPass() が"
                        + "再エスケープしないと実機で \"Format error: ...\" と表示される。");
    }

    @Test
    @DisplayName("設定オプション 3 クラスが getComment() を上書きしている")
    void everyConfigSubclassOverridesGetComment() {
        List<String> problems = new ArrayList<>();
        for (Class<?> c : new Class<?>[] {
                MaliLibConfigCompat.BooleanHotkeyed.class,
                MaliLibConfigCompat.Hotkey.class,
                MaliLibConfigCompat.StringList.class }) {
            try {
                c.getDeclaredMethod("getComment");
            } catch (NoSuchMethodException ex) {
                problems.add(c.getSimpleName()
                        + " が getComment() を上書きしていない（malilib の実装が使われ、"
                        + "再エスケープが効かなくなる）");
            }
        }
        assertEquals("[]", problems.toString(),
                "MaliLibConfigCompat のサブクラスは getComment() を上書きして"
                        + "escapeForSecondFormatPass() を通す必要がある。");
    }

    @Test
    @DisplayName("escapeForSecondFormatPass が 2 回目の String.format を往復する")
    void escapeSurvivesTheSecondFormatPass() {
        String[] samples = {
                "Warns in chat when an item's durability drops to 1% or lower.",
                "耐久値が 1% 以下になったときにチャットへ警告を出します。",
                "100%",
                "% ",
                "%%",
                "no percent at all",
        };
        for (String s : samples) {
            String escaped = MaliLibConfigCompat.escapeForSecondFormatPass(s);
            String shown;
            try {
                shown = String.format(escaped);
            } catch (IllegalFormatException ex) {
                shown = "Format error: " + escaped;
            }
            assertEquals(s, shown,
                    "2 回目の String.format を通したあとに元の文字列へ戻らない: \"" + s + "\"");
        }
    }

    @Test
    @DisplayName("全設定オプションに config.name.* と config.comment.* がそろっている")
    void everyConfigHasNameAndCommentKeys() {
        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            Map<String, String> lang = load(locale);
            for (String name : CONFIG_NAMES) {
                String lower = name.toLowerCase(Locale.ROOT);
                for (String key : new String[] { "config.name." + lower, "config.comment." + lower }) {
                    String value = lang.get(key);
                    if (value == null) {
                        problems.add(locale + ": " + key + " が無い（設定 " + name + "）");
                    } else if (value.isBlank()) {
                        problems.add(locale + ": " + key + " が空（設定 " + name + "）");
                    }
                }
            }
        }
        assertEquals("[]", problems.toString(),
                "設定画面のラベル／ホバーコメントの lang キーが足りない。"
                        + "malilib はキーが無くても例外を出さず、バージョンによって"
                        + "生の camelCase や \"... Comment?\" を表示するだけなので実機でしか気付けない。");
    }

    @Test
    @DisplayName("config.name.* と config.comment.* に余分なキーが無い")
    void noOrphanConfigKeys() {
        TreeSet<String> expected = new TreeSet<>();
        for (String name : CONFIG_NAMES) {
            String lower = name.toLowerCase(Locale.ROOT);
            expected.add("config.name." + lower);
            expected.add("config.comment." + lower);
        }

        List<String> problems = new ArrayList<>();
        for (String locale : LOCALES) {
            for (String key : load(locale).keySet()) {
                if ((key.startsWith("config.name.") || key.startsWith("config.comment."))
                        && !expected.contains(key)) {
                    problems.add(locale + ": " + key);
                }
            }
        }
        assertEquals("[]", problems.toString(),
                "TweaksOptions に対応する設定が無い config.* キーが残っている。"
                        + "設定を消したときは lang からもキーを消すこと。");
    }
}
