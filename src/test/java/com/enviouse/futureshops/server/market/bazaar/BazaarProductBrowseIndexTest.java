package com.enviouse.futureshops.server.market.bazaar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarProductBrowseIndexTest {
    private BazaarOrderBook book;
    private long nextId;

    @BeforeEach
    void setUp() {
        book = new BazaarOrderBook();
        book.registerProduct(product("iron", 1L,
                "minecraft:iron_ingot", "ores"));
        book.registerProduct(product("iron", 2L,
                "minecraft:iron_ingot", "metals"));
        book.registerProduct(product("gold", 1L,
                "minecraft:gold_ingot", "metals"));
        book.setEffectiveRules(rules());
        nextId = 100L;
    }

    @Test
    void browseUsesLatestVersionsAndSortsByInstantBuy() {
        createSell("iron", 2L, 100L, 4);
        createSell("gold", 1L, 50L, 2);
        BazaarProductVersionKey watched =
                new BazaarProductVersionKey("iron", 2L);
        BazaarProductBrowseIndex.Query query =
                BazaarProductBrowseIndex.Query.products("",
                        BazaarProductBrowseIndex.Sort.INSTANT_BUY_LOWEST,
                        0, 10, 1000L, 1000L, 5);

        BazaarProductBrowseIndex.Page page =
                BazaarProductBrowseIndex.query(book.snapshot(), query,
                        Set.of(watched));

        assertEquals(2, page.totalResults());
        assertEquals("gold", page.cards().get(0).product().productId());
        assertEquals(50L, page.cards().get(0).summary()
                .bestAskMinor().orElseThrow());
        assertEquals(watched, page.cards().get(1).product());
        assertTrue(page.cards().get(1).watched());
    }

    @Test
    void predictiveSearchAndCategoryFilterCompose() {
        createSell("iron", 2L, 100L, 4);
        createSell("gold", 1L, 50L, 2);
        BazaarProductBrowseIndex.Query query =
                new BazaarProductBrowseIndex.Query(
                        "iron mine", "metals",
                        Set.of(BazaarProductStatus.ACTIVE),
                        BazaarProductBrowseIndex.Sort.NAME,
                        0, 10, 1000L, 1000L, 5);

        BazaarProductBrowseIndex.Page page =
                BazaarProductBrowseIndex.query(book.snapshot(), query,
                        Set.of());

        assertEquals(1, page.totalResults());
        assertEquals(2L, page.cards().get(0).product().version());
        assertEquals("minecraft:iron_ingot",
                page.cards().get(0).registryId());
    }

    @Test
    void watchedQueryFiltersBeforePagination() {
        BazaarProductVersionKey watched =
                new BazaarProductVersionKey("iron", 2L);
        BazaarProductBrowseIndex.Query query =
                BazaarProductBrowseIndex.Query.products("",
                        BazaarProductBrowseIndex.Sort.NAME,
                        0, 1, 1000L, 1000L, 5);

        BazaarProductBrowseIndex.Page page =
                BazaarProductBrowseIndex.queryWatched(
                        book.snapshot(), query, Set.of(watched));

        assertEquals(1, page.totalResults());
        assertEquals(watched, page.cards().get(0).product());
        assertTrue(page.cards().get(0).watched());
    }

    @Test
    void invalidPageAndDepthBoundsFailClosed() {
        assertThrows(IllegalArgumentException.class, () ->
                BazaarProductBrowseIndex.Query.products("",
                        BazaarProductBrowseIndex.Sort.NAME, 0,
                        BazaarProductBrowseIndex.MAX_PAGE_SIZE + 1,
                        0L, 1L, 1));
        assertThrows(IllegalArgumentException.class, () ->
                BazaarProductBrowseIndex.Query.products("",
                        BazaarProductBrowseIndex.Sort.NAME, 0, 10,
                        0L, 1L,
                        BazaarMarketAnalytics.MAX_DEPTH_LEVELS + 1));
    }

    private void createSell(
            String productId,
            long version,
            long price,
            int quantity
    ) {
        UUID request = next();
        UUID order = next();
        UUID owner = next();
        UUID activation = next();
        UUID custody = next();
        BazaarOperationResult result = book.create(
                new CreateBazaarOrderCommand(request, order, owner,
                        activation, Optional.empty(),
                        Optional.of(custody), productId, version,
                        BazaarOrderSide.SELL, BazaarOrderType.LIMIT,
                        BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                        price, quantity, 100L, 0L, rules()));
        assertTrue(result.newlyCommitted());
    }

    private UUID next() {
        return new UUID(8L, nextId++);
    }

    private static BazaarProduct product(
            String productId,
            long version,
            String registryId,
            String category
    ) {
        return new BazaarProduct(productId, version, registryId, "",
                category, 1, 1L, 1L, 1_000_000L, 1_000_000,
                BazaarProductStatus.ACTIVE);
    }

    private static BazaarRuleSnapshot rules() {
        return new BazaarRuleSnapshot(0, 0, 1_000_000,
                1_000_000_000L, 32, 8, 10_000_000_000L,
                BazaarSelfTradePolicy.CANCEL_TAKER,
                BazaarExecutionPricePolicy.MAKER, false, 5000,
                0L, 1L);
    }
}
