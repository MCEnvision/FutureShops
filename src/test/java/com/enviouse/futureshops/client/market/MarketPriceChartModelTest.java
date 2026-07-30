package com.enviouse.futureshops.client.market;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketPriceChartModelTest {
    @Test
    void realHistoryUsesDirectionalTonesAndExactPrices() {
        MarketPriceChartModel model = MarketPriceChartModel.create(
                List.of(100L, 125L, 110L, 140L),
                1L, 2L, 3L);

        assertEquals(MarketPriceChartModel.Kind.PRICE_HISTORY,
                model.kind());
        assertEquals(List.of(100L, 125L, 110L, 140L),
                model.values());
        assertEquals(List.of(MarketPriceChartModel.Tone.GOLD,
                        MarketPriceChartModel.Tone.POSITIVE,
                        MarketPriceChartModel.Tone.NEGATIVE,
                        MarketPriceChartModel.Tone.POSITIVE),
                model.tones());
        assertEquals(100L, model.minimum());
        assertEquals(140L, model.maximum());
        assertEquals(140L, model.latest());
    }

    @Test
    void missingHistoryFallsBackToAnHonestBidLadder() {
        MarketPriceChartModel model = MarketPriceChartModel.create(
                List.of(), 100L, 110L, 500L);

        assertEquals(MarketPriceChartModel.Kind.BID_LADDER,
                model.kind());
        assertEquals(List.of(100L, 110L, 500L), model.values());
        assertEquals(List.of(MarketPriceChartModel.Tone.GOLD,
                        MarketPriceChartModel.Tone.POSITIVE,
                        MarketPriceChartModel.Tone.NEGATIVE),
                model.tones());
    }

    @Test
    void duplicateAndMissingLadderPricesStayCompact() {
        MarketPriceChartModel model = MarketPriceChartModel.create(
                java.util.Arrays.asList(null, -1L), 100L, 100L, 0L);

        assertEquals(List.of(100L), model.values());
        assertEquals(List.of(MarketPriceChartModel.Tone.GOLD),
                model.tones());
    }
}
