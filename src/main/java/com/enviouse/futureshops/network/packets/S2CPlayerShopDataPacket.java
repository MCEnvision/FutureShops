package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.data.PlayerShopListingData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;
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
        boolean barterStorageSame) {

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
                buffer.readBoolean());
    }

    public static void handle(S2CPlayerShopDataPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handlePlayerShopData(packet)));
        context.setPacketHandled(true);
    }
}

