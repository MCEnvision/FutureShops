package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.server.transaction.ShopBarterService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.server.level.ServerPlayer;


/** Client → server barter request from ItemDetailScreen / BarterScreen. */
public record C2SBarterRequestPacket(String shopId, String recipeId, int multiplier) implements CustomPacketPayload {
    public static final Type<C2SBarterRequestPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2sbarterrequestpacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SBarterRequestPacket> STREAM_CODEC = StreamCodec.ofMember(C2SBarterRequestPacket::encode, C2SBarterRequestPacket::decode);

    @Override
    public Type<C2SBarterRequestPacket> type() {
        return TYPE;
    }

    public static void encode(C2SBarterRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
        buffer.writeUtf(packet.recipeId);
        buffer.writeVarInt(packet.multiplier);
    }

    public static C2SBarterRequestPacket decode(FriendlyByteBuf buffer) {
        return new C2SBarterRequestPacket(buffer.readUtf(), buffer.readUtf(), buffer.readVarInt());
    }

    public static void handle(C2SBarterRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                ShopBarterService.handleBarterRequest(player, packet);
            }
        });
    }
}

