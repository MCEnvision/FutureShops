package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;

import java.util.LinkedHashMap;
import java.util.Map;

/** Server → client owned-item counts for shop-relevant items. */
public record S2CInventorySyncPacket(String shopId, Map<String, Integer> itemCounts) implements CustomPacketPayload {
    public static final Type<S2CInventorySyncPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "s2cinventorysyncpacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CInventorySyncPacket> STREAM_CODEC = StreamCodec.ofMember(S2CInventorySyncPacket::encode, S2CInventorySyncPacket::decode);

    @Override
    public Type<S2CInventorySyncPacket> type() {
        return TYPE;
    }

    public static void encode(S2CInventorySyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
        buffer.writeMap(packet.itemCounts, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
    }

    public static S2CInventorySyncPacket decode(FriendlyByteBuf buffer) {
        return new S2CInventorySyncPacket(
                buffer.readUtf(),
                buffer.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarInt));
    }

    public static void handle(S2CInventorySyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ShopClientPacketHandler.handleInventorySync(new S2CInventorySyncPacket(
                        packet.shopId(), new LinkedHashMap<>(packet.itemCounts()))));
    }
}

