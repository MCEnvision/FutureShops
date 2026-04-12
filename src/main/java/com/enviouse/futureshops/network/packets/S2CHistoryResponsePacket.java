package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.data.TransactionHistoryEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Server → client transaction history page response. */
public record S2CHistoryResponsePacket(
        String shopId,
        int page,
        int totalPages,
        TransactionHistoryEntry.HistoryFilter filter,
        List<TransactionHistoryEntry> entries) {

    public static void encode(S2CHistoryResponsePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
        buffer.writeVarInt(packet.page);
        buffer.writeVarInt(packet.totalPages);
        buffer.writeUtf(packet.filter.name());
        buffer.writeCollection(packet.entries, TransactionHistoryEntry::encode);
    }

    public static S2CHistoryResponsePacket decode(FriendlyByteBuf buffer) {
        return new S2CHistoryResponsePacket(
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                TransactionHistoryEntry.HistoryFilter.fromWire(buffer.readUtf()),
                buffer.readList(TransactionHistoryEntry::decode));
    }

    public static void handle(S2CHistoryResponsePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleHistoryResponse(packet)));
        context.setPacketHandled(true);
    }
}

