package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.InventorySyncService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client requests a fresh authoritative owned-item count sync for the active shop. */
public record C2SInventorySyncPacket(String shopId) {
    public static void encode(C2SInventorySyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
    }

    public static C2SInventorySyncPacket decode(FriendlyByteBuf buffer) {
        return new C2SInventorySyncPacket(buffer.readUtf());
    }

    public static void handle(C2SInventorySyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                InventorySyncService.sendOwnedCounts(player, packet.shopId());
            }
        });
        context.setPacketHandled(true);
    }
}

