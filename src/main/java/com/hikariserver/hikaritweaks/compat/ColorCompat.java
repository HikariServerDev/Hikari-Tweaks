package com.hikariserver.hikaritweaks.compat;

public final class ColorCompat {
    private ColorCompat() {}

    public static int opaqueIfNoAlpha(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }
}
