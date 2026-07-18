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
 * Buy-now on a listing with a buyout price (plan §8 completion: locks the listing, holds buyer
 * funds, allocates item + seller proceeds, refunds any displaced top bid, and stops further
 * actions — settle runs in the same durable commit).
 */
public record C2SAuctionBuyNowPacket(
        UUID requestId,
        UUID routeNonce,
        UUID listingId,
        long expectedRevision
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public C2SAuctionBuyNowPacket {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(routeNonce, "routeNonce");
        Objects.requireNonNull(listingId, "listingId");
        if (requestId.equals(ZERO) || listingId.equals(ZERO)) {
            throw new IllegalArgumentException("Auction buy-now identifiers are required");
        }
        if (expectedRevision < 0L) {
            throw new IllegalArgumentException("Auction buy-now revision must not be negative");
        }
    }

    public static void encode(C2SAuctionBuyNowPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUUID(packet.routeNonce());
        buffer.writeUUID(packet.listingId());
        buffer.writeVarLong(packet.expectedRevision());
    }

    public static C2SAuctionBuyNowPacket decode(FriendlyByteBuf buffer) {
        try {
            return new C2SAuctionBuyNowPacket(buffer.readUUID(), buffer.readUUID(),
                    buffer.readUUID(), buffer.readVarLong());
        } catch (RuntimeException exception) {
            throw new DecoderException("Auction buy-now request is invalid", exception);
        }
    }

    public static void handle(C2SAuctionBuyNowPacket packet,
                              Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                AuctionActionService.buyNow(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}
