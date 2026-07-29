package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.data.BulkSellQuote;
import com.enviouse.futureshops.server.shop.BulkSellService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record S2CBulkSellResultPacket(
        UUID quoteId,
        BulkSellService.Status status,
        int soldLines,
        int failedLines,
        int recoveryLines,
        long paidMinorUnits,
        String currencyName,
        int currencyDecimals,
        boolean replayed
) {
    public S2CBulkSellResultPacket {
        quoteId = Objects.requireNonNull(quoteId, "quoteId");
        status = Objects.requireNonNull(status, "status");
        currencyName = Objects.requireNonNull(
                currencyName, "currencyName").strip();
        if (soldLines < 0 || failedLines < 0
                || recoveryLines < 0 || paidMinorUnits < 0L
                || currencyName.isEmpty()
                || currencyName.length()
                > BulkSellQuote.MAX_TEXT_LENGTH
                || currencyDecimals < 0 || currencyDecimals > 6
                || !validShape(
                status, soldLines, failedLines,
                recoveryLines, paidMinorUnits)) {
            throw new IllegalArgumentException(
                    "Bulk sell result packet is invalid");
        }
    }

    public static S2CBulkSellResultPacket from(
            BulkSellService.CommitResult result,
            String currencyName,
            int currencyDecimals
    ) {
        return new S2CBulkSellResultPacket(
                result.quoteId(), result.status(),
                result.soldLines(), result.failedLines(),
                result.recoveryLines(), result.paidMinorUnits(),
                currencyName, currencyDecimals, result.replayed());
    }

    private static boolean validShape(
            BulkSellService.Status status,
            int sold,
            int failed,
            int recovery,
            long paid
    ) {
        if ((sold > 0) != (paid > 0L)) {
            return false;
        }
        return switch (status) {
            case SUCCESS -> sold > 0
                    && failed == 0 && recovery == 0;
            case PARTIAL -> sold > 0
                    && failed > 0 && recovery == 0;
            case REJECTED -> sold == 0
                    && failed > 0 && recovery == 0;
            case RECOVERY_REQUIRED -> recovery > 0;
            default -> sold == 0
                    && failed == 0 && recovery == 0
                    && paid == 0L;
        };
    }

    public static void encode(
            S2CBulkSellResultPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUUID(packet.quoteId);
        buffer.writeUtf(packet.status.name(), 48);
        buffer.writeVarInt(packet.soldLines);
        buffer.writeVarInt(packet.failedLines);
        buffer.writeVarInt(packet.recoveryLines);
        buffer.writeVarLong(packet.paidMinorUnits);
        buffer.writeUtf(packet.currencyName,
                BulkSellQuote.MAX_TEXT_LENGTH);
        buffer.writeVarInt(packet.currencyDecimals);
        buffer.writeBoolean(packet.replayed);
    }

    public static S2CBulkSellResultPacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            S2CBulkSellResultPacket packet =
                    new S2CBulkSellResultPacket(
                    buffer.readUUID(),
                    BulkSellService.Status.valueOf(
                            buffer.readUtf(48)
                                    .toUpperCase(Locale.ROOT)),
                    buffer.readVarInt(), buffer.readVarInt(),
                    buffer.readVarInt(), buffer.readVarLong(),
                    buffer.readUtf(BulkSellQuote.MAX_TEXT_LENGTH),
                    buffer.readVarInt(), buffer.readBoolean());
            BulkSellPacketCodec.requireComplete(
                    buffer, "Bulk sell result");
            return packet;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Bulk sell result is malformed", exception);
        }
    }

    public static void handle(
            S2CBulkSellResultPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ShopClientPacketHandler
                                .handleBulkSellResult(packet)));
        context.setPacketHandled(true);
    }
}
