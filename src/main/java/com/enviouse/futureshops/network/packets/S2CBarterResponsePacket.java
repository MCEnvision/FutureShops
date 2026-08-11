package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.server.shop.ShopResultCode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Server → client response for a barter request. */
public record S2CBarterResponsePacket(
        boolean success,
        String shopId,
        String recipeId,
        ShopResultCode errorCode,
        int multiplier,
        int outputQuantity,
        UUID requestId) {

    public S2CBarterResponsePacket {
        requestId = Objects.requireNonNull(requestId, "requestId");
        if (requestId.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException(
                    "Barter response identity is invalid");
        }
    }

    public static void encode(S2CBarterResponsePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.shopId);
        buffer.writeUtf(packet.recipeId);
        // Serialize by name for forward/backward tolerance against enum reorderings.
        buffer.writeUtf(packet.errorCode.name());
        buffer.writeVarInt(packet.multiplier);
        buffer.writeVarInt(packet.outputQuantity);
        buffer.writeUUID(packet.requestId);
    }

    public static S2CBarterResponsePacket decode(FriendlyByteBuf buffer) {
        boolean success = buffer.readBoolean();
        String shopId = buffer.readUtf();
        String recipeId = buffer.readUtf();
        String rawCode = buffer.readUtf();
        ShopResultCode code;
        try {
            code = ShopResultCode.valueOf(rawCode);
        } catch (IllegalArgumentException ex) {
            code = ShopResultCode.SERVER_ERROR;
        }
        int multiplier = buffer.readVarInt();
        int outputQuantity = buffer.readVarInt();
        return new S2CBarterResponsePacket(success, shopId, recipeId,
                code, multiplier, outputQuantity, buffer.readUUID());
    }

    public static void handle(S2CBarterResponsePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShopClientPacketHandler.handleBarterResponse(packet)));
        context.setPacketHandled(true);
    }
}
