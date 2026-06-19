package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.data.TransactionHistoryEntry;
import com.enviouse.futureshopsp.server.transaction.TransactionHistoryService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.server.level.ServerPlayer;


/** Client requests one page of transaction history entries. */
public record C2SFetchHistoryPacket(
        String shopId,
        int page,
        int pageSize,
        TransactionHistoryEntry.HistoryFilter filter,
        String searchText,
        TransactionHistoryEntry.SortOrder sortOrder,
        TransactionHistoryEntry.TimeWindow timeWindow) implements CustomPacketPayload {
    public static final Type<C2SFetchHistoryPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2sfetchhistorypacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SFetchHistoryPacket> STREAM_CODEC = StreamCodec.ofMember(C2SFetchHistoryPacket::encode, C2SFetchHistoryPacket::decode);

    @Override
    public Type<C2SFetchHistoryPacket> type() {
        return TYPE;
    }

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
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                TransactionHistoryEntry.HistoryFilter.fromWire(buffer.readUtf()),
                buffer.readUtf(),
                TransactionHistoryEntry.SortOrder.fromWire(buffer.readUtf()),
                TransactionHistoryEntry.TimeWindow.fromWire(buffer.readUtf()));
    }

    public static void handle(C2SFetchHistoryPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
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
    }
}

