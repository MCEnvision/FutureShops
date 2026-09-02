package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Owner-only: named saved-config operations for the Payouts tab. {@code op} is one of
 * "SAVE" (store the current shop config under {@code name}), "APPLY" (stamp the named config
 * onto this shop), or "DELETE" (remove the named config). {@code name} is the config name.
 */
public record C2SPlayerShopSavedConfigPacket(BlockPos shopPos, String op, String name) {
    private static final int MAX_OPERATION_LENGTH = 16;
    private static final int MAX_NAME_LENGTH = 24;
    public static void encode(C2SPlayerShopSavedConfigPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeUtf(packet.op());
        buffer.writeUtf(packet.name());
    }

    public static C2SPlayerShopSavedConfigPacket decode(FriendlyByteBuf buffer) {
        return new C2SPlayerShopSavedConfigPacket(buffer.readBlockPos(),
                buffer.readUtf(MAX_OPERATION_LENGTH), buffer.readUtf(MAX_NAME_LENGTH));
    }

    public static void handle(C2SPlayerShopSavedConfigPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerShopBlockService.handleSavedConfig(player, packet.shopPos(), packet.op(), packet.name());
            }
        });
        context.setPacketHandled(true);
    }
}
