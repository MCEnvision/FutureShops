package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.client.ShopClientPacketHandler;
import com.enviouse.futureshopsp.data.BalanceTopEntry;
import com.enviouse.futureshopsp.data.FranchiseLeaderboardEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;

import java.util.List;
import java.util.UUID;

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
        boolean rankingAvailable,
        String providerId,
        String providerLifecycle,
        String providerDiagnostic) implements CustomPacketPayload {
    public static final Type<S2CBalTopUiPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "s2cbaltopuipacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CBalTopUiPacket> STREAM_CODEC = StreamCodec.ofMember(S2CBalTopUiPacket::encode, S2CBalTopUiPacket::decode);

    public S2CBalTopUiPacket(int page, int totalPages, List<BalanceTopEntry> entries, String currencyName,
                             int currencyDecimals, UUID activityLeaderUuid, String activityLeaderName,
                             int activityLeaderCount, UUID topSellerUuid, String topSellerName, int topSellerCount,
                             String popularItemId, int popularItemTrades, long popularItemQuantity,
                             List<FranchiseLeaderboardEntry> franchises) {
        this(page, totalPages, entries, currencyName, currencyDecimals, activityLeaderUuid, activityLeaderName,
                activityLeaderCount, topSellerUuid, topSellerName, topSellerCount, popularItemId, popularItemTrades,
                popularItemQuantity, franchises, true, "internal", "READY", "");
    }

    @Override
    public Type<S2CBalTopUiPacket> type() {
        return TYPE;
    }

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
        buffer.writeBoolean(packet.rankingAvailable());
        buffer.writeUtf(packet.providerId(), 64);
        buffer.writeUtf(packet.providerLifecycle(), 32);
        buffer.writeUtf(packet.providerDiagnostic(), 256);
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
                buffer.readBoolean(),
                buffer.readUtf(64),
                buffer.readUtf(32),
                buffer.readUtf(256));
    }

    public static void handle(S2CBalTopUiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ShopClientPacketHandler.handleBalTopUi(packet));
    }
}
