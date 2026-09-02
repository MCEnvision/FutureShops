package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.data.TransactionHistoryEntry;
import com.enviouse.futureshops.server.transaction.TransactionHistoryService;
import com.enviouse.futureshops.server.util.PageBounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client requests one page of transaction history entries. */
public record C2SFetchHistoryPacket(
        String shopId,
        int page,
        int pageSize,
        TransactionHistoryEntry.HistoryFilter filter,
        String searchText,
        TransactionHistoryEntry.SortOrder sortOrder,
        TransactionHistoryEntry.TimeWindow timeWindow) {
    private static final int MAX_SHOP_ID_LENGTH = 128;
    private static final int MAX_SEARCH_LENGTH = 256;
    public static void encode(C2SFetchHistoryPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
        buffer.writeVarInt(packet.page);
        buffer.writeVarInt(packet.pageSize);
        buffer.writeUtf(packet.filter.name());
        buffer.writeUtf(packet.searchText);
        buffer.writeUtf(packet.sortOrder.name());
        buffer.writeUtf(packet.timeWindow.name());
    }

    public static C2SFetchHistoryPacket decode(FriendlyByteBuf buffer) {
        return new C2SFetchHistoryPacket(
                buffer.readUtf(MAX_SHOP_ID_LENGTH),
                buffer.readVarInt(),
                buffer.readVarInt(),
                TransactionHistoryEntry.HistoryFilter.fromWire(buffer.readUtf(32)),
                buffer.readUtf(MAX_SEARCH_LENGTH),
                TransactionHistoryEntry.SortOrder.fromWire(buffer.readUtf(32)),
                TransactionHistoryEntry.TimeWindow.fromWire(buffer.readUtf(32)));
    }

    public static void handle(C2SFetchHistoryPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && PageBounds.isValid(packet.page(), packet.pageSize())) {
                TransactionHistoryService.sendHistoryPage(
                        player,
                        packet.shopId(),
                        packet.page(),
                        packet.pageSize(),
                        packet.filter(),
                        packet.searchText(),
                        packet.sortOrder(),
                        packet.timeWindow());
            }
        });
        context.setPacketHandled(true);
    }
}
