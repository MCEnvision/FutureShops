package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.client.market.MarketModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketModuleServiceViewTest {
    @Test
    void moduleSpecificDetailViewsSurviveOpenRouteNormalization() {
        assertEquals("product_detail",
                MarketModuleService.normalizeView(
                        MarketModule.BAZAAR, "product_detail"));
        assertEquals("listing_detail",
                MarketModuleService.normalizeView(
                        MarketModule.AUCTION_HOUSE,
                        "listing_detail"));
    }

    @Test
    void crossModuleAndUnknownDetailViewsReturnToTheModuleRoot() {
        assertEquals("products", MarketModuleService.normalizeView(
                MarketModule.BAZAAR, "listing_detail"));
        assertEquals("browse", MarketModuleService.normalizeView(
                MarketModule.AUCTION_HOUSE, "product_detail"));
        assertEquals("browse", MarketModuleService.normalizeView(
                MarketModule.AUCTION_HOUSE, "unknown_detail"));
    }
}
