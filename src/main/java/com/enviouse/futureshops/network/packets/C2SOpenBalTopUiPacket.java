package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.MarketplaceAnalyticsService;
import com.enviouse.futureshops.server.util.PageBounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SOpenBalTopUiPacket(int page) {
    public static void encode(C2SOpenBalTopUiPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.page());
    }

    public static C2SOpenBalTopUiPacket decode(FriendlyByteBuf buffer) {
        return new C2SOpenBalTopUiPacket(buffer.readVarInt());
    }

    public static void handle(C2SOpenBalTopUiPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (packet.page() < 1 || packet.page() > PageBounds.MAX_PAGE_INDEX) {
                return;
            }
            MarketplaceAnalyticsService.sendLeaderboard(player, packet.page());
        });
        context.setPacketHandled(true);
    }
}
