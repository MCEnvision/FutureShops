package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.transaction.ShopSellService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server sell request from ItemDetailScreen. {@code listingId} is the catalog resolution
 * key; the server resolves the exact listing by it, then counts/removes from the player using that
 * listing's registry itemId + NBT.
 */
public record C2SSellRequestPacket(String shopId, String listingId, int quantity) {
    public static void encode(C2SSellRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
        buffer.writeUtf(packet.listingId);
        buffer.writeVarInt(packet.quantity);
    }

    public static C2SSellRequestPacket decode(FriendlyByteBuf buffer) {
        return new C2SSellRequestPacket(buffer.readUtf(), buffer.readUtf(), buffer.readVarInt());
    }

    public static void handle(C2SSellRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ShopSellService.handleSellRequest(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}

