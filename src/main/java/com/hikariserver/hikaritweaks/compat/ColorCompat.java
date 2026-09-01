package com.hikariserver.hikaritweaks.compat;

// テキスト色 ARGB のバージョン差分を吸収する純ロジック。
//
// ★ このクラスを消してはならない。消すと 1.21.6 以降で「文字だけが一切描かれない」に戻る。
//
// 【経緯】
//   1.21.5 までの TextRenderer には private static int tweakTransparency(int) があり、
//   drawInternal / drawLayer の入口で必ず通っていた。中身は
//       (color & 0xFC000000) == 0 ? color | 0xFF000000 : color
//   で、「アルファ上位 6bit が全部 0 なら不透明とみなす」という救済処理。
//   このおかげで 0xFFFFFF のようなアルファ無し 24bit 色を渡しても白で描けていた。
//   （javap で 1.17.1 / 1.18.1 / 1.18.2 / 1.19.2 / 1.19.3 / 1.19.4 /
//     1.20.1 / 1.20.2 / 1.20.4 / 1.20.6 / 1.21.1 / 1.21.3 / 1.21.4 / 1.21.5 の
//     全ターゲットに ldc -67108864 (= 0xFC000000) があることを確認済み）
//
//   1.21.6 の GUI レンダラ刷新（描画を GuiRenderState へ積む方式）で
//   tweakTransparency が丸ごと削除され、代わりに DrawContext#drawText の先頭が
//       if (ColorHelper.getAlpha(color) == 0) return;
//   になった。つまりアルファ 0 の色は「薄く描かれる」のではなく **即 return され何も描かれない**。
//   （javap で 1.21.6 / 1.21.7 / 1.21.8 / 1.21.10 / 1.21.11 に
//     ARGB.alpha/ColorHelper.getAlpha → ifne → return を確認済み。1.21.5 には無い）
//
//   fill() は昔からアルファを補完しないので影響が無く、malilib のボタンも自前で色を持つ。
//   結果として「矩形とボタンは出るのに文字だけ消える」という症状になる。
//
// 【方針】
//   1.21.5 までのバニラと**まったく同じ式**をここに持つ。
//   - 1.21.5 以前：バニラが同じ変換をもう一度掛けるだけ。この関数は冪等なので結果は不変。
//   - 1.21.6 以降：削除された救済処理をこちらで肩代わりする。
//   どのターゲットでも挙動が一致するので stonecutter の分岐は要らない。
//
//   0xFF ではなく 0xFC でマスクするのもバニラのまま。アルファ 1〜3 を
//   「アルファ指定なし」とみなす挙動まで含めて再現しないと差分が出る。
//   ClientConfig の色（0x66000000 など実アルファ付き）は当然そのまま通る。
public final class ColorCompat {

    private ColorCompat() {}

    // アルファが指定されていない ARGB 色を不透明にする。
    // 実アルファ（上位 6bit のいずれかが 1）を持つ色は一切変更しない。
    public static int opaqueIfNoAlpha(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }
}
