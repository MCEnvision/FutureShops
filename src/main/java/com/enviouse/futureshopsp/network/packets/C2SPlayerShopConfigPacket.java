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


public record C2SPlayerShopConfigPacket(BlockPos shopPos, String shopName, boolean singleItemMode, boolean barterStorageSame, int selectedListingIndex) implements CustomPacketPayload {
    public static final Type<C2SPlayerShopConfigPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2splayershopconfigpacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SPlayerShopConfigPacket> STREAM_CODEC = StreamCodec.ofMember(C2SPlayerShopConfigPacket::encode, C2SPlayerShopConfigPacket::decode);

    @Override
    public Type<C2SPlayerShopConfigPacket> type() {
        return TYPE;
    }

    public static void encode(C2SPlayerShopConfigPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeUtf(packet.shopName(), 128);
        buffer.writeBoolean(packet.singleItemMode());
        buffer.writeBoolean(packet.barterStorageSame());
        buffer.writeVarInt(packet.selectedListingIndex());
    }

    public static C2SPlayerShopConfigPacket decode(FriendlyByteBuf buffer) {
        return new C2SPlayerShopConfigPacket(buffer.readBlockPos(), buffer.readUtf(128), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt());
    }

    public static void handle(C2SPlayerShopConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                PlayerShopBlockService.applyConfig(player, packet.shopPos(), packet.shopName(), packet.singleItemMode(), packet.barterStorageSame(), packet.selectedListingIndex());
            }
        });
    }
}
