package com.enviouse.futureshopsp.server.pricing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicSellPriceTest {
    @Test
    void preservesConfiguredSpreadAndRoundsMinorUnits() {
        assertEquals(75L, DynamicPricingEngine.scaleSellPrice(50L, 100L, 150L));
        assertEquals(30L, DynamicPricingEngine.scaleSellPrice(50L, 100L, 60L));
        assertEquals(55L, DynamicPricingEngine.scaleSellPrice(50L, 100L, 109L));
        assertEquals(150L, DynamicPricingEngine.scaleSellPrice(100L, 100L, 150L));
    }

    @Test
    void supportsSellOnlyListingsAndSmallAmounts() {
        assertEquals(12L, DynamicPricingEngine.scaleSellPrice(20L, 20L, 12L));
        assertEquals(1L, DynamicPricingEngine.scaleSellPrice(1L, 100L, 1L));
        assertEquals(0L, DynamicPricingEngine.scaleSellPrice(0L, 100L, 150L));
    }

    @Test
    void rejectsInvalidInputsAndOverflowWithoutSaturation() {
        assertEquals(0L, DynamicPricingEngine.scaleSellPrice(-1L, 100L, 150L));
        assertEquals(0L, DynamicPricingEngine.scaleSellPrice(50L, 0L, 150L));
        assertEquals(0L, DynamicPricingEngine.scaleSellPrice(50L, 100L, 0L));
        assertEquals(0L, DynamicPricingEngine.scaleSellPrice(Long.MAX_VALUE, 1L, 2L));
        assertEquals(Long.MAX_VALUE, DynamicPricingEngine.scaleSellPrice(Long.MAX_VALUE, 2L, 2L));
    }
}
