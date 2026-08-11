package com.enviouse.futureshops.server.market.bazaar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarMarketAnalyticsTest {
    private static final BazaarProductVersionKey IRON =
            new BazaarProductVersionKey("iron", 1L);

    private BazaarOrderBook book;
    private long nextId;

    @BeforeEach
    void setUp() {
        book = new BazaarOrderBook();
        book.registerProduct(new BazaarProduct("iron", 1L,
                "minecraft:iron_ingot", "", "ores", 1, 1L,
                1L, 1_000_000L, 1_000_000,
                BazaarProductStatus.ACTIVE));
        book.setEffectiveRules(rules());
        book.setReferencePrice("iron", 80L);
        nextId = 100L;
    }

    @Test
    void summaryIncludesSpreadDepthVolumeAndTrend() {
        create(BazaarOrderSide.BUY, 90L, 5, 100L);
        create(BazaarOrderSide.SELL, 130L, 4, 200L);
        create(BazaarOrderSide.SELL, 100L, 2, 900L);
        create(BazaarOrderSide.BUY, 100L, 2, 1000L);
        create(BazaarOrderSide.SELL, 120L, 3, 1900L);
        create(BazaarOrderSide.BUY, 120L, 3, 2000L);

        BazaarMarketAnalytics.ProductSummary summary =
                BazaarMarketAnalytics.summarize(book.snapshot(), IRON,
                        2500L, 2500L, 10);

        assertEquals(90L, summary.bestBidMinor().orElseThrow());
        assertEquals(130L, summary.bestAskMinor().orElseThrow());
        assertEquals(40L, summary.spreadMinor().orElseThrow());
        assertEquals(120L, summary.lastPriceMinor().orElseThrow());
        assertEquals(5L, summary.recentVolume());
        assertEquals(560L, summary.recentNotionalMinor());
        assertEquals(2000L, summary.trendBasisPoints());
        assertEquals(List.of(new BazaarMarketAnalytics.DepthLevel(
                90L, 5L, 1)), summary.bidDepth());
        assertEquals(List.of(new BazaarMarketAnalytics.DepthLevel(
                130L, 4L, 1)), summary.askDepth());
    }

    @Test
    void ohlcvAndPortfolioUseCanonicalFillOrdering() {
        create(BazaarOrderSide.SELL, 100L, 2, 900L);
        create(BazaarOrderSide.BUY, 100L, 2, 1000L);
        create(BazaarOrderSide.SELL, 120L, 3, 1900L);
        create(BazaarOrderSide.BUY, 120L, 3, 2100L);

        List<BazaarMarketAnalytics.OhlcvBucket> buckets =
                BazaarMarketAnalytics.ohlcv(book.snapshot(), IRON,
                        0L, 3999L, 2000L, 4);

        assertEquals(2, buckets.size());
        assertEquals(100L, buckets.get(0).openMinor());
        assertEquals(2L, buckets.get(0).quantity());
        assertEquals(120L, buckets.get(1).closeMinor());
        assertEquals(3L, buckets.get(1).quantity());
        assertEquals(360L, BazaarMarketAnalytics
                .estimatedPortfolioValue(book.snapshot(),
                        Map.of(IRON, 3)));
        assertThrows(IllegalArgumentException.class, () ->
                BazaarMarketAnalytics.ohlcv(book.snapshot(), IRON,
                        0L, 10000L, 1L, 4));
    }

    @Test
    void volumeLeadersAreRankedByNotional() {
        create(BazaarOrderSide.SELL, 100L, 2, 100L);
        create(BazaarOrderSide.BUY, 100L, 2, 200L);

        List<BazaarMarketAnalytics.VolumeLeader> leaders =
                BazaarMarketAnalytics.volumeLeaders(
                        book.snapshot(), 0L, 1000L, 5);

        assertEquals(1, leaders.size());
        assertEquals(IRON, leaders.get(0).product());
        assertEquals(2L, leaders.get(0).quantity());
        assertEquals(200L, leaders.get(0).notionalMinor());
        assertTrue(leaders.get(0).trades() > 0);
    }

    private BazaarOperationResult create(
            BazaarOrderSide side,
            long price,
            int quantity,
            long createdAt
    ) {
        UUID request = next();
        UUID order = next();
        UUID owner = next();
        UUID activation = next();
        Optional<UUID> money = side == BazaarOrderSide.BUY
                ? Optional.of(next()) : Optional.empty();
        Optional<UUID> custody = side == BazaarOrderSide.SELL
                ? Optional.of(next()) : Optional.empty();
        return book.create(new CreateBazaarOrderCommand(
                request, order, owner, activation, money, custody,
                "iron", 1L, side, BazaarOrderType.LIMIT,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, price, quantity,
                createdAt, 0L, rules()));
    }

    private UUID next() {
        return new UUID(0L, nextId++);
    }

    private static BazaarRuleSnapshot rules() {
        return new BazaarRuleSnapshot(0, 0, 1_000_000,
                1_000_000_000L, 32, 8, 10_000_000_000L,
                BazaarSelfTradePolicy.CANCEL_TAKER,
                BazaarExecutionPricePolicy.MAKER, false, 5000,
                0L, 1L);
    }
}
