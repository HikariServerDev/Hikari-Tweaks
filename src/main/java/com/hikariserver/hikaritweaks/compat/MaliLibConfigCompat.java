package com.hikariserver.hikaritweaks.compat;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import net.minecraft.client.resource.language.I18n;

import java.util.Locale;

// malilib の設定オプション（名前 / コメントの翻訳解決）のバージョン差分を吸収するファサード。
//
// ── なぜ必要か ─────────────────────────────────────────────────────────────
// malilib は 17 ターゲットに対して 17 バージョンをピン留めしている
// サードパーティライブラリで、設定画面の「表示名」と「ホバーコメント」の
// 解決ロジックが世代ごとに 3 回作り替えられている。実測（jar の逆アセンブル）で
// 確認した挙動は以下のとおり。詳細な根拠は docs/multiversion/PLAN.md §3.10 を参照。
//
//   世代 A: 0.10.0-dev.26            （1.17.1）
//     getComment()               = StringUtils.translate(comment)
//                                  → comment を lang キーとして翻訳する。自動 lookup は無い。
//     getConfigGuiDisplayName()  = getName()
//                                  → 生の camelCase。config.name.* を一切見ない。
//
//   世代 B: 0.11.8 〜 0.19.2         （1.18.1 〜 1.20.6）
//     getComment()               = getTranslatedOrFallback("config.comment."+name.toLowerCase(), comment)
//                                  → キーが見つかればそれを返し、comment は「見つからなかった時の
//                                    フォールバック」でしかない。
//     getConfigGuiDisplayName()  = getTranslatedOrFallback("config.name."+name.toLowerCase(), getName())
//
//   世代 C: 0.21.10 〜 0.26.8        （1.21.1 〜 1.21.10）
//     getComment()               = comment.isEmpty() なら splitCamelCase(name)+" Comment?" を返して終わり。
//                                  非空なら comment が "comment." を含むときだけ comment 自身をキーとして
//                                  lookup し、含まなければ "config.comment."+name.toLowerCase() を引く。
//     getConfigGuiDisplayName()  = getTranslatedName()
//                                  → translatedName が "name." を含むときだけ lookup。含まなければ
//                                    そのまま返す。translatedName の既定値は name（生の camelCase）。
//
//   世代 D: 0.27.17                  （1.21.11）
//     getComment()               = 世代 C から isEmpty 分岐だけが削除されたもの
//                                  （空でも "config.comment.*" を引くので世代 B 相当に戻った）。
//     getConfigGuiDisplayName()  = 世代 C と同じ。
//
// ── 採った対策 ─────────────────────────────────────────────────────────────
// コメント: コンストラクタの comment 引数へ **lang キーそのもの**
//   ("config.comment.<name.lower>") を渡す。これは 4 世代すべてで正しく効く。
//     世代 A: translate(キー) → 訳文
//     世代 B: キーの自動 lookup が先に成功するので comment は使われない（無害）
//     世代 C: 非空かつ "comment." を含むので comment 自身をキーとして lookup → 訳文
//     世代 D: 世代 C と同じ経路
//   ★ 空文字 "" を渡すと世代 C だけが "Durability Warning Enabled Comment?" という
//     プレースホルダを表示する。これが v1.1.0 のバグだった。
//   ★ 逆に「翻訳済みの文字列」を渡すのは不可。世代 B ではキー lookup が優先されるので
//     二重管理になり、lang キーを消したときに黙って訳文へ戻るなど挙動が読めなくなる。
//   ★ さらに getComment() 自体も上書きしている。malilib はコメントを
//     **2 回** String.format に通す（WidgetHoverInfo がもう一度 translate する）ため、
//     リテラルの % を含む訳文は再エスケープしないと "Format error: ..." になる。
//     理由と経路は escapeForSecondFormatPass() のコメントに詳しく書いた。
//     コンストラクタへ lang キーを渡すのは、上書きを外したときに世代 C が
//     "... Comment?" プレースホルダへ落ちないようにする保険として残してある。
//
// 表示名: malilib 側に「全世代で効く引数」が存在しないため、自前で lookup して
//   getConfigGuiDisplayName() を上書きする。
//     世代 A は config.name.* を見る経路そのものが無い。
//     世代 C/D の translatedName にキーを入れるコンストラクタ引数は 0.21.x 以降にしか無い。
//   さらに世代 C/D では BooleanHotkeyGuiWrapper（設定画面で boolean とホットキーを
//   1 行にまとめる malilib 側のラッパー）が getConfigGuiDisplayName() ではなく
//   **中身の設定の getTranslatedName()** を呼ぶため、そちらも上書きする必要がある。
//   getTranslatedName() は 0.21.10 以降にしか存在しないので Stonecutter で分岐する。
public final class MaliLibConfigCompat {

    private MaliLibConfigCompat() {}

    // ── lang キーの生成 ─────────────────────────────────────────────────────

    // ホバーコメントの lang キー。malilib 自身の自動 lookup と同じ規則
    //（"config.comment." + 設定名の小文字化）にそろえてある。
    public static String commentKey(String name) {
        return "config.comment." + name.toLowerCase(Locale.ROOT);
    }

    // 表示名の lang キー。同上（"config.name." + 設定名の小文字化）。
    public static String nameKey(String name) {
        return "config.name." + name.toLowerCase(Locale.ROOT);
    }

    // 設定名から表示名を引く。lang に無ければ生の設定名を返す。
    // I18n.translate() はキーが未定義ならキー文字列をそのまま返すので、それで判定できる。
    private static String displayName(String name) {
        String key = nameKey(name);
        String translated = I18n.translate(key);
        return key.equals(translated) ? name : translated;
    }

    // ── malilib の二重書式化への対処 ─────────────────────────────────────────
    //
    // ★ この再エスケープを消してはいけない。消すと耐久値警告のホバーコメントが
    //   全 17 ターゲット・en_us / ja_jp の両方で
    //   "Format error: Warns in chat when an item's durability drops to 1% or lower."
    //   になる（v1.1.0 で実際に出ていた不具合。修正は v1.2.0 から入っている）。
    //   「余計な %% を掃除する」のは禁止。
    //
    // なぜ必要か:
    //   malilib は設定コメントを **2 回** String.format に通す。
    //     1 回目 … コメントの解決時。malilib の ConfigBase.getComment() も
    //              下の comment() も、最後は I18n.translate(キー) を呼ぶ。
    //              vanilla の I18n.translate は引数の有無に関係なく必ず
    //              String.format(訳文, args) を実行する（全 17 ターゲットで同一）。
    //              ここで lang の "%%" が "%" に戻る。
    //     2 回目 … その戻り値を malilib 自身が再度 translate する。
    //              WidgetConfigOption.addConfigComment(コメント文字列)
    //                → new WidgetHoverInfo(..., コメント文字列)
    //                → WidgetHoverInfo.setInfoLines() が StringUtils.translate(コメント文字列)
    //              StringUtils.translate は I18n.translate そのものなので、
    //              「もう訳し終わった文字列」がふたたび書式文字列として扱われる。
    //              この経路は 0.10.0-dev.26 〜 0.27.17 の全 17 バージョンに存在する
    //              （jar の逆アセンブルで確認済み。世代 A/B/C/D で違いは無い）。
    //   その結果、1 回目を通った時点で生の "%" が残っていると 2 回目で必ず壊れる。
    //     en_us "… drops to 1% or lower."  → "% o" が変換指定子扱い
    //                                        → MissingFormatArgumentException
    //     ja_jp "… 1% 以下 …"              → "% 以" が不正な変換文字
    //                                        → UnknownFormatConversionException
    //   I18n.translate は IllegalFormatException を握り潰して
    //   "Format error: " + 訳文 を返すため、ログにも例外にも出ず画面にだけ出る。
    //
    // 対策:
    //   getComment() が返す直前に "%" を "%%" へ**戻して**おく。
    //   malilib の 2 回目の String.format がそれを "%" に戻すので画面には正しく出る。
    //   lang 側は今までどおり "%%"（＝リテラルの % 1 個）のままでよい。
    //   往復は LangFormatTest#configCommentsSurviveMaliLibDoubleFormat が CI で検証する。
    //
    // 注意: コメントに %d などの本物の変換指定子は書けない（"%%d" になって
    //   そのまま表示される）。malilib はコメントへ書式引数を渡さないので元々使えない。
    public static String escapeForSecondFormatPass(String resolved) {
        return resolved == null ? null : resolved.replace("%", "%%");
    }

    // ホバーコメントを解決する。
    //
    // malilib 側の getComment() は世代ごとに 3 通りの実装があり
    //（クラス先頭のコメント参照）、どれを踏むかで結果が変わりうる。
    // ここで自前に引き直して上書きすることで、4 世代すべてで同じ文字列になる。
    // lang にキーが無ければ I18n.translate はキー文字列をそのまま返すので、
    // ホバーにキーが出て気付ける（null を返すと世代 C/D の
    // ConfigBase.getComment() 経由で NPE になりうるため、null は返さない）。
    private static String comment(String name) {
        return escapeForSecondFormatPass(I18n.translate(commentKey(name)));
    }

    // ── 設定オプションのサブクラス ───────────────────────────────────────────
    //
    // 3 クラスとも「comment 引数に lang キーを渡す」「表示名は自前で引く」だけの薄い皮。
    // 設定名から両方の lang キーを機械的に導くので、名前とキーがずれることが無い。
    // キーの存在は LangFormatTest が CI で検証する。

    // boolean + ホットキーの設定（補助機能タブ）
    public static final class BooleanHotkeyed extends ConfigBooleanHotkeyed {

        public BooleanHotkeyed(String name, boolean defaultValue, String defaultHotkey) {
            super(name, defaultValue, defaultHotkey, commentKey(name), nameKey(name));
        }

        @Override
        public String getConfigGuiDisplayName() {
            return displayName(this.getName());
        }

        // malilib の二重書式化対策。詳細は escapeForSecondFormatPass() のコメント。
        // BooleanHotkeyGuiWrapper はここの戻り値を自分の comment に写して持つので、
        // 補助機能タブ（ラッパー経由）でも同じ文字列が使われる。
        @Override
        public String getComment() {
            return comment(this.getName());
        }

        // 世代 C/D 用。BooleanHotkeyGuiWrapper がここへ委譲してくるので、
        // これが無いと補助機能タブだけ生の camelCase 表示に戻る。
        //? if >=1.21 {
        /*@Override
        public String getTranslatedName() {
            return displayName(this.getName());
        }
        *///?}
    }

    // ホットキーのみの設定（ホットキータブ）
    public static final class Hotkey extends ConfigHotkey {

        public Hotkey(String name, String defaultStorageString) {
            super(name, defaultStorageString, commentKey(name), nameKey(name));
        }

        @Override
        public String getConfigGuiDisplayName() {
            return displayName(this.getName());
        }

        // malilib の二重書式化対策。詳細は escapeForSecondFormatPass() のコメント。
        // ホットキータブと GuiKeybindSettings の両方がこの戻り値をホバーに使う。
        @Override
        public String getComment() {
            return comment(this.getName());
        }

        // 世代 C/D では getConfigGuiDisplayName() の既定実装がこれを呼ぶ。
        // 直接表示される設定なので上の上書きだけでも足りるが、
        // malilib 側の呼び出し経路が増えたときに備えて両方そろえておく。
        //? if >=1.21 {
        /*@Override
        public String getTranslatedName() {
            return displayName(this.getName());
        }
        *///?}
    }

    // 文字列リストの設定（リストタブ）
    public static final class StringList extends ConfigStringList {

        public StringList(String name, ImmutableList<String> defaultValue) {
            super(name, defaultValue, commentKey(name));
        }

        @Override
        public String getConfigGuiDisplayName() {
            return displayName(this.getName());
        }

        // malilib の二重書式化対策。詳細は escapeForSecondFormatPass() のコメント。
        @Override
        public String getComment() {
            return comment(this.getName());
        }

        // 世代 C/D 用。ConfigStringList には prettyName を渡すコンストラクタが
        // 0.19.2 以前に無いため、表示名は全世代ともここで解決する。
        //? if >=1.21 {
        /*@Override
        public String getTranslatedName() {
            return displayName(this.getName());
        }
        *///?}
    }
}
