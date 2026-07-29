package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.server.shop.BulkSellService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record C2SBulkSellCancelPacket(UUID quoteId) {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    public C2SBulkSellCancelPacket {
        quoteId = Objects.requireNonNull(quoteId, "quoteId");
        if (ZERO_UUID.equals(quoteId)) {
            throw new IllegalArgumentException(
                    "Bulk sell cancellation is invalid");
        }
    }

    public static void encode(
            C2SBulkSellCancelPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUUID(packet.quoteId);
    }

    public static C2SBulkSellCancelPacket decode(
        FriendlyByteBuf buffer
    ) {
        try {
            C2SBulkSellCancelPacket packet =
                    new C2SBulkSellCancelPacket(buffer.readUUID());
            BulkSellPacketCodec.requireComplete(
                    buffer, "Bulk sell cancellation");
            return packet;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Bulk sell cancellation is malformed",
                    exception);
        }
    }

    public static void handle(
            C2SBulkSellCancelPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                BulkSellService.cancel(player, packet.quoteId);
            }
        });
        context.setPacketHandled(true);
    }
}
