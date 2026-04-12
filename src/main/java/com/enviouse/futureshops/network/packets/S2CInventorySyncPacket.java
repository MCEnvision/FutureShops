package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Server → client owned-item counts for shop-relevant items. */
public record S2CInventorySyncPacket(String shopId, Map<String, Integer> itemCounts) {
    public static void encode(S2CInventorySyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
        buffer.writeMap(packet.itemCounts, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
    }

    public static S2CInventorySyncPacket decode(FriendlyByteBuf buffer) {
        return new S2CInventorySyncPacket(
                buffer.readUtf(),
                buffer.readMap(FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarInt));
    }

    public static void handle(S2CInventorySyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleInventorySync(new S2CInventorySyncPacket(
                        packet.shopId(), new LinkedHashMap<>(packet.itemCounts())))));
        context.setPacketHandled(true);
    }
}

