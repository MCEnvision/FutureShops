package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client response for a sell request. */
public record S2CSellResponsePacket(
        boolean success,
        String shopId,
        String itemId,
        String errorCode,
        long resultingBalanceMinorUnits,
        int quantity,
        long totalMinorUnits) {

    public static void encode(S2CSellResponsePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.shopId);
        buffer.writeUtf(packet.itemId);
        buffer.writeUtf(packet.errorCode);
        buffer.writeLong(packet.resultingBalanceMinorUnits);
        buffer.writeVarInt(packet.quantity);
        buffer.writeLong(packet.totalMinorUnits);
    }

    public static S2CSellResponsePacket decode(FriendlyByteBuf buffer) {
        return new S2CSellResponsePacket(
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readLong(),
                buffer.readVarInt(),
                buffer.readLong());
    }

    public static void handle(S2CSellResponsePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleSellResponse(packet)));
        context.setPacketHandled(true);
    }
}

