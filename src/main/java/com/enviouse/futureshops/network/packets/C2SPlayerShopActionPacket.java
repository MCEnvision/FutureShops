package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SPlayerShopActionPacket(BlockPos shopPos, String action, int amount) {
    public static void encode(C2SPlayerShopActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeUtf(packet.action());
        buffer.writeVarInt(packet.amount());
    }

    public static C2SPlayerShopActionPacket decode(FriendlyByteBuf buffer) {
        return new C2SPlayerShopActionPacket(buffer.readBlockPos(), buffer.readUtf(), buffer.readVarInt());
    }

    public static void handle(C2SPlayerShopActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerShopBlockService.applyOwnerAction(player, packet.shopPos(), packet.action(), packet.amount());
            }
        });
        context.setPacketHandled(true);
    }
}

