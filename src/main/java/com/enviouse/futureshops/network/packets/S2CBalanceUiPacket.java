package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.data.OwnedShopSummary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public record S2CBalanceUiPacket(
        UUID playerUuid,
        String playerName,
        long balanceMinorUnits,
        String currencyName,
        int currencyDecimals,
        long totalRevenueMinor,
        long pendingSettlementMinor,
        int shopCount,
        int listingCount,
        int totalStock,
        int lowSupplyCount,
        List<OwnedShopSummary> shopSummaries,
        List<String> alerts) {
    public static void encode(S2CBalanceUiPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.playerUuid());
        buffer.writeUtf(packet.playerName());
        buffer.writeLong(packet.balanceMinorUnits());
        buffer.writeUtf(packet.currencyName());
        buffer.writeVarInt(packet.currencyDecimals());
        buffer.writeLong(packet.totalRevenueMinor());
        buffer.writeLong(packet.pendingSettlementMinor());
        buffer.writeVarInt(packet.shopCount());
        buffer.writeVarInt(packet.listingCount());
        buffer.writeVarInt(packet.totalStock());
        buffer.writeVarInt(packet.lowSupplyCount());
        buffer.writeCollection(packet.shopSummaries(), OwnedShopSummary::encode);
        buffer.writeCollection(packet.alerts(), FriendlyByteBuf::writeUtf);
    }

    public static S2CBalanceUiPacket decode(FriendlyByteBuf buffer) {
        return new S2CBalanceUiPacket(
                buffer.readUUID(),
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readLong(),
                buffer.readLong(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readList(OwnedShopSummary::decode),
                buffer.readList(FriendlyByteBuf::readUtf));
    }

    public static void handle(S2CBalanceUiPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleBalanceUi(packet)));
        context.setPacketHandled(true);
    }
}

