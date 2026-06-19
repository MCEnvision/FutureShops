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


/** Visitor → server: sell N units of a listing's item to the shop (buyback). */
public record C2SPlayerShopSellPacket(BlockPos shopPos, int listingIndex, int quantity) implements CustomPacketPayload {
    public static final Type<C2SPlayerShopSellPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2splayershopsellpacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SPlayerShopSellPacket> STREAM_CODEC = StreamCodec.ofMember(C2SPlayerShopSellPacket::encode, C2SPlayerShopSellPacket::decode);

    @Override
    public Type<C2SPlayerShopSellPacket> type() {
        return TYPE;
    }

    public static void encode(C2SPlayerShopSellPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeVarInt(packet.listingIndex());
        buffer.writeVarInt(packet.quantity());
    }

    public static C2SPlayerShopSellPacket decode(FriendlyByteBuf buffer) {
        return new C2SPlayerShopSellPacket(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(C2SPlayerShopSellPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                PlayerShopBlockService.handleSell(player, packet);
            }
        });
    }
}
