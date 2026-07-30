package com.enviouse.futureshops.server.shop;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopOfferUsageRecoverySourceTest {
    @Test
    void usageReconciliationTracksEveryEscrowMutation()
            throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/shop/"
                        + "PlayerShopEscrowTransactionService.java"));
        String savedData = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/"
                        + "runtime/PlayerShopEscrowSavedData.java"));

        assertTrue(service.contains(
                "escrow.mutationRevision()"));
        assertTrue(service.contains(
                "USAGE_RECONCILED_REVISIONS.put(server, revision)"));
        assertTrue(service.contains(
                "entry.snapshot().commit() != null"));
        assertTrue(savedData.contains(
                "mutationRevision = Math.addExact("
                        + "mutationRevision, 1L)"));
    }

    @Test
    void committedUsageIsRecordedBeforeTheNextOfferLimitCheck()
            throws Exception {
        String service = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/shop/"
                        + "PlayerShopEscrowTransactionService.java"));
        int quote = service.indexOf(
                "private static NormalizedQuote quoteOffer(");
        int reconcile = service.indexOf(
                "reconcileOfferUsage(actor.getServer())", quote);
        int require = service.indexOf(
                "requireUsage(actor, usageShopKey(identity)", quote);

        assertTrue(quote >= 0);
        assertTrue(reconcile > quote);
        assertTrue(require > reconcile);
    }
}
