package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.transaction.ShopBuyService;
import com.enviouse.futureshops.money.PaymentSource;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client → server buy request.
 * Supports both single-item buys (detail screen) and cart checkout.
 */
public record C2SBuyRequestPacket(String shopId, boolean cartCheckout, List<LineItem> lineItems,
                                  String paymentSource) {

    /**
     * One buy line. {@code listingId} is the catalog resolution key (== registry itemId for legacy
     * single-variant listings, a distinct stable id for multi-variant NBT listings) — the server
     * resolves the exact {@code ItemDef} by it, then mints from that def's registry itemId + NBT.
     */
    public record LineItem(String listingId, int quantity) {
        public static void encode(FriendlyByteBuf buffer, LineItem lineItem) {
            buffer.writeUtf(lineItem.listingId);
            buffer.writeVarInt(lineItem.quantity);
        }

        public static LineItem decode(FriendlyByteBuf buffer) {
            return new LineItem(buffer.readUtf(), buffer.readVarInt());
        }
    }

    public static C2SBuyRequestPacket single(String shopId, String listingId, int quantity,
                                             PaymentSource paymentSource) {
        return new C2SBuyRequestPacket(shopId, false, List.of(new LineItem(listingId, quantity)),
                paymentSource.wire());
    }

    public static C2SBuyRequestPacket cart(String shopId, List<LineItem> lineItems,
                                           PaymentSource paymentSource) {
        return new C2SBuyRequestPacket(shopId, true, List.copyOf(lineItems), paymentSource.wire());
    }

    public static void encode(C2SBuyRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId);
        buffer.writeBoolean(packet.cartCheckout);
        buffer.writeCollection(packet.lineItems, LineItem::encode);
        buffer.writeUtf(packet.paymentSource);
    }

    /** Hard cap on cart line count. A real cart never exceeds a few dozen;
     *  bounds a malicious varInt to a finite allocation. */
    private static final int MAX_LINES = 256;

    public static C2SBuyRequestPacket decode(FriendlyByteBuf buffer) {
        String shopId = buffer.readUtf();
        boolean cartCheckout = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_LINES) {
            throw new io.netty.handler.codec.DecoderException(
                    "C2SBuyRequestPacket lines out of range: " + count);
        }
        java.util.List<LineItem> lines = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(LineItem.decode(buffer));
        }
        return new C2SBuyRequestPacket(shopId, cartCheckout, lines, buffer.readUtf());
    }

    public static void handle(C2SBuyRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ShopBuyService.handleBuyRequest(player, packet);
            }
        });
        context.setPacketHandled(true);
    }
}
