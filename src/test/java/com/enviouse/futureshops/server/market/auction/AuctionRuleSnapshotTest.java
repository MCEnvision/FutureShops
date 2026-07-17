package com.enviouse.futureshops.server.market.auction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuctionRuleSnapshotTest {
    @Test
    void minimumBidUsesTheGreaterFixedOrPercentageIncrement() {
        AuctionRuleSnapshot fixed = rules(100L, 50, 250, true);
        AuctionRuleSnapshot percentage = rules(1L, 500, 250, true);

        assertEquals(1000L, fixed.minimumBid(0L, 1000L));
        assertEquals(1100L, fixed.minimumBid(1000L, 1000L));
        assertEquals(1050L, percentage.minimumBid(1000L, 1000L));
        assertEquals(2L, percentage.minimumBid(1L, 1L));
    }

    @Test
    void taxUsesOverflowSafeFloorRounding() {
        AuctionRuleSnapshot rules = rules(1L, 0, 250, true);

        assertEquals(2L, rules.saleTax(100L));
        assertEquals(25L, rules.saleTax(1000L));
        assertEquals(230584300921369395L, rules.saleTax(Long.MAX_VALUE));
    }

    @Test
    void enabledAntiSnipeMustHaveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new AuctionRuleSnapshot(
            0L, 0, 1L, 0, true, 100L, 100L, 99L, 1,
            true, AuctionTimeBasis.REAL_TIME, true, 0L));
    }

    @Test
    void persistedEnumWireCodesAreExplicitAndStable() {
        assertEquals(1, AuctionListingType.BUY_NOW.wireCode());
        assertEquals(2, AuctionListingType.TIMED_AUCTION.wireCode());
        assertEquals(3, AuctionListingType.AUCTION_WITH_BUYOUT.wireCode());
        assertEquals(1, AuctionTimeBasis.REAL_TIME.wireCode());
        assertEquals(2, AuctionTimeBasis.ONLINE_TIME.wireCode());
    }

    private static AuctionRuleSnapshot rules(
        long fixedIncrement,
        int percentageIncrement,
        int tax,
        boolean cancelBeforeBid
    ) {
        return new AuctionRuleSnapshot(
            10L,
            tax,
            fixedIncrement,
            percentageIncrement,
            true,
            60L,
            60L,
            120L,
            2,
            cancelBeforeBid,
            AuctionTimeBasis.REAL_TIME,
            true,
            7L
        );
    }
}
