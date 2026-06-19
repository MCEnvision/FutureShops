package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.server.shop.MarketplaceAnalyticsService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.server.level.ServerPlayer;


public record C2SOpenBalanceUiPacket() implements CustomPacketPayload {
    public static final Type<C2SOpenBalanceUiPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2sopenbalanceuipacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenBalanceUiPacket> STREAM_CODEC = StreamCodec.ofMember(C2SOpenBalanceUiPacket::encode, C2SOpenBalanceUiPacket::decode);

    @Override
    public Type<C2SOpenBalanceUiPacket> type() {
        return TYPE;
    }

    public static void encode(C2SOpenBalanceUiPacket packet, FriendlyByteBuf buffer) {
    }

    public static C2SOpenBalanceUiPacket decode(FriendlyByteBuf buffer) {
        return new C2SOpenBalanceUiPacket();
    }

    public static void handle(C2SOpenBalanceUiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) {
                return;
            }
            MarketplaceAnalyticsService.sendDashboard(player);
        });
    }
}


