package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.network.ServerShopOfferNetworkCodec;
import com.enviouse.futureshops.server.escrow.runtime
        .ServerShopOfferCommit;
import com.enviouse.futureshops.server.shop.PlayerShopOfferEditorService;
import com.enviouse.futureshops.server.transaction.ShopTransactionUtil;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SPlayerShopOfferSavePacket(
        UUID requestId,
        BlockPos shopPos,
        int listingIndex,
        String listingId,
        long expectedRevision,
        ServerShopOfferListing candidate
) {
    private static final int MAXIMUM_ID_LENGTH = 160;
    private static final int MAXIMUM_OFFER_BYTES = 1_048_576;
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public C2SPlayerShopOfferSavePacket {
        requestId = Objects.requireNonNull(requestId, "requestId");
        shopPos = Objects.requireNonNull(shopPos, "shopPos");
        listingId = Objects.requireNonNullElse(
                listingId, "").strip();
        candidate = Objects.requireNonNull(candidate, "candidate");
        if (requestId.equals(ZERO_UUID)
                || !ShopTransactionUtil.isValidPlayerShopListingIndex(
                listingIndex)
                || listingId.isEmpty()
                || listingId.length() > MAXIMUM_ID_LENGTH
                || expectedRevision < 0L
                || expectedRevision
                > ServerShopOfferCommit.MAX_REVISION
                || !candidate.listingId().equals(listingId)) {
            throw new IllegalArgumentException(
                    "Player shop offer save request is invalid");
        }
    }

    public static void encode(
            C2SPlayerShopOfferSavePacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUUID(packet.requestId());
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeVarInt(packet.listingIndex());
        buffer.writeUtf(packet.listingId(), MAXIMUM_ID_LENGTH);
        buffer.writeVarLong(packet.expectedRevision());
        buffer.writeByteArray(
                ServerShopOfferNetworkCodec.encodeListingBytes(
                        packet.candidate()));
    }

    public static C2SPlayerShopOfferSavePacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            return new C2SPlayerShopOfferSavePacket(
                    buffer.readUUID(), buffer.readBlockPos(),
                    buffer.readVarInt(),
                    buffer.readUtf(MAXIMUM_ID_LENGTH),
                    buffer.readVarLong(),
                    ServerShopOfferNetworkCodec.decodeListingBytes(
                            buffer.readByteArray(
                                    MAXIMUM_OFFER_BYTES)));
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Player shop offer save request is malformed",
                    exception);
        }
    }

    public static void handle(
            C2SPlayerShopOfferSavePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerShopOfferEditorService.save(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}
