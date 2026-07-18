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
 * Place (or raise) a bid on an active listing (plan §8 bidding: server locks the listing,
 * re-checks state/deadline/increment, holds only the delta when raising an own bid, and the
 * displaced bidder's refund becomes a durable claim). {@code expectedRevision} is the listing
 * revision the client acted on — a stale value is rejected with {@code STALE_REVISION} so stale
 * interfaces refresh rather than execute (plan §15).
 */
public record C2SAuctionBidPacket(
        UUID requestId,
        UUID routeNonce,
        UUID listingId,
        long expectedRevision,
        long amountMinor
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public C2SAuctionBidPacket {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(routeNonce, "routeNonce");
        Objects.requireNonNull(listingId, "listingId");
        if (requestId.equals(ZERO) || listingId.equals(ZERO)) {
            throw new IllegalArgumentException("Auction bid identifiers are required");
        }
        if (expectedRevision < 0L) {
            throw new IllegalArgumentException("Auction bid revision must not be negative");
        }
        if (amountMinor <= 0L || amountMinor > C2SAuctionCreatePacket.MAX_MINOR) {
            throw new IllegalArgumentException("Auction bid amount is out of bounds");
        }
    }

    public static void encode(C2SAuctionBidPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.requestId());
        buffer.writeUUID(packet.routeNonce());
        buffer.writeUUID(packet.listingId());
        buffer.writeVarLong(packet.expectedRevision());
        buffer.writeVarLong(packet.amountMinor());
    }

    public static C2SAuctionBidPacket decode(FriendlyByteBuf buffer) {
        try {
            return new C2SAuctionBidPacket(buffer.readUUID(), buffer.readUUID(),
                    buffer.readUUID(), buffer.readVarLong(), buffer.readVarLong());
        } catch (RuntimeException exception) {
            throw new DecoderException("Auction bid request is invalid", exception);
        }
    }

    public static void handle(C2SAuctionBidPacket packet,
                              Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                AuctionActionService.bid(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}
