package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.server.shop.PlayerShopBlockService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.server.level.ServerPlayer;


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
        boolean flash) implements CustomPacketPayload {
    public static final Type<C2SPlayerShopPromoPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2splayershoppromopacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SPlayerShopPromoPacket> STREAM_CODEC = StreamCodec.ofMember(C2SPlayerShopPromoPacket::encode, C2SPlayerShopPromoPacket::decode);

    @Override
    public Type<C2SPlayerShopPromoPacket> type() {
        return TYPE;
    }

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
                buffer.readUtf(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean());
    }

    public static void handle(C2SPlayerShopPromoPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
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
    }
}

