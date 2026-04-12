package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.data.BalanceTopEntry;
import com.enviouse.futureshops.network.ShopPackets;
import com.enviouse.futureshops.server.economy.BalanceEntry;
import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record C2SOpenBalTopUiPacket(int page) {
    private static final int PAGE_SIZE = 10;

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
            int safePage = Math.max(1, packet.page());
            EconomyProvider provider = BalanceManager.getProvider();
            List<BalanceEntry> entries = BalanceManager.getTopBalances(safePage, PAGE_SIZE);
            List<BalanceTopEntry> rows = entries.stream().map(entry -> {
                ServerPlayer online = player.server.getPlayerList().getPlayer(entry.playerUUID());
                String name = online != null ? online.getName().getString() : entry.playerUUID().toString().substring(0, 8);
                return new BalanceTopEntry(name, entry.balanceMinorUnits());
            }).toList();
            int totalPages = rows.isEmpty() && safePage > 1 ? safePage : Math.max(1, safePage + (rows.size() == PAGE_SIZE ? 1 : 0));
            ShopPackets.sendToPlayer(player, new S2CBalTopUiPacket(safePage, totalPages, rows, provider.getCurrencyName(), provider.getDecimalPlaces()));
        });
        context.setPacketHandled(true);
    }
}

