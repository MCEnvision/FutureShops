package com.enviouse.futureshops.client.market;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketBackNavigationPolicyTest {
    @Test
    void localTabsNeverReceiveAHistoryBackButton() {
        assertFalse(MarketBackNavigationPolicy.show(
                MarketModule.BAZAAR, "orders", 4));
        assertFalse(MarketBackNavigationPolicy.show(
                MarketModule.AUCTION_HOUSE, "mine", 3));
    }

    @Test
    void detailRoutesReturnToTheirSourceView() {
        assertTrue(MarketBackNavigationPolicy.show(
                MarketModule.BAZAAR, "product_detail", 1));
        assertTrue(MarketBackNavigationPolicy.show(
                MarketModule.AUCTION_HOUSE, "listing_detail", 2));
        assertFalse(MarketBackNavigationPolicy.show(
                MarketModule.AUCTION_HOUSE, "listing_detail", 0));
    }
}
