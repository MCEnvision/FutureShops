package com.enviouse.futureshops.server.market.auction;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionHouseMutationTest {
    @Test
    void oneCanonicalOperationAppliesAndReplays() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionHouseSnapshot before = book.snapshot();
        UUID requestId = id(1);
        book.create(create(requestId, id(2), id(3)));
        AuctionHouseSnapshot after = book.snapshot();
        AuctionHouseMutation mutation = AuctionHouseMutation.between(
                before, after, requestId);

        AuctionHouseMutation.ApplyResult applied = mutation.apply(before);
        AuctionHouseMutation.ApplyResult replayed = mutation.apply(after);

        assertFalse(applied.replayed());
        assertEquals(after, applied.snapshot());
        assertTrue(replayed.replayed());
        assertEquals(after, replayed.snapshot());
    }

    @Test
    void mutationCodecRoundTripsAndRejectsWrongAncestry() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionHouseSnapshot before = book.snapshot();
        UUID requestId = id(1);
        book.create(create(requestId, id(2), id(3)));
        AuctionHouseMutation mutation = AuctionHouseMutation.between(
                before, book.snapshot(), requestId);

        byte[] encoded = AuctionHouseMutationCodec.encode(mutation);
        AuctionHouseMutation decoded = AuctionHouseMutationCodec.decode(
                encoded);

        assertEquals(mutation, decoded);
        assertArrayEquals(encoded, AuctionHouseMutationCodec.encode(decoded));
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);
        assertThrows(IllegalArgumentException.class,
                () -> AuctionHouseMutationCodec.decode(truncated));

        AuctionHouseBook other = new AuctionHouseBook();
        other.create(create(id(20), id(21), id(22)));
        assertThrows(IllegalArgumentException.class,
                () -> mutation.apply(other.snapshot()));
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
