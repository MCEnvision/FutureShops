package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.data.CatalogItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerShopTradeFilterPolicyTest {
    @Test
    void allIsTheDefaultFilter() {
        assertEquals(ServerShopTradeFilterPolicy.Filter.ALL,
                ServerShopTradeFilterPolicy.defaultFilter());
        assertEquals(ServerShopTradeFilterPolicy.Filter.ALL,
                ServerShopTradeFilterPolicy.fromIndex(0));
    }

    @Test
    void allIncludesMoneyAndBarterListings() {
        assertTrue(matches(ServerShopTradeFilterPolicy.Filter.ALL,
                item(100L, 0L, false)));
        assertTrue(matches(ServerShopTradeFilterPolicy.Filter.ALL,
                item(0L, 25L, false)));
        assertTrue(matches(ServerShopTradeFilterPolicy.Filter.ALL,
                item(0L, 0L, true)));
        assertTrue(matches(ServerShopTradeFilterPolicy.Filter.ALL,
                item(100L, 0L, true)));
        assertFalse(matches(ServerShopTradeFilterPolicy.Filter.ALL,
                item(0L, 0L, false)));
    }

    @Test
    void buyAndBarterFiltersRemainIndependent() {
        CatalogItem money = item(100L, 0L, false);
        CatalogItem barter = item(0L, 0L, true);
        CatalogItem both = item(100L, 0L, true);

        assertTrue(matches(ServerShopTradeFilterPolicy.Filter.BUY, money));
        assertFalse(matches(ServerShopTradeFilterPolicy.Filter.BARTER, money));
        assertFalse(matches(ServerShopTradeFilterPolicy.Filter.BUY, barter));
        assertTrue(matches(ServerShopTradeFilterPolicy.Filter.BARTER, barter));
        assertTrue(matches(ServerShopTradeFilterPolicy.Filter.BUY, both));
        assertTrue(matches(ServerShopTradeFilterPolicy.Filter.BARTER, both));
    }

    private static boolean matches(
            ServerShopTradeFilterPolicy.Filter filter,
            CatalogItem item
    ) {
        return ServerShopTradeFilterPolicy.matches(filter, item);
    }

    private static CatalogItem item(
            long buyPrice,
            long sellPrice,
            boolean barter
    ) {
        return new CatalogItem(
                "listing", "minecraft:stone", "Stone",
                buyPrice, sellPrice, 1, false, barter,
                "blocks", false, 0L, barter, "", 1);
    }
}
