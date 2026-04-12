package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;
import java.util.List;

public record S2CPlayerShopDataPacket(
        BlockPos shopPos,
        boolean owner,
        String ownerName,
        String listedItemId,
        String tradeMode,
        long moneyPriceMinor,
        String barterItemId,
        int barterItemCount,
        int stock,
        boolean linked,
        long pendingSettlementMinor,
        long lifetimeRevenueMinor,
        List<String> recentRevenueRows) {

    public static void encode(S2CPlayerShopDataPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeBoolean(packet.owner());
        buffer.writeUtf(packet.ownerName());
        buffer.writeUtf(packet.listedItemId());
        buffer.writeUtf(packet.tradeMode());
        buffer.writeLong(packet.moneyPriceMinor());
        buffer.writeUtf(packet.barterItemId());
        buffer.writeVarInt(packet.barterItemCount());
        buffer.writeVarInt(packet.stock());
        buffer.writeBoolean(packet.linked());
        buffer.writeLong(packet.pendingSettlementMinor());
        buffer.writeLong(packet.lifetimeRevenueMinor());
        buffer.writeCollection(packet.recentRevenueRows(), FriendlyByteBuf::writeUtf);
    }

    public static S2CPlayerShopDataPacket decode(FriendlyByteBuf buffer) {
        return new S2CPlayerShopDataPacket(
                buffer.readBlockPos(),
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readList(FriendlyByteBuf::readUtf));
    }

    public static void handle(S2CPlayerShopDataPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handlePlayerShopData(packet)));
        context.setPacketHandled(true);
    }
}

