package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SPlayerShopConfigPacket(BlockPos shopPos, String shopName, boolean singleItemMode, boolean barterStorageSame, int selectedListingIndex) {
    private static final int MAX_SHOP_NAME_LENGTH = 128;
    public static void encode(C2SPlayerShopConfigPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeUtf(packet.shopName());
        buffer.writeBoolean(packet.singleItemMode());
        buffer.writeBoolean(packet.barterStorageSame());
        buffer.writeVarInt(packet.selectedListingIndex());
    }

    public static C2SPlayerShopConfigPacket decode(FriendlyByteBuf buffer) {
        return new C2SPlayerShopConfigPacket(buffer.readBlockPos(),
                buffer.readUtf(MAX_SHOP_NAME_LENGTH), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readVarInt());
    }

    public static void handle(C2SPlayerShopConfigPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerShopBlockService.applyConfig(player, packet.shopPos(), packet.shopName(), packet.singleItemMode(), packet.barterStorageSame(), packet.selectedListingIndex());
            }
        });
        context.setPacketHandled(true);
    }
}
