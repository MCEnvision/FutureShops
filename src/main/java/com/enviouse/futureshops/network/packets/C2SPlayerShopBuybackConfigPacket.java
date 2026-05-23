package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Owner → server: configure a listing's buyback (sell-to-shop) parameters.
 *
 * @param direction "SELL", "BUY" or "BOTH"
 * @param buybackPriceMinor per-unit price the shop pays when buying from a player
 * @param buybackCap maximum units the shop will buy (0 = unlimited)
 */
public record C2SPlayerShopBuybackConfigPacket(BlockPos shopPos, int listingIndex,
                                               String direction, long buybackPriceMinor, int buybackCap) {
    public static void encode(C2SPlayerShopBuybackConfigPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeVarInt(packet.listingIndex());
        buffer.writeUtf(packet.direction());
        buffer.writeLong(packet.buybackPriceMinor());
        buffer.writeVarInt(packet.buybackCap());
    }

    public static C2SPlayerShopBuybackConfigPacket decode(FriendlyByteBuf buffer) {
        return new C2SPlayerShopBuybackConfigPacket(buffer.readBlockPos(), buffer.readVarInt(),
                buffer.readUtf(), buffer.readLong(), buffer.readVarInt());
    }

    public static void handle(C2SPlayerShopBuybackConfigPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerShopBlockService.applyBuybackConfig(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}
