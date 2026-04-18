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

    public record CartLine(BlockPos shopPos, int listingIndex, int quantity,
                           String expectedItemId, long expectedPriceMinor,
                           boolean expectedNbtAware, String expectedTradeMode) {

        public static void encode(FriendlyByteBuf buffer, CartLine line) {
            buffer.writeBlockPos(line.shopPos);
            buffer.writeVarInt(line.listingIndex);
            buffer.writeVarInt(line.quantity);
            buffer.writeUtf(line.expectedItemId);
            buffer.writeLong(line.expectedPriceMinor);
            buffer.writeBoolean(line.expectedNbtAware);
            buffer.writeUtf(line.expectedTradeMode);
        }

        public static CartLine decode(FriendlyByteBuf buffer) {
            return new CartLine(
                    buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readUtf(), buffer.readLong(), buffer.readBoolean(), buffer.readUtf());
        }
    }

    public static void encode(C2SVerifyCartPacket packet, FriendlyByteBuf buffer) {
        buffer.writeCollection(packet.lines, CartLine::encode);
    }

    public static C2SVerifyCartPacket decode(FriendlyByteBuf buffer) {
        return new C2SVerifyCartPacket(buffer.readList(CartLine::decode));
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

