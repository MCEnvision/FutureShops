package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.server.shop.PlayerShopBlockService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.server.level.ServerPlayer;


/**
 * LGB#4: Added paymentMethod field so the client can tell the server
 * whether to use MONEY or BARTER for BOTH-mode trades.
 * Empty string = server auto-detects (legacy behaviour).
 */
public record C2SPlayerShopBuyPacket(BlockPos shopPos, int listingIndex, int quantity, String paymentMethod) implements CustomPacketPayload {
    public static final Type<C2SPlayerShopBuyPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2splayershopbuypacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SPlayerShopBuyPacket> STREAM_CODEC = StreamCodec.ofMember(C2SPlayerShopBuyPacket::encode, C2SPlayerShopBuyPacket::decode);

    @Override
    public Type<C2SPlayerShopBuyPacket> type() {
        return TYPE;
    }


    /**
     * Legacy constructor for callers that don't specify payment method.
     * @deprecated BOTH-mode listings require an explicit "MONEY" or "BARTER" tag;
     *             prefer the 4-arg constructor so the server-side guard in
     *             {@code PlayerShopBlockService.buy} does not reject the request.
     */
    @Deprecated
    public C2SPlayerShopBuyPacket(BlockPos shopPos, int listingIndex, int quantity) {
        this(shopPos, listingIndex, quantity, "");
    }

    public static void encode(C2SPlayerShopBuyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeVarInt(packet.listingIndex());
        buffer.writeVarInt(packet.quantity());
        buffer.writeUtf(packet.paymentMethod());
    }

    public static C2SPlayerShopBuyPacket decode(FriendlyByteBuf buffer) {
        return new C2SPlayerShopBuyPacket(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(32));
    }

    public static void handle(C2SPlayerShopBuyPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                PlayerShopBlockService.buy(player, packet.shopPos(), packet.listingIndex(), packet.quantity(), packet.paymentMethod());
            }
        });
    }
}
