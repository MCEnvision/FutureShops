package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.data.SettlementHistoryRow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record S2CSettlementHistoryPacket(BlockPos shopPos, int page, int totalPages, List<SettlementHistoryRow> rows) {
    public static void encode(S2CSettlementHistoryPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeVarInt(packet.page());
        buffer.writeVarInt(packet.totalPages());
        buffer.writeCollection(packet.rows(), SettlementHistoryRow::encode);
    }

    public static S2CSettlementHistoryPacket decode(FriendlyByteBuf buffer) {
        return new S2CSettlementHistoryPacket(
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readList(SettlementHistoryRow::decode));
    }

    public static void handle(S2CSettlementHistoryPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleSettlementHistory(packet)));
        context.setPacketHandled(true);
    }
}

