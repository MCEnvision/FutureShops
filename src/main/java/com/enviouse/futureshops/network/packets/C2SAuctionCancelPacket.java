package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.escrow.runtime.AuctionActionService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Seller cancellation of an own listing (plan §8: allowed only before an accepted bid by default;
 * the escrowed item returns to the seller as a claim — cancellation never deletes value).
 */
public record C2SAuctionCancelPacket(
        UUID requestId,
        UUID routeNonce,
        UUID listingId,
        long expectedRevision
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public C2SAuctionCancelPacket {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(routeNonce, "routeNonce");
        Objects.requireNonNull(listingId, "listingId");
        if (requestId.equals(ZERO) || listingId.equals(ZERO)) {
            throw new IllegalArgumentException("Auction cancel identifiers are required");
        }
        if (expectedRevision < 0L) {
            throw new IllegalArgumentException("Auction cancel revision must not be negative");
        }
    }

    public static void encode(C2SAuctionCancelPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUUID(packet.routeNonce());
        buffer.writeUUID(packet.listingId());
        buffer.writeVarLong(packet.expectedRevision());
    }

    public static C2SAuctionCancelPacket decode(FriendlyByteBuf buffer) {
        try {
            return new C2SAuctionCancelPacket(buffer.readUUID(), buffer.readUUID(),
                    buffer.readUUID(), buffer.readVarLong());
        } catch (RuntimeException exception) {
            throw new DecoderException("Auction cancel request is invalid", exception);
        }
    }

    public static void handle(C2SAuctionCancelPacket packet,
                              Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                AuctionActionService.cancel(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}
