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
                "src/main/java/com/enviouse/futureshops/server/shop/PlayerShopEscrowTransactionService.java"));
        int method = source.indexOf("static void buy(");
        int quantityGuard = source.indexOf("ShopTransactionUtil.isValidBuyQuantity(quantity)", method);
        int blockLookup = source.indexOf("buyer.level().getBlockEntity(pos)", method);
        assertTrue(method >= 0);
        assertTrue(quantityGuard > method);
        assertTrue(blockLookup > quantityGuard);
    }

    @Test
    void buyPathUsesCheckedPlansAndDurableEscrowEvidence() throws Exception {
        String block = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/shop/PlayerShopBlockService.java"));
        String buy = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/shop/PlayerShopEscrowTransactionService.java"));
        assertTrue(buy.contains("preparePurchasePlan"));
        assertTrue(buy.contains("checkedBarterTotal"));
        assertTrue(buy.contains("PlayerShopEscrowIntent"));
        assertTrue(buy.contains("PlayerShopLiveEscrowService.execute"));
        assertTrue(buy.contains("previewExtractComposite"));
        assertTrue(buy.contains("PlayerShopStorageMutationPlan"));
        assertFalse(block.contains("PurchasePaymentService"));
        assertFalse(block.contains("settlementData.recordSale"));
        assertFalse(block.contains("provider.withdraw"));
        assertFalse(block.contains("provider.deposit"));
        assertFalse(buy.contains("Math.max(1, quantity)"));
        assertFalse(buy.contains("entry.count() * qty"));
        assertFalse(buy.contains("listing.baseQuantity() * qty"));
        assertFalse(buy.contains("listing.calculatePrice(qty)"));
        assertFalse(buy.contains("listing.effectiveBarterTotal(qty)"));
    }

    @Test
    void everyNormalPlayerShopValueEntrypointUsesLiveEscrow() throws Exception {
        String block = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/shop/PlayerShopBlockService.java"));
        assertTrue(block.contains("PlayerShopEscrowTransactionService.buy"));
        assertTrue(block.contains("PlayerShopEscrowTransactionService.sell"));
        assertTrue(block.contains("PlayerShopSettlementEscrowService.collect"));
        assertFalse(block.contains("buyInternal"));
        assertFalse(block.contains("handleSellInternal"));
        assertFalse(block.contains("handleAdminShopBuy"));
    }
}
