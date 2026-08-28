package com.hikariserver.hikaritweaks.compat;

import net.minecraft.text.Text;
//? if <1.19 {
import net.minecraft.text.LiteralText;
//?}

// Text 生成のバージョン差分を吸収するファサード。
// 1.19 で LiteralText が削除され Text.literal に置き換わった。
public final class TextCompat {

    private TextCompat() {}

    // リテラル文字列から Text を作る
    public static Text literal(String value) {
        //? if >=1.19 {
        /*return Text.literal(value);
        *///?} else {
        return new LiteralText(value);
        //?}
    }

    // 空の Text を作る
    public static Text empty() {
        return literal("");
    }
}
