package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.server.escrow.stock.PersistentStockRepository;
import com.enviouse.futureshops.server.escrow.stock.StockDefinition;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockLimits;
import com.enviouse.futureshops.server.escrow.stock.StockPolicy;
import com.enviouse.futureshops.server.escrow.stock.StockReservationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogStockMutationPlannerTest {
    private static final Instant NOW =
            Instant.parse("2026-07-17T12:00:00Z");
    private static final StockKey KEY =
            new StockKey("default", "offer");
    private static final StockDefinition IDENTITY =
            new StockDefinition(KEY, StockPolicy.limited(10L),
                    "a".repeat(64));

    @Test
    void administrativeResetPreservesHoldsAndSetsExactVisibleQuantity() {
        PersistentStockRepository repository = seeded();
        UUID transactionId = UUID.randomUUID();
        repository.reserve(UUID.randomUUID(), transactionId, KEY,
                4L, 0L, NOW.plusSeconds(1));

        StockDefinition reset = CatalogStockMutationPlanner
                .adminResetDefinition(IDENTITY, 7L,
                        repository.snapshot());
        repository.adminReset(UUID.randomUUID(), reset,
                repository.listing(KEY).revision(), NOW.plusSeconds(2));

        assertEquals(11L, reset.policy().configuredQuantity());
        assertEquals(7L, repository.listing(KEY).availableQuantity());
        assertEquals(4L, repository.backedHeldQuantity(KEY));
        repository.release(UUID.randomUUID(), transactionId,
                StockReservationId.forTransaction(transactionId, KEY),
                0L, NOW.plusSeconds(3));
        assertEquals(11L, repository.listing(KEY).availableQuantity());
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void scheduledRefreshUsesConfiguredCapacityWithoutErasingHolds() {
        PersistentStockRepository repository = seeded();
        repository.reserve(UUID.randomUUID(), UUID.randomUUID(), KEY,
                4L, 0L, NOW.plusSeconds(1));
        StockDefinition changed = new StockDefinition(KEY,
                StockPolicy.limited(12L), IDENTITY.configFingerprint());

        assertTrue(CatalogStockMutationPlanner.refreshNeeded(
                repository.listing(KEY), changed, repository.snapshot()));
        repository.refresh(UUID.randomUUID(), changed,
                repository.listing(KEY).revision(), NOW.plusSeconds(2));

        assertEquals(8L, repository.listing(KEY).availableQuantity());
        assertEquals(4L, repository.backedHeldQuantity(KEY));
        assertFalse(CatalogStockMutationPlanner.refreshNeeded(
                repository.listing(KEY), changed, repository.snapshot()));
    }

    @Test
    void resetBoundsIncludeEveryOutstandingHold() {
        PersistentStockRepository repository = seeded();
        repository.reserve(UUID.randomUUID(), UUID.randomUUID(), KEY,
                1L, 0L, NOW.plusSeconds(1));

        assertThrows(IllegalArgumentException.class,
                () -> CatalogStockMutationPlanner.adminResetDefinition(
                        IDENTITY, StockLimits.MAX_QUANTITY,
                        repository.snapshot()));
    }

    @Test
    void productionRequestIdsBindRevisionAndTarget() {
        UUID first = CatalogStockProductionIds.refresh(IDENTITY, 3L);

        assertEquals(first,
                CatalogStockProductionIds.refresh(IDENTITY, 3L));
        assertNotEquals(first,
                CatalogStockProductionIds.refresh(IDENTITY, 4L));
        assertNotEquals(first,
                CatalogStockProductionIds.adminReset(IDENTITY, 3L));
        assertNotEquals(CatalogStockProductionIds.reload(
                        5L, "b".repeat(64)),
                CatalogStockProductionIds.reload(
                        6L, "b".repeat(64)));
    }

    private static PersistentStockRepository seeded() {
        PersistentStockRepository repository =
                new PersistentStockRepository("b".repeat(64));
        repository.seed(UUID.randomUUID(), IDENTITY, NOW);
        return repository;
    }
}
