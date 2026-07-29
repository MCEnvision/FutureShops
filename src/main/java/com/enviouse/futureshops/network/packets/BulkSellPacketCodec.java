package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.data.BulkSellQuote;
import com.enviouse.futureshops.data.BulkSellTarget;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class BulkSellPacketCodec {
    private static final int MAX_NBT_LENGTH = 65_536;

    private BulkSellPacketCodec() {
    }

    static void encodeQuote(
            FriendlyByteBuf buffer,
            BulkSellQuote quote
    ) {
        buffer.writeUUID(quote.quoteId());
        buffer.writeUtf(quote.target().name(), 32);
        buffer.writeUtf(quote.shopId(), BulkSellQuote.MAX_TEXT_LENGTH);
        buffer.writeLong(quote.expiresAtEpochMillis());
        buffer.writeUtf(quote.currencyName(),
                BulkSellQuote.MAX_TEXT_LENGTH);
        buffer.writeVarInt(quote.currencyDecimals());
        buffer.writeBoolean(quote.selectEligibleByDefault());
        buffer.writeVarInt(quote.lines().size());
        for (BulkSellQuote.Line line : quote.lines()) {
            buffer.writeUtf(line.lineId(),
                    BulkSellQuote.MAX_TEXT_LENGTH);
            buffer.writeUtf(line.destination(),
                    BulkSellQuote.MAX_TEXT_LENGTH);
            buffer.writeVarInt(line.inputs().size());
            for (BulkSellQuote.Component component : line.inputs()) {
                buffer.writeUtf(component.itemId(),
                        BulkSellQuote.MAX_TEXT_LENGTH);
                buffer.writeVarInt(component.count());
                buffer.writeUtf(component.exactNbt(), MAX_NBT_LENGTH);
            }
            buffer.writeVarInt(line.quantity());
            buffer.writeVarLong(line.unitPayoutMinorUnits());
            buffer.writeVarLong(line.totalPayoutMinorUnits());
            buffer.writeBoolean(line.eligible());
            buffer.writeUtf(line.reasonKey(),
                    BulkSellQuote.MAX_TEXT_LENGTH);
        }
    }

    static BulkSellQuote decodeQuote(FriendlyByteBuf buffer) {
        try {
            java.util.UUID quoteId = buffer.readUUID();
            BulkSellTarget target = BulkSellTarget.valueOf(
                    buffer.readUtf(32).toUpperCase(Locale.ROOT));
            String shopId = buffer.readUtf(
                    BulkSellQuote.MAX_TEXT_LENGTH);
            long expiresAt = buffer.readLong();
            String currency = buffer.readUtf(
                    BulkSellQuote.MAX_TEXT_LENGTH);
            int decimals = buffer.readVarInt();
            boolean selectEligible = buffer.readBoolean();
            int lineCount = boundedCount(
                    buffer.readVarInt(), BulkSellQuote.MAX_LINES,
                    "bulk sell quote lines");
            List<BulkSellQuote.Line> lines =
                    new ArrayList<>(lineCount);
            for (int index = 0; index < lineCount; index++) {
                String lineId = buffer.readUtf(
                        BulkSellQuote.MAX_TEXT_LENGTH);
                String destination = buffer.readUtf(
                        BulkSellQuote.MAX_TEXT_LENGTH);
                int componentCount = boundedCount(
                        buffer.readVarInt(),
                        BulkSellQuote.MAX_COMPONENTS,
                        "bulk sell quote components");
                List<BulkSellQuote.Component> components =
                        new ArrayList<>(componentCount);
                for (int componentIndex = 0;
                     componentIndex < componentCount;
                     componentIndex++) {
                    components.add(new BulkSellQuote.Component(
                            buffer.readUtf(
                                    BulkSellQuote.MAX_TEXT_LENGTH),
                            buffer.readVarInt(),
                            buffer.readUtf(MAX_NBT_LENGTH)));
                }
                lines.add(new BulkSellQuote.Line(
                        lineId, destination, components,
                        buffer.readVarInt(), buffer.readVarLong(),
                        buffer.readVarLong(), buffer.readBoolean(),
                        buffer.readUtf(
                                BulkSellQuote.MAX_TEXT_LENGTH)));
            }
            return new BulkSellQuote(
                    quoteId, target, shopId, expiresAt,
                    currency, decimals, selectEligible, lines);
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Bulk sell quote is malformed", exception);
        }
    }

    static int boundedCount(
            int value,
            int maximum,
            String field
    ) {
        if (value < 0 || value > maximum) {
            throw new DecoderException(field + " are invalid");
        }
        return value;
    }

    static void requireComplete(
            FriendlyByteBuf buffer,
            String packet
    ) {
        if (buffer.isReadable()) {
            throw new DecoderException(
                    packet + " has trailing data");
        }
    }
}
