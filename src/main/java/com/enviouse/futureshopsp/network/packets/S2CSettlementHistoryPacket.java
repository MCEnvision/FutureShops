package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.client.ShopClientPacketHandler;
import com.enviouse.futureshopsp.data.SettlementHistoryRow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;

import java.util.List;

public record S2CSettlementHistoryPacket(BlockPos shopPos, int page, int totalPages, List<SettlementHistoryRow> rows) implements CustomPacketPayload {
    public static final Type<S2CSettlementHistoryPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "s2csettlementhistorypacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSettlementHistoryPacket> STREAM_CODEC = StreamCodec.ofMember(S2CSettlementHistoryPacket::encode, S2CSettlementHistoryPacket::decode);

    @Override
    public Type<S2CSettlementHistoryPacket> type() {
        return TYPE;
    }

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

    public static void handle(S2CSettlementHistoryPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ShopClientPacketHandler.handleSettlementHistory(packet));
    }
}

