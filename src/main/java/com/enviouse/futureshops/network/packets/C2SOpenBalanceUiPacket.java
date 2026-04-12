package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
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
            EconomyProvider provider = BalanceManager.getProvider();
            ShopPackets.sendToPlayer(player, new S2CBalanceUiPacket(
                    provider.getBalance(player.getUUID()),
                    provider.getCurrencyName(),
                    provider.getDecimalPlaces()));
        });
        context.setPacketHandled(true);
    }
}

