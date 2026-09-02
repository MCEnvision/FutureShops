package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.LocalShopAggregator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server: Request aggregated local shop data (owners/franchises with departments+listings).
 * If ownerUuid is non-null, requests the detail view for that specific owner.
 */
public record C2SFetchLocalShopsPacket(String ownerFilter) {
    private static final int MAX_OWNER_FILTER_LENGTH = 128;

    public static void encode(C2SFetchLocalShopsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.ownerFilter);
    }

    public static C2SFetchLocalShopsPacket decode(FriendlyByteBuf buffer) {
        return new C2SFetchLocalShopsPacket(buffer.readUtf(MAX_OWNER_FILTER_LENGTH));
    }

    public static void handle(C2SFetchLocalShopsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.getServer() != null) {
                LocalShopAggregator.sendLocalShops(player, packet.ownerFilter);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
