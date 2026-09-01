package com.hikariserver.hikaritweaks.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// ColorCompat.opaqueIfNoAlpha() のテスト。
//
// 1.21.5 までの TextRenderer#tweakTransparency と 1 ビットも違ってはならない。
// ここが緩む（例えば 0xFC ではなく 0xFF でマスクする）と、
// 実アルファ付きの ClientConfig 色まで不透明に潰れて HUD の見た目が変わる。
class ColorCompatTest {

    @Test
    @DisplayName("アルファ無しの 24bit 色は不透明になる")
    void addsFullAlphaWhenMissing() {
        // 設定画面が渡しているリテラル
        assertEquals(0xFFFFFFFF, ColorCompat.opaqueIfNoAlpha(0xFFFFFF));
        assertEquals(0xFFAAFFAA, ColorCompat.opaqueIfNoAlpha(0xAAFFAA));
        assertEquals(0xFF666666, ColorCompat.opaqueIfNoAlpha(0x666666));
        assertEquals(0xFFAAAAAA, ColorCompat.opaqueIfNoAlpha(0xAAAAAA));
        assertEquals(0xFFFFFFAA, ColorCompat.opaqueIfNoAlpha(0xFFFFAA));
        assertEquals(0xFFFFAAAA, ColorCompat.opaqueIfNoAlpha(0xFFAAAA));
        assertEquals(0xFFCCCCCC, ColorCompat.opaqueIfNoAlpha(0xCCCCCC));
        // 黒（全ビット 0）も不透明な黒になる
        assertEquals(0xFF000000, ColorCompat.opaqueIfNoAlpha(0x000000));
    }

    @Test
    @DisplayName("実アルファを持つ色は変更しない")
    void keepsExplicitAlpha() {
        // ClientConfig の既定色（ユーザーが色ピッカーで変えられる）
        assertEquals(0x66000000, ColorCompat.opaqueIfNoAlpha(0x66000000));
        assertEquals(0x4D000000, ColorCompat.opaqueIfNoAlpha(0x4D000000));
        assertEquals(0xFFFFFFFF, ColorCompat.opaqueIfNoAlpha(0xFFFFFFFF));
        assertEquals(0xFFFF5555, ColorCompat.opaqueIfNoAlpha(0xFFFF5555));
        assertEquals(0xFFFFFF55, ColorCompat.opaqueIfNoAlpha(0xFFFFFF55));
        // 半透明を意図した色が潰れないこと
        assertEquals(0x80FFFFFF, ColorCompat.opaqueIfNoAlpha(0x80FFFFFF));
        assertEquals(0x04000000, ColorCompat.opaqueIfNoAlpha(0x04000000));
    }

    @Test
    @DisplayName("アルファ 1〜3 はバニラ同様「指定なし」として不透明にする")
    void treatsLowestAlphaBitsAsUnspecified() {
        // マスクは 0xFF ではなく 0xFC。上位 6bit が 0 なら未指定扱い、というバニラの挙動。
        assertEquals(0xFF123456, ColorCompat.opaqueIfNoAlpha(0x01123456));
        assertEquals(0xFF123456, ColorCompat.opaqueIfNoAlpha(0x03123456));
        // 0x04 は上位 6bit に立つので実アルファ扱い
        assertEquals(0x04123456, ColorCompat.opaqueIfNoAlpha(0x04123456));
    }

    @Test
    @DisplayName("冪等（1.21.5 以前でバニラが再適用しても結果が変わらない）")
    void isIdempotent() {
        int[] samples = {0xFFFFFF, 0x000000, 0x66000000, 0x80FFFFFF, 0x03123456, 0xFFFF5555};
        for (int c : samples) {
            int once = ColorCompat.opaqueIfNoAlpha(c);
            assertEquals(once, ColorCompat.opaqueIfNoAlpha(once));
        }
    }
}
