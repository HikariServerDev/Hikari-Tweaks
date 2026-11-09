package com.hikariserver.hikaritweaks.compat;

//? if >=1.21.11 {
/*import net.minecraft.resources.Identifier;
*///?} else {
import net.minecraft.resources.ResourceLocation;
//?}

public final class IdCompat {
    private IdCompat() {}

    //? if >=1.21.11 {
    /*
    public static Identifier parse(String channel) {
        return Identifier.parse(channel);
    }
    *///?} elif >=1.21 {
    /*public static ResourceLocation parse(String channel) {
        int colon = channel.indexOf(':');
        return ResourceLocation.parse(channel);
    }
    *///?} else {
    public static ResourceLocation parse(String channel) {
        int colon = channel.indexOf(':');
        return new ResourceLocation(channel.substring(0, colon), channel.substring(colon + 1));
    }
    //?}
}
