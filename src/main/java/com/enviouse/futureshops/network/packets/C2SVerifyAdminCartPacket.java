package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.AdminCartVerificationService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client → Server: Pre-checkout verification for admin shop cart.
 * Sends the client's cart snapshot (itemId + expectedPrice) so the server can
 * check against the current catalog state and report any changes.
 */
public record C2SVerifyAdminCartPacket(String shopId, List<AdminCartLine> lines) {
    private static final int MAX_IDENTIFIER_LENGTH = 128;
    private static final int MAX_NBT_LENGTH = 65_536;

    /**
     * One admin-cart line. {@code listingId} is the catalog resolution key (see
     * C2SBuyRequestPacket.LineItem). {@code expectedNbtJson} is the listing tag the
     * buyer saw at add-to-cart time (v25, trailing; blank = no NBT) so the server
     * can warn when an operator swapped the variant before checkout.
     */
    public record AdminCartLine(String listingId, int quantity, long expectedPriceMinor, String expectedNbtJson) {

        public static void encode(FriendlyByteBuf buffer, AdminCartLine line) {
            buffer.writeUtf(line.listingId, MAX_IDENTIFIER_LENGTH);
            buffer.writeVarInt(line.quantity);
            buffer.writeLong(line.expectedPriceMinor);
            buffer.writeUtf(line.expectedNbtJson == null ? "" : line.expectedNbtJson,
                    MAX_NBT_LENGTH);
        }

        public static AdminCartLine decode(FriendlyByteBuf buffer) {
            return new AdminCartLine(buffer.readUtf(MAX_IDENTIFIER_LENGTH),
                    buffer.readVarInt(), buffer.readLong(),
                    buffer.readUtf(MAX_NBT_LENGTH));
        }
    }

    public static void encode(C2SVerifyAdminCartPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId, MAX_IDENTIFIER_LENGTH);
        buffer.writeCollection(packet.lines, AdminCartLine::encode);
    }

    private static final int MAX_LINES = 256;

    public static C2SVerifyAdminCartPacket decode(FriendlyByteBuf buffer) {
        String shopId = buffer.readUtf(MAX_IDENTIFIER_LENGTH);
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_LINES) {
            throw new io.netty.handler.codec.DecoderException(
                    "C2SVerifyAdminCartPacket lines out of range: " + count);
        }
        java.util.List<AdminCartLine> lines = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(AdminCartLine.decode(buffer));
        }
        return new C2SVerifyAdminCartPacket(shopId, lines);
    }

    public static void handle(C2SVerifyAdminCartPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                AdminCartVerificationService.verify(player, packet.shopId, packet.lines);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
