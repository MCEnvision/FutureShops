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


public record C2SPlayerShopActionPacket(BlockPos shopPos, String action, int listingIndex, int amount) implements CustomPacketPayload {
    public static final Type<C2SPlayerShopActionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2splayershopactionpacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SPlayerShopActionPacket> STREAM_CODEC = StreamCodec.ofMember(C2SPlayerShopActionPacket::encode, C2SPlayerShopActionPacket::decode);

    @Override
    public Type<C2SPlayerShopActionPacket> type() {
        return TYPE;
    }

    public static void encode(C2SPlayerShopActionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeUtf(packet.action(), 32);
        buffer.writeVarInt(packet.listingIndex());
        buffer.writeVarInt(packet.amount());
    }

    public static C2SPlayerShopActionPacket decode(FriendlyByteBuf buffer) {
        return new C2SPlayerShopActionPacket(buffer.readBlockPos(), buffer.readUtf(32), buffer.readVarInt(), buffer.readVarInt());
    }

    public static void handle(C2SPlayerShopActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                PlayerShopBlockService.applyOwnerAction(player, packet.shopPos(), packet.action(), packet.listingIndex(), packet.amount());
            }
        });
    }
}
