package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.server.shop.MarketplaceAnalyticsService;
import com.enviouse.futureshopsp.server.util.PageBounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.server.level.ServerPlayer;


public record C2SOpenBalTopUiPacket(int page) implements CustomPacketPayload {
    public static final Type<C2SOpenBalTopUiPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2sopenbaltopuipacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenBalTopUiPacket> STREAM_CODEC = StreamCodec.ofMember(C2SOpenBalTopUiPacket::encode, C2SOpenBalTopUiPacket::decode);

    @Override
    public Type<C2SOpenBalTopUiPacket> type() {
        return TYPE;
    }

    public static void encode(C2SOpenBalTopUiPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.page());
    }

    public static C2SOpenBalTopUiPacket decode(FriendlyByteBuf buffer) {
        return new C2SOpenBalTopUiPacket(buffer.readVarInt());
    }

    public static void handle(C2SOpenBalTopUiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) {
                return;
            }
            if (packet.page() >= 1 && packet.page() <= PageBounds.MAX_PAGE_INDEX) {
                MarketplaceAnalyticsService.sendLeaderboard(player, packet.page());
            }
        });
    }
}
