package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.CartVerificationService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client → Server: Pre-checkout cart verification request.
 * Sends the client's cart snapshot so the server can check each entry
 * against the current listing state and report any changes (NBT disabled,
 * price changed, out of stock, listing removed, etc.).
 */
public record C2SVerifyCartPacket(List<CartLine> lines) {
    private static final int MAX_ITEM_ID_LENGTH = 256;
    private static final int MAX_TRADE_MODE_LENGTH = 32;
    private static final int MAX_NBT_LENGTH = 65_536;

    public record CartLine(BlockPos shopPos, int listingIndex, int quantity,
                           String expectedItemId, long expectedPriceMinor,
                           boolean expectedNbtAware, String expectedTradeMode,
                           String expectedNbtJson) {

        public static void encode(FriendlyByteBuf buffer, CartLine line) {
            buffer.writeBlockPos(line.shopPos);
            buffer.writeVarInt(line.listingIndex);
            buffer.writeVarInt(line.quantity);
            buffer.writeUtf(line.expectedItemId, MAX_ITEM_ID_LENGTH);
            buffer.writeLong(line.expectedPriceMinor);
            buffer.writeBoolean(line.expectedNbtAware);
            buffer.writeUtf(line.expectedTradeMode, MAX_TRADE_MODE_LENGTH);
            // Protocol 25: SNBT snapshot of the listing tag at add-to-cart time
            // (blank = no NBT). Appended last to keep prior field order intact.
            buffer.writeUtf(line.expectedNbtJson == null ? "" : line.expectedNbtJson,
                    MAX_NBT_LENGTH);
        }

        public static CartLine decode(FriendlyByteBuf buffer) {
            return new CartLine(
                    buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readUtf(MAX_ITEM_ID_LENGTH), buffer.readLong(),
                    buffer.readBoolean(), buffer.readUtf(MAX_TRADE_MODE_LENGTH),
                    buffer.readUtf(MAX_NBT_LENGTH));
        }
    }

    public static void encode(C2SVerifyCartPacket packet, FriendlyByteBuf buffer) {
        buffer.writeCollection(packet.lines, CartLine::encode);
    }

    private static final int MAX_LINES = 256;

    public static C2SVerifyCartPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_LINES) {
            throw new io.netty.handler.codec.DecoderException(
                    "C2SVerifyCartPacket lines out of range: " + count);
        }
        java.util.List<CartLine> lines = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(CartLine.decode(buffer));
        }
        return new C2SVerifyCartPacket(lines);
    }

    public static void handle(C2SVerifyCartPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                CartVerificationService.verify(player, packet.lines);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
