package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.server.shop.ShopDataService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.server.level.ServerPlayer;


public record C2SOpenShopPacket(String shopId) implements CustomPacketPayload {
    public static final Type<C2SOpenShopPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2sopenshoppacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SOpenShopPacket> STREAM_CODEC = StreamCodec.ofMember(C2SOpenShopPacket::encode, C2SOpenShopPacket::decode);

    @Override
    public Type<C2SOpenShopPacket> type() {
        return TYPE;
    }

    public static void encode(C2SOpenShopPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId, 128);
    }

    public static C2SOpenShopPacket decode(FriendlyByteBuf buffer) {
        return new C2SOpenShopPacket(buffer.readUtf(128));
    }

    public static void handle(C2SOpenShopPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) {
                return;
            }

            ShopDataService.openShop(player, packet.shopId);
        });
    }
}
