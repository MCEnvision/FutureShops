package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.data.PlayerShopListingData;
import com.enviouse.futureshops.data.PlayerShopNormalizedOfferData;
import com.enviouse.futureshops.data.PlayerShopStorageEntry;
import com.enviouse.futureshops.network.ServerShopOfferNetworkCodec;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record S2CPlayerShopDataPacket(
        BlockPos shopPos,
        boolean owner,
        UUID ownerUuid,
        String ownerName,
        List<PlayerShopListingData> listings,
        boolean linked,
        long pendingSettlementMinor,
        long lifetimeRevenueMinor,
        List<String> recentRevenueRows,
        String shopName,
        boolean singleItemMode,
        boolean barterStorageSame,
        String description,
        String franchiseName,
        boolean placedByCreative,
        boolean adminShopMode,
        // Protocol 27: floating block-top icon mode (+ custom item) and the linked-storage list
        // for the owner Storage sub-tab. Appended LAST to preserve the wire order of older fields.
        String floatingIconMode,
        String floatingIconItem,
        List<PlayerShopStorageEntry> linkedStorages,
        // Owner Payouts tab: names of the viewer's persistent saved shop configs.
        List<String> savedConfigNames,
        List<PlayerShopNormalizedOfferData> normalizedOffers) {

    public S2CPlayerShopDataPacket {
        normalizedOffers = List.copyOf(Objects.requireNonNull(
                normalizedOffers, "normalizedOffers"));
        if (normalizedOffers.size()
                > ServerShopOfferNetworkCodec.MAX_LISTINGS) {
            throw new IllegalArgumentException(
                    "Player shop normalized offer count is invalid");
        }
    }

    public static void encode(S2CPlayerShopDataPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeBoolean(packet.owner());
        buffer.writeUUID(packet.ownerUuid());
        buffer.writeUtf(packet.ownerName());
        buffer.writeCollection(packet.listings(), PlayerShopListingData::encode);
        buffer.writeBoolean(packet.linked());
        buffer.writeLong(packet.pendingSettlementMinor());
        buffer.writeLong(packet.lifetimeRevenueMinor());
        buffer.writeCollection(packet.recentRevenueRows(), FriendlyByteBuf::writeUtf);
        buffer.writeUtf(packet.shopName());
        buffer.writeBoolean(packet.singleItemMode());
        buffer.writeBoolean(packet.barterStorageSame());
        buffer.writeUtf(packet.description());
        buffer.writeUtf(packet.franchiseName());
        buffer.writeBoolean(packet.placedByCreative());
        buffer.writeBoolean(packet.adminShopMode());
        buffer.writeUtf(packet.floatingIconMode());
        buffer.writeUtf(packet.floatingIconItem());
        buffer.writeCollection(packet.linkedStorages(), PlayerShopStorageEntry::encode);
        buffer.writeCollection(packet.savedConfigNames(), FriendlyByteBuf::writeUtf);
        writeNormalizedOffers(buffer, packet.normalizedOffers());
    }

    public static S2CPlayerShopDataPacket decode(FriendlyByteBuf buffer) {
        return new S2CPlayerShopDataPacket(
                buffer.readBlockPos(),
                buffer.readBoolean(),
                buffer.readUUID(),
                buffer.readUtf(),
                buffer.readList(PlayerShopListingData::decode),
                buffer.readBoolean(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readList(FriendlyByteBuf::readUtf),
                buffer.readUtf(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readList(PlayerShopStorageEntry::decode),
                buffer.readList(FriendlyByteBuf::readUtf),
                readNormalizedOffers(buffer));
    }

    private static List<PlayerShopNormalizedOfferData>
    readNormalizedOffers(FriendlyByteBuf buffer) {
        byte[] payload;
        try {
            payload = buffer.readByteArray(
                    ServerShopOfferNetworkCodec
                            .MAX_ENCODED_CATALOG_BYTES);
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Player shop normalized offer payload is too large",
                    exception);
        }
        FriendlyByteBuf encoded = new FriendlyByteBuf(
                Unpooled.wrappedBuffer(payload));
        try {
            int count = encoded.readVarInt();
            if (count < 0
                    || count
                    > ServerShopOfferNetworkCodec.MAX_LISTINGS) {
                throw new DecoderException(
                        "Player shop normalized offer count is invalid");
            }
            List<PlayerShopNormalizedOfferData> offers =
                    new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                offers.add(PlayerShopNormalizedOfferData.decode(
                        encoded));
            }
            if (encoded.isReadable()) {
                throw new DecoderException(
                        "Player shop normalized offers have trailing bytes");
            }
            return List.copyOf(offers);
        } finally {
            encoded.release();
        }
    }

    private static void writeNormalizedOffers(
            FriendlyByteBuf buffer,
            List<PlayerShopNormalizedOfferData> offers
    ) {
        FriendlyByteBuf encoded = new FriendlyByteBuf(
                Unpooled.buffer(256,
                        ServerShopOfferNetworkCodec
                                .MAX_ENCODED_CATALOG_BYTES));
        try {
            encoded.writeVarInt(offers.size());
            for (PlayerShopNormalizedOfferData offer : offers) {
                PlayerShopNormalizedOfferData.encode(encoded, offer);
            }
            byte[] payload = new byte[encoded.readableBytes()];
            encoded.readBytes(payload);
            buffer.writeByteArray(payload);
        } catch (IndexOutOfBoundsException exception) {
            throw new IllegalArgumentException(
                    "Player shop normalized offer payload is too large",
                    exception);
        } finally {
            encoded.release();
        }
    }

    public static void handle(S2CPlayerShopDataPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handlePlayerShopData(packet)));
        context.setPacketHandled(true);
    }
}
