package com.hikariserver.hikaritweaks.lang;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// lang ファイルの書式指定子（%d など）を機械的に検証するテスト。
//
// なぜ必要か:
//   vanilla の I18n.translate(String key, Object... args) は
//   内部で String.format(訳文, args) を実行し、IllegalFormatException を
//   握り潰して "Format error: " + 訳文 を返す実装になっている（全 17 ターゲットで同一）。
//   つまり lang 側の指定子と呼び出し側の引数がずれても例外は飛ばず、
//   画面に "Format error: ..." と出るだけなのでレビューでもテストでも見落としやすい。
//   実際 v1.1.0 では以下 2 種類のバグを両方出荷してしまった。
//     (1) String.format(I18n.translate(key), args) と二重に書式化していた 5 箇所
//     (2) 訳文中の生の "%" を "%%" にエスケープしていなかった durabilitywarningenabled
//   このテストは Minecraft を一切起動せず（lang の JSON を読んで String.format を
//   直接叩くだけ）、両方を CI で検出できる。
//
// 前提: このモッドの lang が使う変換文字は %d と %% だけ。
//   %f や %s を新たに使うときは DUMMY_ARG の型を見直すこと。
class LangFormatTest {

    // 検証対象の lang ファイル（クラスパス上のパス。processResources の出力を読む）
    private static final String[] LOCALES = { "en_us", "ja_jp" };

    // 書式引数を取るキーと、その引数の個数。
    // ここに無いキーは「引数を取らない」＝訳文に生の指定子があってはならない、とみなす。
    // 呼び出し側（gui/ScoreboardTab.java）の I18n.translate(key, ...) の引数の数と必ず揃えること。
    private static final Map<String, Integer> EXPECTED_ARG_COUNT = new LinkedHashMap<>();
    static {
        EXPECTED_ARG_COUNT.put("hikaritweaks.scoreboard_tab.selected_count", 1);
        EXPECTED_ARG_COUNT.put("hikaritweaks.scoreboard_tab.shown_group",    1);
        EXPECTED_ARG_COUNT.put("hikaritweaks.scoreboard_tab.hidden_group",   1);
        EXPECTED_ARG_COUNT.put("hikaritweaks.scoreboard_tab.ranking_summary", 3);
        EXPECTED_ARG_COUNT.put("hikaritweaks.scoreboard_tab.page_size_value", 1);
    }

    // String.format に流し込むダミー引数（%d を満たせる型）
    private static final Object DUMMY_ARG = Integer.valueOf(1);

    // java.util.Formatter の書式指定子。
    //   %[引数index$][フラグ][幅][.精度][t|T]変換文字
    // "%%"（リテラルの %）と "%n"（改行）は引数を消費しないので個数から除く。
    private static final Pattern SPECIFIER =
            Pattern.compile("%(\\d+\\$)?([-#+ 0,(<]*)(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])");

    // ── ヘルパー ───────────────────────────────────────────────────────────

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

    // 訳文が消費する書式引数の個数を数える。
    // 引数 index 指定（%1$s）と再利用フラグ（%<s）はこのテストが想定していないので落とす。
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

    // ── テスト ─────────────────────────────────────────────────────────────

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
                    continue; // キー欠落は allLocalesHaveTheSameKeys の担当
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

    // 上の 2 つは正規表現による静的な数え上げなので、正規表現が拾えない壊れ方
    //（例: \"1% 以下\" のように % の直後が変換文字にならない）は素通りしてしまう。
    // ここで実際に String.format を通し、I18n が "Format error: " を返す条件そのものを塞ぐ。
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
                    // I18n.translate はこれを握り潰して "Format error: " + 訳文 を返す
                    problems.add(locale + "/" + e.getKey()
                            + ": " + ex.getClass().getSimpleName()
                            + "  → \"" + e.getValue() + "\"");
                }
            }
        }
        assertEquals("[]", problems.toString(),
                "String.format が失敗する訳文がある。実機では \"Format error: ...\" と表示される。");
    }
}
