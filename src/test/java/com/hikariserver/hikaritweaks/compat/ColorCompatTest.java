package com.hikariserver.hikaritweaks.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorCompatTest {
    @Test
    @DisplayName("アルファ無しの 24bit 色は不透明になる")
    void addsFullAlphaWhenMissing() {
        assertEquals(0xFFFFFFFF, ColorCompat.opaqueIfNoAlpha(0xFFFFFF));
        assertEquals(0xFFAAFFAA, ColorCompat.opaqueIfNoAlpha(0xAAFFAA));
        assertEquals(0xFF666666, ColorCompat.opaqueIfNoAlpha(0x666666));
        assertEquals(0xFFAAAAAA, ColorCompat.opaqueIfNoAlpha(0xAAAAAA));
        assertEquals(0xFFFFFFAA, ColorCompat.opaqueIfNoAlpha(0xFFFFAA));
        assertEquals(0xFFFFAAAA, ColorCompat.opaqueIfNoAlpha(0xFFAAAA));
        assertEquals(0xFFCCCCCC, ColorCompat.opaqueIfNoAlpha(0xCCCCCC));
        assertEquals(0xFF000000, ColorCompat.opaqueIfNoAlpha(0x000000));
    }

    @Test
    @DisplayName("実アルファを持つ色は変更しない")
    void keepsExplicitAlpha() {
        assertEquals(0x66000000, ColorCompat.opaqueIfNoAlpha(0x66000000));
        assertEquals(0x4D000000, ColorCompat.opaqueIfNoAlpha(0x4D000000));
        assertEquals(0xFFFFFFFF, ColorCompat.opaqueIfNoAlpha(0xFFFFFFFF));
        assertEquals(0xFFFF5555, ColorCompat.opaqueIfNoAlpha(0xFFFF5555));
        assertEquals(0xFFFFFF55, ColorCompat.opaqueIfNoAlpha(0xFFFFFF55));
        assertEquals(0x80FFFFFF, ColorCompat.opaqueIfNoAlpha(0x80FFFFFF));
        assertEquals(0x04000000, ColorCompat.opaqueIfNoAlpha(0x04000000));
    }

    @Test
    @DisplayName("アルファ 1〜3 はバニラ同様「指定なし」として不透明にする")
    void treatsLowestAlphaBitsAsUnspecified() {
        assertEquals(0xFF123456, ColorCompat.opaqueIfNoAlpha(0x01123456));
        assertEquals(0xFF123456, ColorCompat.opaqueIfNoAlpha(0x03123456));
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
