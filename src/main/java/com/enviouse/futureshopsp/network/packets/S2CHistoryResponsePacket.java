package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.client.ShopClientPacketHandler;
import com.enviouse.futureshopsp.data.TransactionHistoryEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;

import java.util.List;

/** Server → client transaction history page response. */
public record S2CHistoryResponsePacket(
        String shopId,
        int page,
        int totalPages,
        TransactionHistoryEntry.HistoryFilter filter,
        List<TransactionHistoryEntry> entries) implements CustomPacketPayload {
    public static final Type<S2CHistoryResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "s2chistoryresponsepacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CHistoryResponsePacket> STREAM_CODEC = StreamCodec.ofMember(S2CHistoryResponsePacket::encode, S2CHistoryResponsePacket::decode);

    @Override
    public Type<S2CHistoryResponsePacket> type() {
        return TYPE;
    }


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

    public static void handle(S2CHistoryResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ShopClientPacketHandler.handleHistoryResponse(packet));
    }
}

