package com.enviouse.futureshops.network.packets;

import com.enviouse.futureshops.data.BulkSellQuote;
import com.enviouse.futureshops.data.BulkSellTarget;
import com.enviouse.futureshops.server.shop.BulkSellService;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BulkSellPacketTest {
    @Test
    void everyBulkSellPacketRoundTripsExactly() {
        BulkSellQuote quote = quote();
        C2SBulkSellQuotePacket request =
                new C2SBulkSellQuotePacket(
                        BulkSellTarget.ADMIN_SHOP,
                        "default", true);
        assertRoundTrip(request,
                C2SBulkSellQuotePacket::encode,
                C2SBulkSellQuotePacket::decode);

        S2CBulkSellQuotePacket response =
                new S2CBulkSellQuotePacket(
                        BulkSellService.Status.SUCCESS, quote);
        assertRoundTrip(response,
                S2CBulkSellQuotePacket::encode,
                S2CBulkSellQuotePacket::decode);

        C2SBulkSellCommitPacket commit =
                new C2SBulkSellCommitPacket(
                        quote.quoteId(),
                        List.of(quote.lines().get(0).lineId()));
        assertRoundTrip(commit,
                C2SBulkSellCommitPacket::encode,
                C2SBulkSellCommitPacket::decode);

        C2SBulkSellCancelPacket cancel =
                new C2SBulkSellCancelPacket(quote.quoteId());
        assertRoundTrip(cancel,
                C2SBulkSellCancelPacket::encode,
                C2SBulkSellCancelPacket::decode);

        S2CBulkSellResultPacket result =
                new S2CBulkSellResultPacket(
                        quote.quoteId(),
                        BulkSellService.Status.PARTIAL,
                        1, 1, 0, 250L,
                        "Coins", 2, false);
        assertRoundTrip(result,
                S2CBulkSellResultPacket::encode,
                S2CBulkSellResultPacket::decode);
    }

    @Test
    void selectedLineCountAndTrailingDataAreRejected() {
        FriendlyByteBuf oversized = buffer();
        oversized.writeUUID(UUID.randomUUID());
        oversized.writeVarInt(BulkSellQuote.MAX_LINES + 1);
        assertThrows(DecoderException.class, () ->
                C2SBulkSellCommitPacket.decode(oversized));

        C2SBulkSellQuotePacket request =
                new C2SBulkSellQuotePacket(
                        BulkSellTarget.PLAYER_SHOPS,
                        "playershops", false);
        FriendlyByteBuf trailing = buffer();
        C2SBulkSellQuotePacket.encode(request, trailing);
        trailing.writeByte(1);
        assertThrows(DecoderException.class, () ->
                C2SBulkSellQuotePacket.decode(trailing));
    }

    @Test
    void quoteRejectsDuplicateLinesAndOverflowingTotals() {
        BulkSellQuote.Line line = eligibleLine(
                "line.one", Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () ->
                new BulkSellQuote(
                        UUID.randomUUID(),
                        BulkSellTarget.ADMIN_SHOP,
                        "default", 1L,
                        "Coins", 2, true,
                        List.of(line, line)));

        BulkSellQuote.Line second = eligibleLine(
                "line.two", 1L);
        assertThrows(ArithmeticException.class, () ->
                new BulkSellQuote(
                        UUID.randomUUID(),
                        BulkSellTarget.ADMIN_SHOP,
                        "default", 1L,
                        "Coins", 2, true,
                        List.of(line, second)));
    }

    @Test
    void commitAndResultShapesRejectAmbiguousRequests() {
        UUID quoteId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                new C2SBulkSellCommitPacket(
                        quoteId, List.of("line", "line")));
        assertThrows(IllegalArgumentException.class, () ->
                new C2SBulkSellCommitPacket(
                        new UUID(0L, 0L), List.of("line")));
        assertThrows(IllegalArgumentException.class, () ->
                new S2CBulkSellResultPacket(
                        quoteId, BulkSellService.Status.SUCCESS,
                        1, 0, 0, 0L,
                        "Coins", 2, false));
        assertThrows(IllegalArgumentException.class, () ->
                new S2CBulkSellResultPacket(
                        quoteId, BulkSellService.Status.REJECTED,
                        1, 1, 0, 10L,
                        "Coins", 2, false));
    }

    private static BulkSellQuote quote() {
        BulkSellQuote.Line eligible = new BulkSellQuote.Line(
                "line.one", "Server Shop",
                List.of(new BulkSellQuote.Component(
                        "minecraft:diamond", 1, "")),
                2, 125L, 250L, true,
                "gui.futureshops.bulk_sell.reason.eligible");
        BulkSellQuote.Line unavailable =
                new BulkSellQuote.Line(
                        "line.two",
                        "gui.futureshops.bulk_sell.destination.none",
                        List.of(new BulkSellQuote.Component(
                                "minecraft:dirt", 4, "")),
                        1, 0L, 0L, false,
                        "gui.futureshops.bulk_sell.reason.not_accepted");
        return new BulkSellQuote(
                UUID.randomUUID(),
                BulkSellTarget.ADMIN_SHOP,
                "default",
                System.currentTimeMillis() + 60_000L,
                "Coins", 2, true,
                List.of(eligible, unavailable));
    }

    private static BulkSellQuote.Line eligibleLine(
            String lineId,
            long payout
    ) {
        return new BulkSellQuote.Line(
                lineId, "Server Shop",
                List.of(new BulkSellQuote.Component(
                        "minecraft:diamond", 1, "")),
                1, payout, payout, true,
                "gui.futureshops.bulk_sell.reason.eligible");
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private static <T> void assertRoundTrip(
            T packet,
            Encoder<T> encoder,
            Decoder<T> decoder
    ) {
        FriendlyByteBuf buffer = buffer();
        encoder.encode(packet, buffer);
        assertEquals(packet, decoder.decode(buffer));
    }

    @FunctionalInterface
    private interface Encoder<T> {
        void encode(T packet, FriendlyByteBuf buffer);
    }

    @FunctionalInterface
    private interface Decoder<T> {
        T decode(FriendlyByteBuf buffer);
    }
}
