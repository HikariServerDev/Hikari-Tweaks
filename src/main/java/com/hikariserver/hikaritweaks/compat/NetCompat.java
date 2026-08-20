package com.hikariserver.hikaritweaks.compat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
//? if <1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
//?}

// カスタムパケット送受信のバージョン差分を吸収するファサード。
//
// 1.20.5 で Fabric のネットワーキング API が CustomPayload 必須になった。
// サーバー側（HikariScoreBoard = Bukkit プラグイン）は**生の PluginMessage**を
// 送ってくるので、長さプレフィックスを付けずに「残りバイト全部」を読む
// codec を自前で用意している。PacketCodecs.BYTE_ARRAY は長さ付きなので使えない。
public final class NetCompat {

    private NetCompat() {}

    // 受信コールバック。バージョンによらず (client, buf) の形に正規化する。
    @FunctionalInterface
    public interface Receiver {
        void receive(MinecraftClient client, PacketByteBuf buf);
    }

    //? if >=1.20.5 {
    /*// チャンネルごとの CustomPayload.Id をキャッシュする
    private static final java.util.Map<Identifier, net.minecraft.network.packet.CustomPayload.Id<RawPayload>> IDS =
            new java.util.HashMap<>();

    // 生バイト列をそのまま運ぶ CustomPayload
    public record RawPayload(
            net.minecraft.network.packet.CustomPayload.Id<RawPayload> typeId,
            PacketByteBuf data
    ) implements net.minecraft.network.packet.CustomPayload {
        @Override
        public net.minecraft.network.packet.CustomPayload.Id<? extends net.minecraft.network.packet.CustomPayload> getId() {
            return typeId;
        }
    }

    private static net.minecraft.network.packet.CustomPayload.Id<RawPayload> idOf(Identifier channel) {
        return IDS.computeIfAbsent(channel, c -> new net.minecraft.network.packet.CustomPayload.Id<>(c));
    }

    // 残りバイトを全部読む codec を作る
    private static net.minecraft.network.codec.PacketCodec<PacketByteBuf, RawPayload> codecOf(
            net.minecraft.network.packet.CustomPayload.Id<RawPayload> id) {
        return net.minecraft.network.codec.PacketCodec.of(
                (payload, buf) -> buf.writeBytes(payload.data().copy()),
                buf -> new RawPayload(id, new PacketByteBuf(buf.readBytes(buf.readableBytes()))));
    }

    public static void registerReceiver(Identifier channel, Receiver receiver) {
        net.minecraft.network.packet.CustomPayload.Id<RawPayload> id = idOf(channel);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(id, codecOf(id));
        ClientPlayNetworking.registerGlobalReceiver(id,
                (payload, context) -> receiver.receive(context.client(), payload.data()));
    }

    public static void registerSendChannel(Identifier channel) {
        net.minecraft.network.packet.CustomPayload.Id<RawPayload> id = idOf(channel);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(id, codecOf(id));
    }

    public static boolean canSend(Identifier channel) {
        return ClientPlayNetworking.canSend(idOf(channel));
    }

    public static PacketByteBuf createBuf() {
        return new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
    }

    public static void send(Identifier channel, PacketByteBuf buf) {
        ClientPlayNetworking.send(new RawPayload(idOf(channel), buf));
    }
    *///?} else {
    public static void registerReceiver(Identifier channel, Receiver receiver) {
        ClientPlayNetworking.registerGlobalReceiver(channel,
                (client, handler, buf, responseSender) -> receiver.receive(client, buf));
    }

    // 1.20.5 未満は送信チャンネルの事前登録が不要なので何もしない
    public static void registerSendChannel(Identifier channel) {
    }

    public static boolean canSend(Identifier channel) {
        return ClientPlayNetworking.canSend(channel);
    }

    public static PacketByteBuf createBuf() {
        return PacketByteBufs.create();
    }

    public static void send(Identifier channel, PacketByteBuf buf) {
        ClientPlayNetworking.send(channel, buf);
    }
    //?}
}
