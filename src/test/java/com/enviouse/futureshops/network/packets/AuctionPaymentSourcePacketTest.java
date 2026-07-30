package com.enviouse.futureshops.network.packets;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuctionPaymentSourcePacketTest {
    private static final UUID REQUEST = UUID.fromString(
            "12345678-1234-5678-1234-567812345678");
    private static final UUID ROUTE = UUID.fromString(
            "22345678-1234-5678-1234-567812345678");
    private static final UUID LISTING = UUID.fromString(
            "32345678-1234-5678-1234-567812345678");
    private static final String FINGERPRINT = "0123456789abcdef".repeat(4);

    @Test
    void createRoundTripsPhysicalSource() {
        C2SAuctionCreatePacket input = new C2SAuctionCreatePacket(
                REQUEST, ROUTE, 4, "TIMED_AUCTION", 100L, 0L,
                3600L, 3, FINGERPRINT, "PHYSICAL");
        FriendlyByteBuf buffer = buffer();
        C2SAuctionCreatePacket.encode(input, buffer);
        assertEquals(input, C2SAuctionCreatePacket.decode(buffer));
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void bidAndBuyNowRoundTripBothSources() {
        C2SAuctionBidPacket bid = new C2SAuctionBidPacket(
                REQUEST, ROUTE, LISTING, 7L, 900L, "PHYSICAL");
        FriendlyByteBuf bidBuffer = buffer();
        C2SAuctionBidPacket.encode(bid, bidBuffer);
        assertEquals(bid, C2SAuctionBidPacket.decode(bidBuffer));

        C2SAuctionBuyNowPacket buyNow = new C2SAuctionBuyNowPacket(
                REQUEST, ROUTE, LISTING, 8L, "WALLET");
        FriendlyByteBuf buyBuffer = buffer();
        C2SAuctionBuyNowPacket.encode(buyNow, buyBuffer);
        assertEquals(buyNow, C2SAuctionBuyNowPacket.decode(buyBuffer));
    }

    @Test
    void constructorsRejectUnknownSources() {
        assertThrows(IllegalArgumentException.class,
                () -> new C2SAuctionBidPacket(REQUEST, ROUTE, LISTING,
                        1L, 100L, "bank"));
        assertThrows(IllegalArgumentException.class,
                () -> new C2SAuctionBuyNowPacket(REQUEST, ROUTE, LISTING,
                        1L, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new C2SAuctionCreatePacket(REQUEST, ROUTE, 0,
                        "BUY_NOW", 0L, 100L, 0L, 1,
                        FINGERPRINT, "inventory"));
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }
}
