package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Owner-only: unlinks ONE stock-storage container from the shop by its exact position.
 * Identity-based (not a positional index) so a stale click during lag can never unlink a
 * different-but-still-valid container — it either matches a currently-linked position or no-ops.
 */
public record C2SPlayerShopUnlinkStoragePacket(BlockPos shopPos, BlockPos storagePos) {
    public static void encode(C2SPlayerShopUnlinkStoragePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeBlockPos(packet.storagePos());
    }

    public static C2SPlayerShopUnlinkStoragePacket decode(FriendlyByteBuf buffer) {
        return new C2SPlayerShopUnlinkStoragePacket(buffer.readBlockPos(), buffer.readBlockPos());
    }

    public static void handle(C2SPlayerShopUnlinkStoragePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerShopBlockService.unlinkStorageByPos(player, packet.shopPos(), packet.storagePos());
            }
        });
        context.setPacketHandled(true);
    }
}
