package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.client.ShopClientPacketHandler;
import com.enviouse.futureshopsp.data.PlayerShopListingData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;

import java.util.List;
import java.util.UUID;

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
        boolean adminShopMode) implements CustomPacketPayload {
    public static final Type<S2CPlayerShopDataPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "s2cplayershopdatapacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CPlayerShopDataPacket> STREAM_CODEC = StreamCodec.ofMember(S2CPlayerShopDataPacket::encode, S2CPlayerShopDataPacket::decode);

    @Override
    public Type<S2CPlayerShopDataPacket> type() {
        return TYPE;
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
                buffer.readBoolean());
    }

    public static void handle(S2CPlayerShopDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ShopClientPacketHandler.handlePlayerShopData(packet));
    }
}
