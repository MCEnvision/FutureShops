package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.server.shop.LocalShopAggregator;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.server.level.ServerPlayer;


/**
 * Client → Server: Request aggregated local shop data (owners/franchises with departments+listings).
 * If ownerUuid is non-null, requests the detail view for that specific owner.
 */
public record C2SFetchLocalShopsPacket(String ownerFilter) implements CustomPacketPayload {
    public static final Type<C2SFetchLocalShopsPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2sfetchlocalshopspacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SFetchLocalShopsPacket> STREAM_CODEC = StreamCodec.ofMember(C2SFetchLocalShopsPacket::encode, C2SFetchLocalShopsPacket::decode);

    @Override
    public Type<C2SFetchLocalShopsPacket> type() {
        return TYPE;
    }


    public static void encode(C2SFetchLocalShopsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.ownerFilter, 128);
    }

    public static C2SFetchLocalShopsPacket decode(FriendlyByteBuf buffer) {
        return new C2SFetchLocalShopsPacket(buffer.readUtf(128));
    }

    public static void handle(C2SFetchLocalShopsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null && player.getServer() != null) {
                LocalShopAggregator.sendLocalShops(player, packet.ownerFilter);
            }
        });
    }
}
