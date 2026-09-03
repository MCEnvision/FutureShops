package com.enviouse.futureshopsp.server.shop;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopSaleEscrowSourceTest {
    @Test
    void saleEscrowPersistsExactStacksAndUsesFailClosedStates() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "shop", "PlayerShopSaleEscrowSavedData.java")));

        assertTrue(source.contains("PREPARED"));
        assertTrue(source.contains("REMOVED"));
        assertTrue(source.contains("DELIVERED"));
        assertTrue(source.contains("CLAIMED"));
        assertTrue(source.contains("RECOVERY_REQUIRED"));
        assertTrue(source.contains("stack.save(provider)"));
        assertTrue(source.contains("checksum(record)"));
        assertTrue(source.contains("cleanMarker"));
        assertTrue(source.contains("hasIncompleteRecords"));
    }

    @Test
    void playerShopBuyUsesSaleEscrowAroundPhysicalDelivery() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "shop", "PlayerShopBlockService.java")));

        assertTrue(source.contains("snapshotSaleStacks"));
        assertTrue(source.contains("saleEscrow.prepare"));
        assertTrue(source.contains("saleEscrow.markRemoved"));
        assertTrue(source.contains("saleEscrow.markDelivered"));
        assertTrue(source.contains("saleEscrow.markClaimed"));
        assertTrue(source.contains("saleEscrow.markRecoveryRequired"));
        assertTrue(source.contains("if (!ShopTransactionUtil.canFit(buyer.getInventory(), salePreview))"));
        assertTrue(source.contains("sendResult(buyer, false, ShopResultCode.RECOVERY_REQUIRED)"));
        assertTrue(source.contains("if (!ShopTransactionUtil.canFit(buyer.getInventory(), delivered))"));
        assertFalse(source.contains("buyer.drop(stack, false)"));
        int adminCapacity = source.indexOf("if (!ShopTransactionUtil.canFit(buyer.getInventory(), delivered))");
        int adminDebit = source.indexOf("admin buyer debit");
        assertTrue(adminCapacity >= 0 && adminDebit > adminCapacity);
    }

    @Test
    void settlementClaimPreflightsBeforeCreatingClaim() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "shop", "PlayerShopBlockService.java")));

        int claimLookup = source.indexOf("coordinator.claim(requestId).orElse(null)");
        int preflight = source.indexOf("coordinator.preflight(depositRequest)");
        int createClaim = source.indexOf("coordinator.createClaim(requestId");
        int settlementLock = source.indexOf("ReentrantLock settlementLock");
        assertTrue(claimLookup >= 0);
        assertTrue(preflight > claimLookup);
        assertTrue(createClaim > preflight);
        assertTrue(settlementLock >= 0);
        assertTrue(source.indexOf("settlementLock.lock()", settlementLock) > settlementLock);
        assertTrue(source.indexOf("settlementLock.unlock()", settlementLock) > settlementLock);
    }

    @Test
    void playerShopBuybackPreflightsAndEscrowsBeforeValueLegs() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp", "server", "shop",
                "PlayerShopBlockService.java")));

        int playerPath = source.indexOf("// ── Player shop path ──");
        int ownerPreflight = source.indexOf("coordinator.preflight(ownerDebitRequest)", playerPath);
        int escrowPrepare = source.indexOf("itemEscrow.prepare", playerPath);
        int itemRemoval = source.indexOf("collectAndRemoveItems", playerPath);
        int ownerDebit = source.indexOf("coordinator.withdraw(ownerDebitRequest)", playerPath);
        int sellerCredit = source.indexOf("coordinator.deposit(sellerCreditRequest)", playerPath);
        int stored = source.indexOf("itemEscrow.markStored", playerPath);
        int complete = source.indexOf("itemEscrow.markComplete", playerPath);
        assertTrue(playerPath >= 0);
        assertTrue(ownerPreflight > playerPath);
        assertTrue(escrowPrepare > ownerPreflight);
        assertTrue(itemRemoval > escrowPrepare);
        assertTrue(stored > itemRemoval);
        assertTrue(ownerDebit > itemRemoval);
        assertTrue(sellerCredit > ownerDebit);
        assertTrue(complete > sellerCredit);
    }

    @Test
    void adminBuybackPreflightsAndEscrowsBeforeVoid() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp", "server", "shop",
                "PlayerShopBlockService.java")));

        int handleSell = source.indexOf("public static void handleSell");
        int adminPath = source.indexOf("if (shop.isAdminShopMode()) {", handleSell);
        int admission = source.indexOf("coordinator.preflight(adminCreditRequest)", adminPath);
        int prepare = source.indexOf("itemEscrow.prepare", adminPath);
        int remove = source.indexOf("collectAndRemoveItems", adminPath);
        int credit = source.indexOf("coordinator.deposit(adminCreditRequest)", adminPath);
        assertTrue(handleSell >= 0);
        assertTrue(adminPath > handleSell);
        assertTrue(admission > adminPath);
        assertTrue(prepare > admission);
        assertTrue(remove > prepare);
        assertTrue(credit > remove);
    }

    private static Path projectDirectory() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve(Path.of("src", "main", "java")))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("FutureShops source directory is unavailable");
    }
}
