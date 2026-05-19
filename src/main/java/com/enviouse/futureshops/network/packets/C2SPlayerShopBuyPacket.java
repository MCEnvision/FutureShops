package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.PlayerShopBlockService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * LGB#4: Added paymentMethod field so the client can tell the server
 * whether to use MONEY or BARTER for BOTH-mode trades.
 * Empty string = server auto-detects (legacy behaviour).
 */
public record C2SPlayerShopBuyPacket(BlockPos shopPos, int listingIndex, int quantity, String paymentMethod) {

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
        return new C2SPlayerShopBuyPacket(buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf());
    }

    public static void handle(C2SPlayerShopBuyPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                PlayerShopBlockService.buy(player, packet.shopPos(), packet.listingIndex(), packet.quantity(), packet.paymentMethod());
            }
        });
        context.setPacketHandled(true);
    }
}

