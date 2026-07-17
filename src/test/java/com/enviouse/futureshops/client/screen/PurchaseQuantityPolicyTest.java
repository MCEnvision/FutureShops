package com.enviouse.futureshops.client.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PurchaseQuantityPolicyTest {
    @Test
    void serverShopMaximumDoesNotDependOnWalletAffordability() {
        assertEquals(48, PurchaseQuantityPolicy.serverShopMaximum(false, 48, 0));
        assertEquals(PurchaseQuantityPolicy.MAX_SCREEN_QUANTITY,
                PurchaseQuantityPolicy.serverShopMaximum(true, 0, 0));
    }

    @Test
    void serverShopMaximumStillSupportsSellingMoreThanBuyStock() {
        assertEquals(64, PurchaseQuantityPolicy.serverShopMaximum(false, 5, 64));
    }

    @Test
    void moneyAndBothModesUseStockBeforePaymentSourceSelection() {
        assertEquals(32, PurchaseQuantityPolicy.playerShopMaximum("MONEY", 32, 0));
        assertEquals(32, PurchaseQuantityPolicy.playerShopMaximum("BOTH", 32, 0));
    }

    @Test
    void barterAndCompoundModesKeepTheBarterCap() {
        assertEquals(3, PurchaseQuantityPolicy.playerShopMaximum("BARTER", 32, 3));
        assertEquals(3, PurchaseQuantityPolicy.playerShopMaximum(
                "MONEY_AND_BARTER", 32, 3));
    }

    @Test
    void adminPlayerShopsUseTheScreenSafetyLimit() {
        assertEquals(PurchaseQuantityPolicy.MAX_SCREEN_QUANTITY,
                PurchaseQuantityPolicy.playerShopStockMaximum(true, 0));
    }
}
