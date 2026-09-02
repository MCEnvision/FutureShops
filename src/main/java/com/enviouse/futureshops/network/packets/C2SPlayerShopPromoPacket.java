package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SPlayerShopPromoPacket(
        BlockPos shopPos,
        int listingIndex,
        boolean clear,
        String promoType,
        double promoValue,
        int buyX,
        int buyY,
        int startsInMinutes,
        int durationMinutes,
        boolean flash) {
    private static final int MAX_PROMO_TYPE_LENGTH = 32;
    public static void encode(C2SPlayerShopPromoPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeVarInt(packet.listingIndex());
        buffer.writeBoolean(packet.clear());
        buffer.writeUtf(packet.promoType());
        buffer.writeDouble(packet.promoValue());
        buffer.writeVarInt(packet.buyX());
        buffer.writeVarInt(packet.buyY());
        buffer.writeVarInt(packet.startsInMinutes());
        buffer.writeVarInt(packet.durationMinutes());
        buffer.writeBoolean(packet.flash());
    }

    public static C2SPlayerShopPromoPacket decode(FriendlyByteBuf buffer) {
        return new C2SPlayerShopPromoPacket(
                buffer.readBlockPos(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readUtf(MAX_PROMO_TYPE_LENGTH),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean());
    }

    public static void handle(C2SPlayerShopPromoPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerShopBlockService.applyPromoAction(
                        player,
                        packet.shopPos(),
                        packet.listingIndex(),
                        packet.clear(),
                        packet.promoType(),
                        packet.promoValue(),
                        packet.buyX(),
                        packet.buyY(),
                        packet.startsInMinutes(),
                        packet.durationMinutes(),
                        packet.flash());
            }
        });
        context.setPacketHandled(true);
    }
}
