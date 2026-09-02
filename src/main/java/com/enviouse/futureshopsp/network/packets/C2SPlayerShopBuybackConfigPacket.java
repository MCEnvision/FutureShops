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
 * Owner → server: configure a listing's buyback (sell-to-shop) parameters.
 *
 * @param direction "SELL", "BUY" or "BOTH"
 * @param buybackPriceMinor per-unit price the shop pays when buying from a player
 * @param buybackCap maximum units the shop will buy (0 = unlimited)
 */
public record C2SPlayerShopBuybackConfigPacket(BlockPos shopPos, int listingIndex,
                                               String direction, long buybackPriceMinor, int buybackCap) implements CustomPacketPayload {
    public static final Type<C2SPlayerShopBuybackConfigPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2splayershopbuybackconfigpacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SPlayerShopBuybackConfigPacket> STREAM_CODEC = StreamCodec.ofMember(C2SPlayerShopBuybackConfigPacket::encode, C2SPlayerShopBuybackConfigPacket::decode);

    @Override
    public Type<C2SPlayerShopBuybackConfigPacket> type() {
        return TYPE;
    }

    public static void encode(C2SPlayerShopBuybackConfigPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.shopPos());
        buffer.writeVarInt(packet.listingIndex());
        buffer.writeUtf(packet.direction(), 16);
        buffer.writeLong(packet.buybackPriceMinor());
        buffer.writeVarInt(packet.buybackCap());
    }

    public static C2SPlayerShopBuybackConfigPacket decode(FriendlyByteBuf buffer) {
        return new C2SPlayerShopBuybackConfigPacket(buffer.readBlockPos(), buffer.readVarInt(),
                buffer.readUtf(16), buffer.readLong(), buffer.readVarInt());
    }

    public static void handle(C2SPlayerShopBuybackConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                PlayerShopBlockService.applyBuybackConfig(player, packet);
            }
        });
    }
}
