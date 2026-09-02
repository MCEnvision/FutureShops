package com.enviouse.futureshopsp.server.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromoValueValidationTest {
    @Test
    void rejectsNonFiniteAndOutOfRangePromotionValues() {
        assertFalse(PlayerShopBlockService.isValidPromoValue("PERCENTAGE", Double.NaN));
        assertFalse(PlayerShopBlockService.isValidPromoValue("PERCENTAGE", Double.POSITIVE_INFINITY));
        assertFalse(PlayerShopBlockService.isValidPromoValue("PERCENTAGE", 100.0001D));
        assertFalse(PlayerShopBlockService.isValidPromoValue("FLAT", Double.NEGATIVE_INFINITY));
        assertTrue(PlayerShopBlockService.isValidPromoValue("PERCENTAGE", 25.0D));
        assertTrue(PlayerShopBlockService.isValidPromoValue("FLAT", 1.0D));
    }
}
