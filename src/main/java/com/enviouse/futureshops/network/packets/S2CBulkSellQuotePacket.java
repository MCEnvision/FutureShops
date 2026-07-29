package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.client.ShopClientPacketHandler;
import com.enviouse.futureshops.data.BulkSellQuote;
import com.enviouse.futureshops.server.shop.BulkSellService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

public record S2CBulkSellQuotePacket(
        BulkSellService.Status status,
        @Nullable BulkSellQuote quote
) {
    public S2CBulkSellQuotePacket {
        status = Objects.requireNonNull(status, "status");
        if (status.success() != (quote != null)) {
            throw new IllegalArgumentException(
                    "Bulk sell quote response is invalid");
        }
    }

    public static S2CBulkSellQuotePacket from(
            BulkSellService.QuoteResult result
    ) {
        return new S2CBulkSellQuotePacket(
                result.status(), result.quote());
    }

    public static void encode(
            S2CBulkSellQuotePacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeUtf(packet.status.name(), 48);
        buffer.writeBoolean(packet.quote != null);
        if (packet.quote != null) {
            BulkSellPacketCodec.encodeQuote(buffer, packet.quote);
        }
    }

    public static S2CBulkSellQuotePacket decode(
            FriendlyByteBuf buffer
    ) {
        try {
            BulkSellService.Status status =
                    BulkSellService.Status.valueOf(
                            buffer.readUtf(48)
                                    .toUpperCase(Locale.ROOT));
            BulkSellQuote quote = buffer.readBoolean()
                    ? BulkSellPacketCodec.decodeQuote(buffer) : null;
            S2CBulkSellQuotePacket packet =
                    new S2CBulkSellQuotePacket(status, quote);
            BulkSellPacketCodec.requireComplete(
                    buffer, "Bulk sell quote response");
            return packet;
        } catch (RuntimeException exception) {
            throw new DecoderException(
                    "Bulk sell quote response is malformed",
                    exception);
        }
    }

    public static void handle(
            S2CBulkSellQuotePacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ShopClientPacketHandler
                                .handleBulkSellQuote(packet)));
        context.setPacketHandled(true);
    }
}
