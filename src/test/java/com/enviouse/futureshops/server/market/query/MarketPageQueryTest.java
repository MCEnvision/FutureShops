package com.enviouse.futureshops.server.market.query;

import com.enviouse.futureshops.client.market.MarketModule;
import org.junit.jupiter.api.Test;

import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketPageQueryTest {
    @Test
    void queryNormalizesRouteFieldsAndPreservesBounds() {
        MarketPageQuery query = new MarketPageQuery(UUID.randomUUID(),
                UUID.randomUUID(), MarketModule.BAZAAR, "Products",
                "iron", "Metals", "Name", 2, 28,
                OptionalLong.of(10L), OptionalLong.of(20L), 100L);

        assertEquals("products", query.view());
        assertEquals("metals", query.category());
        assertEquals("name", query.sort());
        assertEquals(2, query.pageIndex());
    }

    @Test
    void invalidModulePricesAndPaginationFailClosed() {
        UUID request = UUID.randomUUID();
        UUID route = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () ->
                MarketPageQuery.root(request, route,
                        MarketModule.SHOP, "browse", 28, 0L));
        assertThrows(IllegalArgumentException.class, () ->
                new MarketPageQuery(request, route,
                        MarketModule.AUCTION_HOUSE, "browse", "", "",
                        "", 0, 28, OptionalLong.of(20L),
                        OptionalLong.of(10L), 0L));
        assertThrows(IllegalArgumentException.class, () ->
                MarketPageQuery.root(request, route,
                        MarketModule.BAZAAR, "products",
                        MarketPageQuery.MAXIMUM_PAGE_SIZE + 1, 0L));
    }
}
