package com.enviouse.futureshops.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShopCatalogPricingMathTest {
    @Test
    void percentageDiscountRoundsExactMinorUnitsHalfUp() {
        assertEquals(67L,
                ShopCatalog.applyDiscount(101L, "PERCENTAGE", 33.5D));
        assertEquals(0L,
                ShopCatalog.applyDiscount(101L, "FLASH", 100.0D));
    }

    @Test
    void flatDiscountUsesConfiguredMinorUnitPrecision() {
        int previous = com.enviouse.futureshops.Config.economyCurrencyDecimals;
        try {
            com.enviouse.futureshops.Config.economyCurrencyDecimals = 2;
            assertEquals(875L,
                    ShopCatalog.applyDiscount(1_000L, "FLAT", 1.25D));
        } finally {
            com.enviouse.futureshops.Config.economyCurrencyDecimals = previous;
        }
    }

    @Test
    void invalidDiscountsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> ShopCatalog.applyDiscount(100L, "PERCENTAGE",
                        Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> ShopCatalog.applyDiscount(100L, "FLAT", -1.0D));
    }
}
