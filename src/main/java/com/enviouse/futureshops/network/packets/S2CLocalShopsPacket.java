package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.data.LocalShopOwnerEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Server → Client: Aggregated local shops data.
 * Contains a list of nearby owners/franchises with their departments and listings.
 */
public record S2CLocalShopsPacket(List<LocalShopOwnerEntry> owners) {

    public static void encode(S2CLocalShopsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeCollection(packet.owners, LocalShopOwnerEntry::encode);
    }

    public static S2CLocalShopsPacket decode(FriendlyByteBuf buffer) {
        return new S2CLocalShopsPacket(buffer.readList(LocalShopOwnerEntry::decode));
    }

    public static void handle(S2CLocalShopsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleLocalShops(packet)));
        ctx.get().setPacketHandled(true);
    }
}

