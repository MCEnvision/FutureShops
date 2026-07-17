package com.enviouse.futureshops.server.market.auction;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuctionHouseSnapshotCodecTest {
    @Test
    void canonicalSnapshotRoundTripsAppliedAndRejectedReceipts() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionListing listing = book.create(create(id(1), id(2), id(3)))
                .listing().orElseThrow();
        book.bid(new PlaceAuctionBidCommand(id(10), listing.listingId(),
                listing.revision(), id(11), id(12), id(13), id(14),
                100L, 100L, 1200L));
        book.bid(new PlaceAuctionBidCommand(id(20), listing.listingId(),
                99L, id(21), id(22), id(23), id(24),
                200L, 200L, 1300L));
        AuctionHouseSnapshot snapshot = book.snapshot();

        byte[] encoded = AuctionHouseSnapshotCodec.encode(snapshot);
        AuctionHouseSnapshot decoded =
                AuctionHouseSnapshotCodec.decode(encoded);

        assertEquals(snapshot, decoded);
        assertArrayEquals(encoded, AuctionHouseSnapshotCodec.encode(decoded));
        assertEquals(snapshot.listings(),
                new AuctionHouseBook(decoded).listings());
    }

    @Test
    void truncatedOversizedAndTamperedSnapshotsAreRejected() {
        AuctionHouseBook book = new AuctionHouseBook();
        book.create(create(id(1), id(2), id(3)));
        byte[] encoded = AuctionHouseSnapshotCodec.encode(book.snapshot());

        assertThrows(IllegalArgumentException.class,
                () -> AuctionHouseSnapshotCodec.decode(
                        Arrays.copyOf(encoded, encoded.length - 1)));
        byte[] tampered = encoded.clone();
        tampered[tampered.length - 1] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> AuctionHouseSnapshotCodec.decode(tampered));
        assertThrows(IllegalArgumentException.class,
                () -> AuctionHouseSnapshotCodec.decode(new byte[
                        AuctionHouseSnapshotCodec.MAX_ENCODED_BYTES + 1]));
    }

    private static CreateAuctionCommand create(UUID requestId,
                                                UUID listingId,
                                                UUID sellerId) {
        return new CreateAuctionCommand(requestId, listingId, sellerId,
                id(1002), new AuctionItemLot(id(2002),
                "minecraft:diamond", "a".repeat(64), 1, 32,
                "materials", "diamond minecraft"),
                AuctionListingType.AUCTION_WITH_BUYOUT, 100L, 1000L,
                new AuctionRuleSnapshot(10L, 250, 10L, 0, true,
                        60L, 60L, 120L, 2, true,
                        AuctionTimeBasis.REAL_TIME, true, 7L),
                1000L, 2000L);
    }

    private static UUID id(long value) {
        return new UUID(1L, value);
    }
}
