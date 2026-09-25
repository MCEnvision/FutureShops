package com.enviouse.futureshopsp.server.pricing;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicPricingEngineTest {
    @Test
    void calculatesPriceWithExactMinorUnitRounding() {
        OptionalLong price = DynamicPricingEngine.calculatePrice(
                100L, 100L, 1, 0, 0.5D, 0.0D, 1.0D, 100.0D, 0.0D);

        assertTrue(price.isPresent());
        assertEquals(101L, price.getAsLong());
    }

    @Test
    void rejectsInvalidCountsAndNonFiniteConfiguration() {
        assertTrue(DynamicPricingEngine.calculatePrice(
                100L, 100L, -1, 0, 0.5D, 0.0D, 1.0D, 100.0D, 0.0D).isEmpty());
        assertTrue(DynamicPricingEngine.calculatePrice(
                100L, 100L, 1, 0, Double.NaN, 0.0D, 1.0D, 100.0D, 0.0D).isEmpty());
    }

    @Test
    void rejectsOverflowInsteadOfSaturatingThePrice() {
        assertTrue(DynamicPricingEngine.calculatePrice(
                Long.MAX_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE, 0,
                Double.MAX_VALUE, 0.0D, Double.MAX_VALUE, 100.0D, 0.0D).isEmpty());
    }

    @Test
    void reportedConfigurationClampsBothDemandAndSupply() {
        assertEquals(300L, DynamicPricingEngine.calculatePrice(
                100L, 100L, 1_000, 0, 0.6D, 0.4D, 0.98D, 200D, 40D).orElseThrow());
        assertEquals(60L, DynamicPricingEngine.calculatePrice(
                100L, 100L, 0, 1_000, 0.6D, 0.4D, 0.98D, 200D, 40D).orElseThrow());
    }
}
