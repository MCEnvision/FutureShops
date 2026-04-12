package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.MarketplaceAnalyticsService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SOpenBalanceUiPacket() {
    public static void encode(C2SOpenBalanceUiPacket packet, FriendlyByteBuf buffer) {
    }

    public static C2SOpenBalanceUiPacket decode(FriendlyByteBuf buffer) {
        return new C2SOpenBalanceUiPacket();
    }

    public static void handle(C2SOpenBalanceUiPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            MarketplaceAnalyticsService.sendDashboard(player);
        });
        context.setPacketHandled(true);
    }
}


