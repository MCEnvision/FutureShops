package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.transaction.ShopSellService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server sell request from ItemDetailScreen. */
public record C2SSellRequestPacket(String shopId, String itemId, int quantity) {
    public static void encode(C2SSellRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
        buffer.writeUtf(packet.itemId);
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

