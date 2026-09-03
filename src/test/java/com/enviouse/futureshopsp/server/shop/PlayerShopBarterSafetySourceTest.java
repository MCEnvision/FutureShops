package com.enviouse.futureshopsp.server.shop;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopBarterSafetySourceTest {
    @Test
    void pureBarterChecksSettlementAndRollsPaymentBackFromBarterStorage() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "shop", "PlayerShopBlockService.java")));

        assertTrue(source.contains("boolean recorded = PlayerShopSettlementSavedData.get(buyer.getServer())"));
        assertTrue(source.contains("if (!recorded) {"));
        assertTrue(source.contains("rollbackBarterPayment(barterStorage, buyer, barterItem, barterAmount"));
        assertTrue(source.contains("private static boolean rollbackAll(LinkedStorage linkedStorage, LinkedStorage barterStorage"));
        assertTrue(source.contains("rollbackBarterPayment(barterStorage, buyer, barterItem, barterAmount"));
        assertTrue(source.contains("if (!ShopTransactionUtil.canFit(buyer.getInventory(), stacks))"));
        assertTrue(source.contains("private static boolean restorePaymentToBuyer(ServerPlayer buyer, List<ItemStack> stacks)"));
        assertTrue(source.contains("return false;\n        }\n        return ShopTransactionUtil.insertIntoInventory"));
        assertTrue(source.contains("barterEscrow.markRecoveryRequired(barterEscrowRequestId)"));
        assertTrue(source.contains("private static boolean rollbackBarterPayment"));
        int restoreStart = source.indexOf("private static boolean restorePaymentToBuyer");
        int restoreEnd = source.indexOf("private static boolean reinsert", restoreStart);
        assertTrue(restoreStart >= 0 && restoreEnd > restoreStart);
        assertFalse(source.substring(restoreStart, restoreEnd).contains(".drop("));
        assertTrue(source.contains("boolean complete = true;"));
        assertTrue(source.contains("return complete;"));
        assertTrue(source.contains("custodyId = custodyIdFor(transactionId, \"buyer compound debit\")"));
        assertTrue(source.contains("custodyId = custodyIdFor(transactionId, \"buyer debit\")"));
        assertTrue(source.contains("return rootRequest.child(role).child(\"custody\")"));
        assertTrue(source.contains("PlayerShopBarterEscrowSavedData.get(buyer.getServer())"));
        assertTrue(source.contains("barterEscrow.markRemoved(barterEscrowRequestId"));
        assertTrue(source.contains("barterEscrow.markStored(barterEscrowRequestId)"));
        assertTrue(source.contains("barterEscrow.markComplete(barterEscrowRequestId)"));
        assertTrue(source.contains("snapshotMatchingItems"));
        assertFalse(source.contains("rollbackBarterPayment(linkedStorage.handler(),"));
        assertFalse(source.contains("ShopTransactionUtil.insertIntoInventory(buyer.getInventory(), paymentStacks)"));
    }

    @Test
    void settlementSavedDataParticipatesInLifecycleIntegrity() throws Exception {
        String source = Files.readString(projectDirectory().resolve(Path.of(
                "src", "main", "java", "com", "enviouse", "futureshopsp",
                "server", "economy", "BalanceManager.java")));

        assertTrue(source.contains("PlayerShopSettlementSavedData settlements"));
        assertTrue(source.contains("settlements.cleanMarkerValid()"));
        assertTrue(source.contains("settlements.integrityValid()"));
        assertTrue(source.contains("settlements.markUnclean()"));
        assertTrue(source.contains("settlements.markCleanMarker()"));
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
