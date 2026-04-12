package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client response for a barter request. */
public record S2CBarterResponsePacket(
        boolean success,
        String shopId,
        String recipeId,
        String errorCode,
        int multiplier,
        int outputQuantity) {

    public static void encode(S2CBarterResponsePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.shopId);
        buffer.writeUtf(packet.recipeId);
        buffer.writeUtf(packet.errorCode);
        buffer.writeVarInt(packet.multiplier);
        buffer.writeVarInt(packet.outputQuantity);
    }

    public static S2CBarterResponsePacket decode(FriendlyByteBuf buffer) {
        return new S2CBarterResponsePacket(
                buffer.readBoolean(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readUtf(),
                buffer.readVarInt(),
                buffer.readVarInt());
    }

    public static void handle(S2CBarterResponsePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleBarterResponse(packet)));
        context.setPacketHandled(true);
    }
}

