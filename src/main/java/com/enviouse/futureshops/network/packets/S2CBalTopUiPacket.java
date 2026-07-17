package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.data.BalanceTopEntry;
import com.enviouse.futureshops.data.FranchiseLeaderboardEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public record S2CBalTopUiPacket(
        int page,
        int totalPages,
        List<BalanceTopEntry> entries,
        String currencyName,
        int currencyDecimals,
        UUID activityLeaderUuid,
        String activityLeaderName,
        int activityLeaderCount,
        UUID topSellerUuid,
        String topSellerName,
        int topSellerCount,
        String popularItemId,
        int popularItemTrades,
        long popularItemQuantity,
        List<FranchiseLeaderboardEntry> franchises,
        String popularItemNbtJson) {
    public static void encode(S2CBalTopUiPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.page());
        buffer.writeVarInt(packet.totalPages());
        buffer.writeCollection(packet.entries(), BalanceTopEntry::encode);
        buffer.writeUtf(packet.currencyName());
        buffer.writeVarInt(packet.currencyDecimals());
        buffer.writeUUID(packet.activityLeaderUuid());
        buffer.writeUtf(packet.activityLeaderName());
        buffer.writeVarInt(packet.activityLeaderCount());
        buffer.writeUUID(packet.topSellerUuid());
        buffer.writeUtf(packet.topSellerName());
        buffer.writeVarInt(packet.topSellerCount());
        buffer.writeUtf(packet.popularItemId());
        buffer.writeVarInt(packet.popularItemTrades());
        buffer.writeLong(packet.popularItemQuantity());
        buffer.writeCollection(packet.franchises(), FranchiseLeaderboardEntry::encode);
        buffer.writeUtf(packet.popularItemNbtJson() == null ? "" : packet.popularItemNbtJson());
    }

    public static S2CBalTopUiPacket decode(FriendlyByteBuf buffer) {
        return new S2CBalTopUiPacket(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readList(BalanceTopEntry::decode),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readUUID(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readUUID(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readLong(),
                buffer.readList(FranchiseLeaderboardEntry::decode),
                buffer.readUtf());
    }

    public static void handle(S2CBalTopUiPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleBalTopUi(packet)));
        context.setPacketHandled(true);
    }
}

