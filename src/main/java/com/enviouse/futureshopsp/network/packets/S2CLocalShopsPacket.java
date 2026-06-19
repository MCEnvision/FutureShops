package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.client.ShopClientPacketHandler;
import com.enviouse.futureshopsp.data.LocalShopOwnerEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;

import java.util.List;

/**
 * Server → Client: Aggregated local shops data.
 * Contains a list of nearby owners/franchises with their departments and listings.
 */
public record S2CLocalShopsPacket(List<LocalShopOwnerEntry> owners) implements CustomPacketPayload {
    public static final Type<S2CLocalShopsPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "s2clocalshopspacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CLocalShopsPacket> STREAM_CODEC = StreamCodec.ofMember(S2CLocalShopsPacket::encode, S2CLocalShopsPacket::decode);

    @Override
    public Type<S2CLocalShopsPacket> type() {
        return TYPE;
    }


    public static void encode(S2CLocalShopsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeCollection(packet.owners, LocalShopOwnerEntry::encode);
    }

    public static S2CLocalShopsPacket decode(FriendlyByteBuf buffer) {
        return new S2CLocalShopsPacket(buffer.readList(LocalShopOwnerEntry::decode));
    }

    public static void handle(S2CLocalShopsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ShopClientPacketHandler.handleLocalShops(packet));
    }
}

