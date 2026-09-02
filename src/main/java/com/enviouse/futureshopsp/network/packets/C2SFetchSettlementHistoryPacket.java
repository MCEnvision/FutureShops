package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.data.SettlementHistoryRow;
import com.enviouse.futureshopsp.server.shop.PlayerShopBlockService;
import com.enviouse.futureshopsp.server.util.PageBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.server.level.ServerPlayer;


public record C2SFetchSettlementHistoryPacket(
        BlockPos shopPos,
        int page,
        int pageSize,
        SettlementHistoryRow.SettlementFilter filter,
        long fromEpochSeconds,
        long toEpochSeconds) implements CustomPacketPayload {
    public static final Type<C2SFetchSettlementHistoryPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2sfetchsettlementhistorypacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SFetchSettlementHistoryPacket> STREAM_CODEC = StreamCodec.ofMember(C2SFetchSettlementHistoryPacket::encode, C2SFetchSettlementHistoryPacket::decode);

    @Override
    public Type<C2SFetchSettlementHistoryPacket> type() {
        return TYPE;
    }

    public static void encode(C2SFetchSettlementHistoryPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeVarInt(packet.page());
        buffer.writeVarInt(packet.pageSize());
        buffer.writeUtf(packet.filter().name(), 32);
        buffer.writeLong(packet.fromEpochSeconds());
        buffer.writeLong(packet.toEpochSeconds());
    }

    public static C2SFetchSettlementHistoryPacket decode(FriendlyByteBuf buffer) {
        return new C2SFetchSettlementHistoryPacket(
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                SettlementHistoryRow.SettlementFilter.fromWire(buffer.readUtf(32)),
                buffer.readLong(),
                buffer.readLong());
    }

    public static void handle(C2SFetchSettlementHistoryPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null && PageBounds.isValid(packet.page(), packet.pageSize())) {
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
    }
}
