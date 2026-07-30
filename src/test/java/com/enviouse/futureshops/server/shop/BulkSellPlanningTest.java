package com.enviouse.futureshops.server.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkSellPlanningTest {
    @Test
    void higherEffectivePayoutWinsBeforeDistance() {
        int result = BulkSellPlanning.compare(
                9L, 3L, 1.0D, "near",
                8L, 2L, 100.0D, "far");

        assertTrue(result > 0);
    }

    @Test
    void equalDensityPrefersCompletePayoutThenDistanceThenIdentity() {
        assertTrue(BulkSellPlanning.compare(
                10L, 2L, 1.0D, "a",
                20L, 4L, 1.0D, "b") > 0);
        assertTrue(BulkSellPlanning.compare(
                10L, 2L, 10.0D, "a",
                10L, 2L, 20.0D, "b") < 0);
        assertTrue(BulkSellPlanning.compare(
                10L, 2L, 10.0D, "a",
                10L, 2L, 10.0D, "b") < 0);
    }

    @Test
    void maximumQuantityUsesAvailableDynamicCapacity() {
        assertEquals(7,
                BulkSellPlanning.maximumExecutableQuantity(
                        64, quantity -> quantity <= 7));
        assertEquals(64,
                BulkSellPlanning.maximumExecutableQuantity(
                        64, quantity -> true));
        assertEquals(0,
                BulkSellPlanning.maximumExecutableQuantity(
                        64, quantity -> false));
        assertEquals(0,
                BulkSellPlanning.maximumExecutableQuantity(
                        0, quantity -> true));
    }
}
