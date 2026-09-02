package com.enviouse.futureshops.server.escrow.stock;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Issue34FiniteInfiniteStockTest {
    private static final Instant NOW = Instant.parse("2026-09-02T08:00:00Z");
    private static final StockKey FINITE = new StockKey(
            "default", "minecraft:iron_ingot");
    private static final StockKey INFINITE = new StockKey(
            "default", "minecraft:bread");

    @Test
    void finiteAndInfinitePurchasesShareTheSameAuthoritativeContract() {
        PersistentStockRepository repository = new PersistentStockRepository(
                fingerprint('a'));
        repository.seed(UUID.randomUUID(), new StockDefinition(
                FINITE, StockPolicy.limited(2L), fingerprint('a')), NOW);
        repository.seed(UUID.randomUUID(), new StockDefinition(
                INFINITE, StockPolicy.unlimitedStock(), fingerprint('a')),
                NOW);

        UUID finiteTransaction = UUID.randomUUID();
        StockApplyResult finiteHold = repository.reserve(
                UUID.randomUUID(), finiteTransaction, FINITE, 2L, 0L,
                NOW.plusSeconds(1));
        assertEquals(StockMutationOutcome.APPLIED,
                finiteHold.receipt().outcome());
        assertEquals(0L, repository.listing(FINITE).availableQuantity());
        repository.commit(UUID.randomUUID(), finiteTransaction,
                StockReservationId.forTransaction(finiteTransaction, FINITE),
                0L, NOW.plusSeconds(2));

        StockApplyResult finiteExhausted = repository.reserve(
                UUID.randomUUID(), UUID.randomUUID(), FINITE, 1L,
                repository.listing(FINITE).revision(),
                NOW.plusSeconds(3));
        assertEquals(StockMutationOutcome.INSUFFICIENT_STOCK,
                finiteExhausted.receipt().outcome());

        UUID infiniteTransaction = UUID.randomUUID();
        StockApplyResult infiniteHold = repository.reserve(
                UUID.randomUUID(), infiniteTransaction, INFINITE,
                StockLimits.MAX_QUANTITY, 0L, NOW.plusSeconds(4));
        assertEquals(StockMutationOutcome.APPLIED,
                infiniteHold.receipt().outcome());
        assertTrue(!repository.reservation(
                StockReservationId.forTransaction(infiniteTransaction, INFINITE))
                .inventoryBacked());
        repository.commit(UUID.randomUUID(), infiniteTransaction,
                StockReservationId.forTransaction(infiniteTransaction, INFINITE),
                0L, NOW.plusSeconds(5));

        assertEquals(0L, repository.listing(FINITE).availableQuantity());
        assertEquals(-1L, repository.listing(INFINITE).displayQuantity());
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void pairedReloadPreservesFiniteAvailabilityAndInfiniteSemantics() {
        PersistentStockRepository repository = new PersistentStockRepository(
                fingerprint('a'));
        repository.seed(UUID.randomUUID(), new StockDefinition(
                FINITE, StockPolicy.limited(4L), fingerprint('a')), NOW);
        repository.seed(UUID.randomUUID(), new StockDefinition(
                INFINITE, StockPolicy.unlimitedStock(), fingerprint('a')),
                NOW);

        repository.reconcileReload(UUID.randomUUID(), java.util.List.of(
                new StockDefinition(FINITE, StockPolicy.limited(4L),
                        fingerprint('a')),
                new StockDefinition(INFINITE, StockPolicy.unlimitedStock(),
                        fingerprint('a'))), fingerprint('a'),
                NOW.plusSeconds(1));

        Map<StockKey, CatalogStockState> listings = repository.snapshot().listings();
        assertEquals(4L, listings.get(FINITE).availableQuantity());
        assertEquals(-1L, listings.get(INFINITE).displayQuantity());
        assertTrue(repository.conservation().conserved());
    }

    private static String fingerprint(char value) {
        return String.valueOf(value).repeat(64);
    }
}
