package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.server.shop.AdminCartVerificationService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.enviouse.futureshopsp.Futureshops;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Client → Server: Pre-checkout verification for admin shop cart.
 * Sends the client's cart snapshot (itemId + expectedPrice) so the server can
 * check against the current catalog state and report any changes.
 */
public record C2SVerifyAdminCartPacket(String shopId, List<AdminCartLine> lines) implements CustomPacketPayload {
    public static final Type<C2SVerifyAdminCartPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2sverifyadmincartpacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SVerifyAdminCartPacket> STREAM_CODEC = StreamCodec.ofMember(C2SVerifyAdminCartPacket::encode, C2SVerifyAdminCartPacket::decode);

    @Override
    public Type<C2SVerifyAdminCartPacket> type() {
        return TYPE;
    }


    /** One admin-cart line. {@code listingId} is the catalog resolution key (see C2SBuyRequestPacket.LineItem). */
    public record AdminCartLine(String listingId, int quantity, long expectedPriceMinor) {

        public static void encode(FriendlyByteBuf buffer, AdminCartLine line) {
            buffer.writeUtf(line.listingId, 256);
            buffer.writeVarInt(line.quantity);
            buffer.writeLong(line.expectedPriceMinor);
        }

        public static AdminCartLine decode(FriendlyByteBuf buffer) {
            return new AdminCartLine(buffer.readUtf(256), buffer.readVarInt(), buffer.readLong());
        }
    }

    public static void encode(C2SVerifyAdminCartPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.shopId, 128);
        buffer.writeCollection(packet.lines, AdminCartLine::encode);
    }

    private static final int MAX_LINES = 256;

    public static C2SVerifyAdminCartPacket decode(FriendlyByteBuf buffer) {
        String shopId = buffer.readUtf(128);
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

    public static void handle(C2SVerifyAdminCartPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                AdminCartVerificationService.verify(player, packet.shopId, packet.lines);
            }
        });
    }
}
