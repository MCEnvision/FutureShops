package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.market.MarketCapabilityProjectionService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SMarketCapabilitiesPacket(UUID requestId) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public C2SMarketCapabilitiesPacket {
        requestId = Objects.requireNonNull(requestId, "requestId");
        if (ZERO.equals(requestId)) {
            throw new IllegalArgumentException(
                    "Market capability request identity is invalid");
        }
    }

    public static void encode(
            C2SMarketCapabilitiesPacket packet,
            FriendlyByteBuf buffer
    ) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeUUID(packet.requestId());
    }

    public static C2SMarketCapabilitiesPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            C2SMarketCapabilitiesPacket result =
                    new C2SMarketCapabilitiesPacket(buffer.readUUID());
            requireFullyRead(buffer);
            return result;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Market capability request is invalid", exception);
        }
    }

    public static void handle(
            C2SMarketCapabilitiesPacket packet,
            Supplier<NetworkEvent.Context> supplier
    ) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                MarketCapabilityProjectionService.respond(
                        player, packet.requestId());
            }
        });
        context.setPacketHandled(true);
    }

    private static void requireFullyRead(FriendlyByteBuf buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException(
                    "Market capability request has trailing data");
        }
    }
}
