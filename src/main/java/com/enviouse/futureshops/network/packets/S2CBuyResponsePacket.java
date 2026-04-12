package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client response for a buy request.
 * The authoritative post-transaction catalog refresh still arrives via S2CShopDataPacket.
 */
public record S2CBuyResponsePacket(
        boolean success,
        boolean cartCheckout,
        String shopId,
        String errorCode,
        long resultingBalanceMinorUnits,
        int totalQuantity,
        long totalMinorUnits) {

    public static void encode(S2CBuyResponsePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success);
        buffer.writeBoolean(packet.cartCheckout);
        buffer.writeUtf(packet.shopId);
        buffer.writeUtf(packet.errorCode);
        buffer.writeLong(packet.resultingBalanceMinorUnits);
        buffer.writeVarInt(packet.totalQuantity);
        buffer.writeLong(packet.totalMinorUnits);
    }

    public static S2CBuyResponsePacket decode(FriendlyByteBuf buffer) {
        return new S2CBuyResponsePacket(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readVarInt(),
                buffer.readLong());
    }

    public static void handle(S2CBuyResponsePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleBuyResponse(packet)));
        context.setPacketHandled(true);
    }
}


