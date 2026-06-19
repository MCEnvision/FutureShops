package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;


public record S2CPlayerShopResultPacket(boolean success, String code, String chatMessage) implements CustomPacketPayload {
    public static final Type<S2CPlayerShopResultPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "s2cplayershopresultpacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CPlayerShopResultPacket> STREAM_CODEC = StreamCodec.ofMember(S2CPlayerShopResultPacket::encode, S2CPlayerShopResultPacket::decode);

    @Override
    public Type<S2CPlayerShopResultPacket> type() {
        return TYPE;
    }

    public static void encode(S2CPlayerShopResultPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success());
        buffer.writeUtf(packet.code());
        buffer.writeUtf(packet.chatMessage());
    }

    public static S2CPlayerShopResultPacket decode(FriendlyByteBuf buffer) {
        return new S2CPlayerShopResultPacket(buffer.readBoolean(), buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(S2CPlayerShopResultPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ShopClientPacketHandler.handlePlayerShopResult(packet));
    }
}
