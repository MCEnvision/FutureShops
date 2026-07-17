package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Owner-only: sets the block's floating-icon mode (CYCLE / OWNER_HEAD / CUSTOM_ITEM) and,
 * for CUSTOM_ITEM, the item id to display. {@code iconMode} is the enum name; unknown values
 * fall back to CYCLE server-side.
 */
public record C2SPlayerShopIconPacket(BlockPos shopPos, String iconMode, String iconItem) {
    public static void encode(C2SPlayerShopIconPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeUtf(packet.iconMode());
        buffer.writeUtf(packet.iconItem());
    }

    public static C2SPlayerShopIconPacket decode(FriendlyByteBuf buffer) {
        return new C2SPlayerShopIconPacket(buffer.readBlockPos(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(C2SPlayerShopIconPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerShopBlockService.applyFloatingIcon(player, packet.shopPos(), packet.iconMode(), packet.iconItem());
            }
        });
        context.setPacketHandled(true);
    }
}
