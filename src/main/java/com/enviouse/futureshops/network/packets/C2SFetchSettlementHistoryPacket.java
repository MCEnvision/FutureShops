package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.data.SettlementHistoryRow;
import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SFetchSettlementHistoryPacket(
        BlockPos shopPos,
        int page,
        int pageSize,
        SettlementHistoryRow.SettlementFilter filter,
        long fromEpochSeconds,
        long toEpochSeconds) {
    public static void encode(C2SFetchSettlementHistoryPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeVarInt(packet.page());
        buffer.writeVarInt(packet.pageSize());
        buffer.writeUtf(packet.filter().name());
        buffer.writeLong(packet.fromEpochSeconds());
        buffer.writeLong(packet.toEpochSeconds());
    }

    public static C2SFetchSettlementHistoryPacket decode(FriendlyByteBuf buffer) {
        return new C2SFetchSettlementHistoryPacket(
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                SettlementHistoryRow.SettlementFilter.fromWire(buffer.readUtf()),
                buffer.readLong(),
                buffer.readLong());
    }

    public static void handle(C2SFetchSettlementHistoryPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerShopBlockService.sendSettlementHistoryPage(
                        player,
                        packet.shopPos(),
                        packet.page(),
                        packet.pageSize(),
                        packet.filter(),
                        packet.fromEpochSeconds(),
                        packet.toEpochSeconds());
            }
        });
        context.setPacketHandled(true);
    }
}

