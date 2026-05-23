package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Visitor → server: sell N units of a listing's item to the shop (buyback). */
public record C2SPlayerShopSellPacket(BlockPos shopPos, int listingIndex, int quantity) {
    public static void encode(C2SPlayerShopSellPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeVarInt(packet.listingIndex());
        buffer.writeVarInt(packet.quantity());
    }

    public static C2SPlayerShopSellPacket decode(FriendlyByteBuf buffer) {
        return new C2SPlayerShopSellPacket(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(C2SPlayerShopSellPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerShopBlockService.handleSell(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}
