package com.hikariserver.hikaritweaks.compat;

import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

// サウンド参照のバージョン差分を吸収するファサード。
// 1.19.3 で SoundEvents の定数が SoundEvent から
// RegistryEntry.Reference<SoundEvent> に変わった。
public final class SoundCompat {

    private SoundCompat() {}

    // 耐久値警告に使うノートブロックの pling 音を返す
    public static SoundEvent noteBlockPling() {
        //? if >=1.19.3 {
        /*return SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
        *///?} else {
        return SoundEvents.BLOCK_NOTE_BLOCK_PLING;
        //?}
    }
}
