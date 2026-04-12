package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.session.ShopSessionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record C2SOpenShopPacket(String shopId) {
    public static void encode(C2SOpenShopPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
    }

    public static C2SOpenShopPacket decode(FriendlyByteBuf buffer) {
        return new C2SOpenShopPacket(buffer.readUtf());
    }

    public static void handle(C2SOpenShopPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            String targetShopId = packet.shopId.isBlank() ? "default" : packet.shopId;
            ShopSessionManager.open(player.getUUID(), targetShopId);

            EconomyProvider provider = BalanceManager.getProvider();
            long balance = provider.getBalance(player.getUUID());
            ShopPackets.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CShopDataPacket(targetShopId, balance, provider.getCurrencyName(), provider.getDecimalPlaces()));
        });
        context.setPacketHandled(true);
    }
}

