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
        int claimPreview = source.indexOf("settlements.previewClaim");
        int preflight = source.indexOf("coordinator.preflight(depositRequest)");
        int claimBegin = source.indexOf("settlements.beginClaim", preflight);
        int createClaim = source.indexOf("coordinator.createClaim(requestId");
        int settlementLock = source.indexOf("ReentrantLock settlementLock");
        assertTrue(claimLookup >= 0);
        assertTrue(claimPreview > settlementLock);
        assertTrue(preflight > claimPreview);
        assertTrue(claimBegin > preflight);
        assertTrue(preflight > claimLookup);
        assertTrue(createClaim > claimBegin);
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

    @Test
    void adminShopBuySecuresOutputBeforeChargingAndFinalizesCustody() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp", "server", "shop",
                "PlayerShopBlockService.java")));

        int adminPath = source.indexOf("private static void handleAdminShopBuy");
        int salePrepare = source.indexOf("saleEscrow.prepare", adminPath);
        int saleRemoved = source.indexOf("saleEscrow.markRemoved", salePrepare);
        int debit = source.indexOf("coordinatorMutationWithCustody", saleRemoved);
        int delivery = source.indexOf("ShopTransactionUtil.insertIntoInventory(buyer.getInventory(), delivered)", debit);
        int custodyClaim = source.indexOf("coordinator.claimCustody(custodyId)", delivery);
        int barterRemoved = source.indexOf("barterEscrow.markRemoved", saleRemoved);
        int barterStored = source.indexOf("barterEscrow.markStored", barterRemoved);
        int barterComplete = source.indexOf("barterEscrow.markComplete", delivery);
        int saleClaim = source.indexOf("saleEscrow.markClaimed", custodyClaim);
        assertTrue(adminPath >= 0);
        assertTrue(salePrepare > adminPath);
        assertTrue(saleRemoved > salePrepare);
        assertTrue(debit > saleRemoved);
        assertTrue(barterRemoved > saleRemoved);
        assertTrue(barterStored > barterRemoved);
        assertTrue(delivery > debit);
        assertTrue(barterComplete > delivery);
        assertTrue(custodyClaim > delivery);
        assertTrue(saleClaim > custodyClaim);
        assertTrue(source.contains("admin shop delivery requires recovery"));
    }

    @Test
    void buybackRemovalMismatchCannotSilentlyRefundUnrestoredItems() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp", "server", "shop",
                "PlayerShopBlockService.java")));

        int firstMismatch = source.indexOf("if (!itemEscrow.markRemoved(itemEscrowRequestId, taken");
        int secondMismatch = source.indexOf("if (!itemEscrow.markRemoved(itemEscrowRequestId, paymentStacks");
        assertTrue(firstMismatch >= 0);
        assertTrue(secondMismatch > firstMismatch);

        String adminMismatch = source.substring(firstMismatch, secondMismatch);
        String playerMismatch = source.substring(secondMismatch,
                source.indexOf("// Capacity was simulated", secondMismatch));
        assertTrue(adminMismatch.contains("ShopTransactionUtil.canFit(seller.getInventory(), taken)"));
        assertTrue(adminMismatch.contains("itemEscrow.markRecoveryRequired(itemEscrowRequestId)"));
        assertTrue(playerMismatch.contains("ShopTransactionUtil.canFit(seller.getInventory(), paymentStacks)"));
        assertTrue(playerMismatch.contains("itemEscrow.markRecoveryRequired(itemEscrowRequestId)"));

        int storageFailure = source.indexOf("if (!inserted) {", secondMismatch);
        assertTrue(storageFailure > secondMismatch);
        String storageMismatch = source.substring(storageFailure,
                source.indexOf("if (!itemEscrow.markStored", storageFailure));
        assertTrue(storageMismatch.contains("ShopTransactionUtil.canFit(seller.getInventory(), paymentStacks)"));
        assertTrue(storageMismatch.contains("itemEscrow.markRecoveryRequired(itemEscrowRequestId)"));
    }

    @Test
    void confirmedSettlementClaimCannotSkipLocalFinalization() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp", "server", "shop",
                "PlayerShopBlockService.java")));

        int claimStart = source.indexOf("case \"CLAIM_SETTLEMENT\"");
        int claimEnd = source.indexOf("default ->", claimStart);
        assertTrue(claimStart >= 0 && claimEnd > claimStart);
        String claimPath = source.substring(claimStart, claimEnd);
        assertTrue(claimPath.contains("coordinator.deliverClaim(requestId)"));
        assertTrue(claimPath.contains("coordinator.resolveClaim(requestId)"));
        assertTrue(claimPath.contains("delivered.state() != ClaimState.DELIVERED"));
        assertTrue(claimPath.contains("resolved.state() != ClaimState.RESOLVED"));
        assertTrue(claimPath.contains("coordinator.markRecoveryRequired"));
        assertTrue(claimPath.contains("settlement record finalization requires recovery"));
    }

    @Test
    void serverShopDeliveryAndSellRestoreNeverDropOrForgetCustody() throws Exception {
        String buySource = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp", "server", "transaction",
                "ShopBuyService.java")));
        String sellSource = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp", "server", "transaction",
                "ShopSellService.java")));

        assertFalse(buySource.contains("player.drop(stack, false)"));
        assertTrue(buySource.contains("coordinator.markRecoveryRequired(\"shop delivery requires recovery\")"));
        assertTrue(buySource.contains("coordinator.markRecoveryRequired(\"shop delivery claim requires recovery\")"));
        assertTrue(buySource.contains("ShopResultCode.RECOVERY_REQUIRED"));
        assertTrue(sellSource.contains("CustodyState.HELD, false"));
        assertTrue(sellSource.contains("boolean restored = ShopTransactionUtil.insertIntoInventory"));
        assertTrue(sellSource.contains("sell item restoration requires recovery"));
        assertTrue(sellSource.contains("sell compensation item restoration requires recovery"));
        assertTrue(sellSource.contains("coordinator.markRecoveryRequired(\"sell compensation requires recovery\")"));
        assertTrue(sellSource.contains("sell compensation custody release requires recovery"));
        int compensationRequest = sellSource.indexOf("requestId.child(\"sell compensation\")");
        int compensationKind = sellSource.indexOf("MutationKind.WITHDRAW", compensationRequest);
        int compensationCall = sellSource.indexOf("coordinator.withdraw(compensationRequest)", compensationKind);
        assertTrue(compensationRequest >= 0);
        assertTrue(compensationKind > compensationRequest);
        assertTrue(compensationCall > compensationKind);
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
