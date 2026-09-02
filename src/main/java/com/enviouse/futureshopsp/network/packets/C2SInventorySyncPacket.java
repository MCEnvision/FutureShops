package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.server.shop.InventorySyncService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.server.level.ServerPlayer;


/** Client requests a fresh authoritative owned-item count sync for the active shop. */
public record C2SInventorySyncPacket(String shopId) implements CustomPacketPayload {
    public static final Type<C2SInventorySyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2sinventorysyncpacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SInventorySyncPacket> STREAM_CODEC = StreamCodec.ofMember(C2SInventorySyncPacket::encode, C2SInventorySyncPacket::decode);

    @Override
    public Type<C2SInventorySyncPacket> type() {
        return TYPE;
    }

    public static void encode(C2SInventorySyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId, 128);
    }

    public static C2SInventorySyncPacket decode(FriendlyByteBuf buffer) {
        return new C2SInventorySyncPacket(buffer.readUtf(128));
    }

    public static void handle(C2SInventorySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                InventorySyncService.sendOwnedCounts(player, packet.shopId());
            }
        });
    }
}
