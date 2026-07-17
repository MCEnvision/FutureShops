package com.enviouse.futureshops;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopBuyHardeningContractTest {
    @Test
    void serverRejectsQuantityBeforeResolvingTheShop() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/shop/PlayerShopBlockService.java"));
        int method = source.indexOf("private static void buyInternal");
        int quantityGuard = source.indexOf("ShopTransactionUtil.isValidBuyQuantity(quantity)", method);
        int blockLookup = source.indexOf("buyer.level().getBlockEntity(pos)", method);
        assertTrue(method >= 0);
        assertTrue(quantityGuard > method);
        assertTrue(blockLookup > quantityGuard);
    }

    @Test
    void buyPathUsesCheckedPlansAndSettlementHeadroom() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/shop/PlayerShopBlockService.java"));
        int start = source.indexOf("private static void buyInternal");
        int end = source.indexOf("public static int confirmLink", start);
        String buy = source.substring(start, end);
        assertTrue(buy.contains("preparePurchasePlan"));
        assertTrue(buy.contains("checkedBarterTotal"));
        assertTrue(buy.contains("checkedStackCount"));
        assertTrue(buy.contains("canRecordSale"));
        assertTrue(buy.contains("if (!settlementData.recordSale"));
        assertFalse(buy.contains("Math.max(1, quantity)"));
        assertFalse(buy.contains("entry.count() * qty"));
        assertFalse(buy.contains("listing.baseQuantity() * qty"));
        assertFalse(buy.contains("listing.calculatePrice(qty)"));
        assertFalse(buy.contains("listing.effectiveBarterTotal(qty)"));
    }
}
