package com.enviouse.futureshopsp.network.packets;

import com.enviouse.futureshopsp.server.shop.CartVerificationService;
import net.minecraft.core.BlockPos;
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
 * Client → Server: Pre-checkout cart verification request.
 * Sends the client's cart snapshot so the server can check each entry
 * against the current listing state and report any changes (NBT disabled,
 * price changed, out of stock, listing removed, etc.).
 */
public record C2SVerifyCartPacket(List<CartLine> lines) implements CustomPacketPayload {
    public static final Type<C2SVerifyCartPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Futureshops.MODID, "c2sverifycartpacket"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SVerifyCartPacket> STREAM_CODEC = StreamCodec.ofMember(C2SVerifyCartPacket::encode, C2SVerifyCartPacket::decode);

    @Override
    public Type<C2SVerifyCartPacket> type() {
        return TYPE;
    }


    public record CartLine(BlockPos shopPos, int listingIndex, int quantity,
                           String expectedItemId, long expectedPriceMinor,
                           boolean expectedNbtAware, String expectedTradeMode) {

        public static void encode(FriendlyByteBuf buffer, CartLine line) {
            buffer.writeBlockPos(line.shopPos);
            buffer.writeVarInt(line.listingIndex);
            buffer.writeVarInt(line.quantity);
            buffer.writeUtf(line.expectedItemId, 256);
            buffer.writeLong(line.expectedPriceMinor);
            buffer.writeBoolean(line.expectedNbtAware);
            buffer.writeUtf(line.expectedTradeMode, 32);
        }

        public static CartLine decode(FriendlyByteBuf buffer) {
            return new CartLine(
                    buffer.readBlockPos(), buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readUtf(256), buffer.readLong(), buffer.readBoolean(), buffer.readUtf(32));
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

    public static void handle(C2SVerifyCartPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player != null) {
                CartVerificationService.verify(player, packet.lines);
            }
        });
    }
}
