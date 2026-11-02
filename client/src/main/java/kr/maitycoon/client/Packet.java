package kr.maitycoon.client;

import kr.maitycoon.client.protocol.Wire;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** One channel's raw bytes. Wire reads and writes them; Minecraft only carries them. */
public record Packet(CustomPacketPayload.Type<Packet> type, byte[] data) implements CustomPacketPayload {
    public static final Type<Packet> HELLO = type(Wire.HELLO), HELLO_ACK = type(Wire.HELLO_ACK), MAP_REQUEST = type(Wire.MAP_REQUEST), MAP = type(Wire.MAP);
    private static Type<Packet> type(String channel) { return new Type<>(Identifier.parse(channel)); }
    private static StreamCodec<FriendlyByteBuf, Packet> codec(Type<Packet> type) {
        return StreamCodec.of((buffer, packet) -> buffer.writeBytes(packet.data()), buffer -> {
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return new Packet(type, data);
        });
    }
    static void register() {
        PayloadTypeRegistry.playC2S().register(HELLO, codec(HELLO));
        PayloadTypeRegistry.playC2S().register(MAP_REQUEST, codec(MAP_REQUEST));
        PayloadTypeRegistry.playS2C().register(HELLO_ACK, codec(HELLO_ACK));
        PayloadTypeRegistry.playS2C().register(MAP, codec(MAP));
    }
}
