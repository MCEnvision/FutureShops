package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.client.ShopClientPacketHandler;
import com.enviouse.futureshopsp.data.OwnedShopSummary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;

import java.util.List;
import java.util.UUID;

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
        List<String> alerts) implements CustomPacketPayload {
    public static final Type<S2CBalanceUiPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "s2cbalanceuipacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CBalanceUiPacket> STREAM_CODEC = StreamCodec.ofMember(S2CBalanceUiPacket::encode, S2CBalanceUiPacket::decode);

    @Override
    public Type<S2CBalanceUiPacket> type() {
        return TYPE;
    }

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

    public static void handle(S2CBalanceUiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ShopClientPacketHandler.handleBalanceUi(packet));
    }
}

