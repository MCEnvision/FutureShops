package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.playershop.PlayerShopBackendException;
import com.enviouse.futureshops.server.escrow.playershop.PlayerShopSettlementImportEvidence;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShopSettlementEscrowServiceTest {
    @Test
    void crashAfterSeedMakesNewRequestResumeOriginalImport() {
        UUID requestA = UUID.randomUUID();
        UUID requestB = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID shop = UUID.randomUUID();
        String key = "minecraft.overworld.1.64.1";
        PlayerShopSettlementImportEvidence durable =
                PlayerShopSettlementImportEvidence.capture(requestA, owner,
                        shop, key, 500L, 75L);

        PlayerShopSettlementImportEvidence selected =
                PlayerShopSettlementEscrowService.selectPendingImport(
                        List.of(durable), owner, shop, key).orElseThrow();

        assertEquals(requestA, selected.requestId());
        assertNotEquals(requestB, selected.requestId());
        assertEquals(PlayerShopSettlementEscrowService
                        .LegacyCleanupDisposition.CLEAR,
                PlayerShopSettlementEscrowService.legacyCleanupDisposition(
                        75L, selected.pendingMinorUnits()));
    }

    @Test
    void duplicateDurableOwnersFailClosedInsteadOfSeedingAgain() {
        UUID owner = UUID.randomUUID();
        UUID shop = UUID.randomUUID();
        String key = "minecraft.overworld.1.64.1";
        PlayerShopSettlementImportEvidence first =
                PlayerShopSettlementImportEvidence.capture(
                        UUID.randomUUID(), owner, shop, key, 500L, 75L);
        PlayerShopSettlementImportEvidence second =
                PlayerShopSettlementImportEvidence.capture(
                        UUID.randomUUID(), owner, shop, key, 500L, 75L);

        assertThrows(PlayerShopBackendException.class, () ->
                PlayerShopSettlementEscrowService.selectPendingImport(
                        List.of(first, second), owner, shop, key));
    }

    @Test
    void legacyCleanupIsIdempotentAfterDurableSeed() {
        assertEquals(PlayerShopSettlementEscrowService
                        .LegacyCleanupDisposition.CLEAR,
                PlayerShopSettlementEscrowService.legacyCleanupDisposition(
                        75L, 75L));
        assertEquals(PlayerShopSettlementEscrowService
                        .LegacyCleanupDisposition.ALREADY_CLEARED,
                PlayerShopSettlementEscrowService.legacyCleanupDisposition(
                        0L, 75L));
        assertEquals(PlayerShopSettlementEscrowService
                        .LegacyCleanupDisposition.CONFLICT,
                PlayerShopSettlementEscrowService.legacyCleanupDisposition(
                        80L, 75L));
    }

    @Test
    void durableSeedIsWrittenBeforeLegacyCleanup() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/enviouse/futureshops/server/escrow/runtime/PlayerShopSettlementEscrowService.java"));
        int seed = source.indexOf("runtime.commitLedger(seed);");
        int cleanup = source.indexOf("clearLegacySettlement();", seed);

        assertTrue(seed >= 0);
        assertTrue(cleanup > seed);
    }
}
