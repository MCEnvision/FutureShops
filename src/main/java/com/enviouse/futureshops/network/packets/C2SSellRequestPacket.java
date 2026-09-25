package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.transaction.ShopSellService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → server sell request from ItemDetailScreen. {@code listingId} is the catalog resolution
 * key; the server resolves the exact listing by it, then counts/removes from the player using that
 * listing's registry itemId + NBT.
 */
public record C2SSellRequestPacket(
        String shopId,
        String listingId,
        int quantity,
        UUID requestId,
        long snapshotRevision,
        UUID sessionId
) {
    private static final int MAX_IDENTIFIER_LENGTH = 128;
    private static final UUID UNCORRELATED_SESSION_ID = new UUID(0L, 0L);
    public C2SSellRequestPacket {
        shopId = Objects.requireNonNull(shopId, "shopId");
        listingId = Objects.requireNonNull(listingId, "listingId");
        requestId = Objects.requireNonNull(requestId, "requestId");
        sessionId = sessionId == null ? UNCORRELATED_SESSION_ID : sessionId;
        if (requestId.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException(
                    "Sell request identity is invalid");
        }
        if (snapshotRevision < 0L) {
            throw new IllegalArgumentException("snapshotRevision is invalid");
        }
    }

    public C2SSellRequestPacket(
            String shopId,
            String listingId,
            int quantity
    ) {
        this(shopId, listingId, quantity, UUID.randomUUID(), 0L,
                UNCORRELATED_SESSION_ID);
    }

    public C2SSellRequestPacket(
            String shopId,
            String listingId,
            int quantity,
            UUID requestId
    ) {
        this(shopId, listingId, quantity, requestId, 0L,
                UNCORRELATED_SESSION_ID);
    }

    public C2SSellRequestPacket(
            String shopId,
            String listingId,
            int quantity,
            UUID requestId,
            long snapshotRevision
    ) {
        this(shopId, listingId, quantity, requestId, snapshotRevision,
                UNCORRELATED_SESSION_ID);
    }

    public static void encode(C2SSellRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
        buffer.writeUtf(packet.listingId);
        buffer.writeVarInt(packet.quantity);
        buffer.writeUUID(packet.requestId);
        buffer.writeLong(packet.snapshotRevision);
        buffer.writeUUID(packet.sessionId);
    }

    public static C2SSellRequestPacket decode(FriendlyByteBuf buffer) {
        return new C2SSellRequestPacket(
                buffer.readUtf(MAX_IDENTIFIER_LENGTH),
                buffer.readUtf(MAX_IDENTIFIER_LENGTH),
                buffer.readVarInt(), buffer.readUUID(), buffer.readLong(),
                buffer.readUUID());
    }

    public static void handle(C2SSellRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ShopSellService.handleSellRequest(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}
