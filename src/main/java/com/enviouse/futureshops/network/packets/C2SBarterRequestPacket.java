package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.transaction.ShopBarterService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server barter request from ItemDetailScreen / BarterScreen. */
public record C2SBarterRequestPacket(String shopId, String recipeId, int multiplier) {
    public static void encode(C2SBarterRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
        buffer.writeUtf(packet.recipeId);
        buffer.writeVarInt(packet.multiplier);
    }

    public static C2SBarterRequestPacket decode(FriendlyByteBuf buffer) {
        return new C2SBarterRequestPacket(buffer.readUtf(), buffer.readUtf(), buffer.readVarInt());
    }

    public static void handle(C2SBarterRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ShopBarterService.handleBarterRequest(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}

