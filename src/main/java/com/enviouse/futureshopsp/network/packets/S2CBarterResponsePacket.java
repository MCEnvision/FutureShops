package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.client.ShopClientPacketHandler;
import com.enviouse.futureshopsp.server.shop.ShopResultCode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;


/** Server → client response for a barter request. */
public record S2CBarterResponsePacket(
        boolean success,
        String shopId,
        String recipeId,
        ShopResultCode errorCode,
        int multiplier,
        int outputQuantity) implements CustomPacketPayload {
    public static final Type<S2CBarterResponsePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "s2cbarterresponsepacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CBarterResponsePacket> STREAM_CODEC = StreamCodec.ofMember(S2CBarterResponsePacket::encode, S2CBarterResponsePacket::decode);

    @Override
    public Type<S2CBarterResponsePacket> type() {
        return TYPE;
    }


    public static void encode(S2CBarterResponsePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.success);
        buffer.writeUtf(packet.shopId);
        buffer.writeUtf(packet.recipeId);
        // Serialize by name for forward/backward tolerance against enum reorderings.
        buffer.writeUtf(packet.errorCode.name());
        buffer.writeVarInt(packet.multiplier);
        buffer.writeVarInt(packet.outputQuantity);
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
        return new S2CBarterResponsePacket(success, shopId, recipeId, code, multiplier, outputQuantity);
    }

    public static void handle(S2CBarterResponsePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ShopClientPacketHandler.handleBarterResponse(packet));
    }
}

