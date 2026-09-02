package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.ShopDataService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SOpenShopPacket(String shopId) {
    private static final int MAX_SHOP_ID_LENGTH = 128;
    public static void encode(C2SOpenShopPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
    }

    public static C2SOpenShopPacket decode(FriendlyByteBuf buffer) {
        return new C2SOpenShopPacket(buffer.readUtf(MAX_SHOP_ID_LENGTH));
    }

    public static void handle(C2SOpenShopPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            ShopDataService.openShop(player, packet.shopId);
        });
        context.setPacketHandled(true);
    }
}
