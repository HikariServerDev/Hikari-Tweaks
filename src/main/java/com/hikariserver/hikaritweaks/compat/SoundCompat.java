package com.hikariserver.hikaritweaks.compat;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class SoundCompat {
    private SoundCompat() {}

    public static SoundEvent noteBlockPling() {
        //? if >=1.19.3 {
        /*return SoundEvents.NOTE_BLOCK_PLING.value();
        *///?} else {
        return SoundEvents.NOTE_BLOCK_PLING;
        //?}
    }
}
