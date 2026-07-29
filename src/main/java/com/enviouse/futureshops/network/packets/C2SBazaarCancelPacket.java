package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.escrow.runtime.BazaarActionService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cancel an own Bazaar order (plan §9 seller custody: cancelling returns only unfilled items /
 * unspent reserve as claims; committed fills never reverse; the cancel waits for the current fill
 * boundary because both run under the same durable commit path).
 */
public record C2SBazaarCancelPacket(
        UUID requestId,
        UUID routeNonce,
        UUID orderId,
        long expectedRevision
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public C2SBazaarCancelPacket {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(routeNonce, "routeNonce");
        Objects.requireNonNull(orderId, "orderId");
        if (requestId.equals(ZERO) || orderId.equals(ZERO)) {
            throw new IllegalArgumentException("Bazaar cancel identifiers are required");
        }
        if (expectedRevision < 0L) {
            throw new IllegalArgumentException("Bazaar cancel revision must not be negative");
        }
    }

    public static void encode(C2SBazaarCancelPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUUID(packet.routeNonce());
        buffer.writeUUID(packet.orderId());
        buffer.writeVarLong(packet.expectedRevision());
    }

    public static C2SBazaarCancelPacket decode(FriendlyByteBuf buffer) {
        try {
            return new C2SBazaarCancelPacket(buffer.readUUID(), buffer.readUUID(),
                    buffer.readUUID(), buffer.readVarLong());
        } catch (RuntimeException exception) {
            throw new DecoderException("Bazaar cancel request is invalid", exception);
        }
    }

    public static void handle(C2SBazaarCancelPacket packet,
                              Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                BazaarActionService.cancel(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}
