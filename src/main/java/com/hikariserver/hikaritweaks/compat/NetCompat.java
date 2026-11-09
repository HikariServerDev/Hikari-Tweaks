package com.hikariserver.hikaritweaks.compat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
//? if <1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
//?}

public final class NetCompat {
    private NetCompat() {}

    @FunctionalInterface
    public interface Receiver {
        void receive(Minecraft client, FriendlyByteBuf buf);
    }

    //? if >=1.20.5 {
    /*
    private static final java.util.Map<String, net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload>> IDS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public record RawPayload(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> typeId,
            FriendlyByteBuf data
    ) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        @Override
        public net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
            return typeId;
        }
    }

    private static net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> idOf(String channel) {
        return IDS.computeIfAbsent(channel,
                c -> new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(IdCompat.parse(c)));
    }

    private static net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, RawPayload> codecOf(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> id) {
        return net.minecraft.network.codec.StreamCodec.of(
                (buf, payload) -> {
                    FriendlyByteBuf data = payload.data();
                    buf.writeBytes(data, data.readerIndex(), data.readableBytes());
                },
                buf -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return new RawPayload(id, new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bytes)));
                });
    }

    public static void registerReceiver(String channel, Receiver receiver) {
        net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> id = idOf(channel);
        registerClientbound(id, codecOf(id));
        ClientPlayNetworking.registerGlobalReceiver(id,
                (payload, context) -> receiver.receive(context.client(), payload.data()));
    }

    public static void registerSendChannel(String channel) {
        net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> id = idOf(channel);
        registerServerbound(id, codecOf(id));
    }

    public static boolean canSend(String channel) {
        return ClientPlayNetworking.canSend(idOf(channel));
    }

    public static FriendlyByteBuf createBuf() {
        return new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
    }

    public static void send(String channel, FriendlyByteBuf buf) {
        ClientPlayNetworking.send(new RawPayload(idOf(channel), buf));
    }
    *///?} else {
    public static void registerReceiver(String channel, Receiver receiver) {
        ClientPlayNetworking.registerGlobalReceiver(IdCompat.parse(channel),
                (client, handler, buf, responseSender) -> receiver.receive(client, buf));
    }

    public static void registerSendChannel(String channel) {
    }

    public static boolean canSend(String channel) {
        return ClientPlayNetworking.canSend(IdCompat.parse(channel));
    }

    public static FriendlyByteBuf createBuf() {
        return PacketByteBufs.create();
    }

    public static void send(String channel, FriendlyByteBuf buf) {
        ClientPlayNetworking.send(IdCompat.parse(channel), buf);
    }
    //?}

    //? if >=26.1 {
    /*private static void registerClientbound(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> id,
            net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, RawPayload> codec) {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(id, codec);
    }

    private static void registerServerbound(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> id,
            net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, RawPayload> codec) {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(id, codec);
    }
    *///?} elif >=1.20.5 {
    /*private static void registerClientbound(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> id,
            net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, RawPayload> codec) {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(id, codec);
    }

    private static void registerServerbound(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> id,
            net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, RawPayload> codec) {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(id, codec);
    }
    *///?}
}
