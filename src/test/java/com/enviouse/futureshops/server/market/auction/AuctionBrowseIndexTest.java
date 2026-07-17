package com.enviouse.futureshops.server.market.auction;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionBrowseIndexTest {
    @Test
    void predictiveSearchFiltersAndSortsServerAuthoritativeCards() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionListing first = book.create(command(1L,
                "minecraft:diamond", "materials",
                "shiny diamond minecraft gem", 100L,
                1000L, 3000L)).listing().orElseThrow();
        book.create(command(10L, "minecraft:emerald", "materials",
                "green emerald minecraft gem", 200L,
                1100L, 2400L));
        AuctionListing third = book.create(command(20L,
                "example:diamond_hammer", "tools",
                "diamond hammer example tool", 300L,
                1200L, 2200L)).listing().orElseThrow();

        AuctionBrowseIndex.Query query = new AuctionBrowseIndex.Query(
                "diamond", "", "", Optional.empty(),
                OptionalLong.empty(), OptionalLong.empty(),
                Set.of(AuctionListingState.ACTIVE),
                AuctionBrowseIndex.Sort.ENDING_SOON,
                0, 10, 1500L);
        AuctionBrowseIndex.Page page = AuctionBrowseIndex.query(
                book.snapshot(), query, Set.of(third.listingId()));

        assertEquals(2, page.totalResults());
        assertEquals(third.listingId(),
                page.cards().get(0).listingId());
        assertEquals(first.listingId(),
                page.cards().get(1).listingId());
        assertTrue(page.cards().get(0).watched());
        assertEquals(700L, page.cards().get(0).remainingMillis());
    }

    @Test
    void exactVariantPriceSellerAndPaginationCompose() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionListing first = book.create(command(1L,
                "minecraft:diamond", "materials", "diamond", 100L,
                1000L, 3000L)).listing().orElseThrow();
        book.create(command(10L, "minecraft:diamond", "materials",
                "diamond", 200L, 1100L, 3100L));
        String fingerprint = first.itemLot().fingerprint();
        AuctionBrowseIndex.Query query = new AuctionBrowseIndex.Query(
                "", "materials", fingerprint,
                Optional.of(first.sellerId()), OptionalLong.of(50L),
                OptionalLong.of(150L),
                Set.of(AuctionListingState.ACTIVE),
                AuctionBrowseIndex.Sort.LOWEST_PRICE,
                0, 1, 1200L);

        AuctionBrowseIndex.Page page = AuctionBrowseIndex.query(
                book.snapshot(), query, Set.of());

        assertEquals(1, page.totalResults());
        assertEquals(1, page.pageCount());
        assertEquals(first.listingId(),
                page.cards().get(0).listingId());
        assertEquals(100L, page.cards().get(0).displayPriceMinor());
    }

    @Test
    void invalidQueryBoundsFailClosed() {
        assertThrows(IllegalArgumentException.class, () ->
                AuctionBrowseIndex.Query.browse("x",
                        AuctionBrowseIndex.Sort.NEWEST, 0,
                        AuctionBrowseIndex.MAX_PAGE_SIZE + 1, 0L));
        assertThrows(IllegalArgumentException.class, () ->
                new AuctionBrowseIndex.Query("", "", "bad",
                        Optional.empty(), OptionalLong.empty(),
                        OptionalLong.empty(),
                        Set.of(AuctionListingState.ACTIVE),
                        AuctionBrowseIndex.Sort.NEWEST, 0, 10, 0L));
    }

    private static CreateAuctionCommand command(
            long suffix,
            String registryId,
            String category,
            String search,
            long startingBid,
            long createdAt,
            long deadline
    ) {
        return new CreateAuctionCommand(id(suffix), id(suffix + 1L),
                id(suffix + 2L), id(suffix + 3L),
                new AuctionItemLot(id(suffix + 4L), registryId,
                        fingerprint(suffix), 1, 64, category, search),
                AuctionListingType.TIMED_AUCTION, startingBid, 0L,
                rules(), createdAt, deadline);
    }

    private static AuctionRuleSnapshot rules() {
        return new AuctionRuleSnapshot(0L, 0, 10L, 0,
                true, 60L, 60L, 600L, 10,
                true, AuctionTimeBasis.REAL_TIME, true, 1L);
    }

    private static String fingerprint(long suffix) {
        return String.format("%064x", suffix);
    }

    private static UUID id(long value) {
        return new UUID(9L, value);
    }
}
