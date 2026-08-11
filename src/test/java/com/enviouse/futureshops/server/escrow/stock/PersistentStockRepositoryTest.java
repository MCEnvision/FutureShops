package com.enviouse.futureshops.server.escrow.stock;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistentStockRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");
    private static final StockKey KEY = new StockKey("default", "minecraft:diamond");

    @Test
    void everyMutationHasExactReplayAndRequestConflictSemantics() {
        PersistentStockRepository repository = new PersistentStockRepository(fingerprint('a'));
        StockDefinition initial = limited(KEY, 20L, 'a');
        UUID seedRequest = UUID.randomUUID();
        StockApplyResult seed = repository.seed(seedRequest, initial, NOW);
        assertFalse(seed.replayed());
        assertTrue(repository.seed(seedRequest, initial, NOW.plusMillis(1)).replayed());
        assertEquals(NOW, repository.receipt(seedRequest).appliedAt());
        assertThrows(StockConflictException.class, () -> repository.seed(seedRequest,
                limited(KEY, 21L, 'a'), NOW));

        UUID firstTransaction = UUID.randomUUID();
        UUID reserveRequest = UUID.randomUUID();
        StockApplyResult reserve = repository.reserve(reserveRequest, firstTransaction, KEY,
                5L, 0L, NOW.plusSeconds(1));
        assertFalse(reserve.replayed());
        assertTrue(repository.reserve(reserveRequest, firstTransaction, KEY, 5L,
                0L, NOW.plusSeconds(8)).replayed());
        assertThrows(StockConflictException.class, () -> repository.reserve(reserveRequest,
                firstTransaction, KEY, 4L, 0L, NOW.plusSeconds(1)));

        StockReservationId firstReservation = StockReservationId.forTransaction(
                firstTransaction, KEY);
        UUID commitRequest = UUID.randomUUID();
        assertFalse(repository.commit(commitRequest, firstTransaction, firstReservation,
                0L, NOW.plusSeconds(2)).replayed());
        assertTrue(repository.commit(commitRequest, firstTransaction, firstReservation,
                0L, NOW.plusSeconds(9)).replayed());

        UUID secondTransaction = UUID.randomUUID();
        repository.reserve(UUID.randomUUID(), secondTransaction, KEY, 3L, 2L,
                NOW.plusSeconds(3));
        StockReservationId secondReservation = StockReservationId.forTransaction(
                secondTransaction, KEY);
        UUID releaseRequest = UUID.randomUUID();
        assertFalse(repository.release(releaseRequest, secondTransaction, secondReservation,
                0L, NOW.plusSeconds(4)).replayed());
        assertTrue(repository.release(releaseRequest, secondTransaction, secondReservation,
                0L, NOW.plusSeconds(10)).replayed());

        UUID refreshRequest = UUID.randomUUID();
        StockDefinition refreshed = limited(KEY, 18L, 'a');
        long beforeRefresh = repository.listing(KEY).revision();
        assertFalse(repository.refresh(refreshRequest, refreshed, beforeRefresh,
                NOW.plusSeconds(5)).replayed());
        assertTrue(repository.refresh(refreshRequest, refreshed, beforeRefresh,
                NOW.plusSeconds(11)).replayed());

        UUID resetRequest = UUID.randomUUID();
        StockDefinition reset = limited(KEY, 12L, 'a');
        long beforeReset = repository.listing(KEY).revision();
        assertFalse(repository.adminReset(resetRequest, reset, beforeReset,
                NOW.plusSeconds(6)).replayed());
        assertTrue(repository.adminReset(resetRequest, reset, beforeReset,
                NOW.plusSeconds(12)).replayed());

        UUID reloadRequest = UUID.randomUUID();
        StockDefinition reloaded = limited(KEY, 8L, 'a');
        assertFalse(repository.reconcileReload(reloadRequest, List.of(reloaded),
                fingerprint('d'), NOW.plusSeconds(7)).replayed());
        assertTrue(repository.reconcileReload(reloadRequest, List.of(reloaded),
                fingerprint('d'), NOW.plusSeconds(13)).replayed());
        assertThrows(StockConflictException.class, () -> repository.reconcileReload(
                reloadRequest, List.of(reloaded), fingerprint('e'), NOW.plusSeconds(7)));
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void multipleHoldsCommitAndReleaseConserveFiniteStock() {
        PersistentStockRepository repository = seeded(10L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        repository.reserve(UUID.randomUUID(), first, KEY, 3L, 0L, NOW.plusSeconds(1));
        repository.reserve(UUID.randomUUID(), second, KEY, 4L, 1L, NOW.plusSeconds(2));

        assertEquals(3L, repository.listing(KEY).availableQuantity());
        assertEquals(7L, repository.backedHeldQuantity(KEY));
        repository.commit(UUID.randomUUID(), first,
                StockReservationId.forTransaction(first, KEY), 0L, NOW.plusSeconds(3));
        repository.release(UUID.randomUUID(), second,
                StockReservationId.forTransaction(second, KEY), 0L, NOW.plusSeconds(4));

        assertEquals(7L, repository.listing(KEY).availableQuantity());
        assertEquals(0L, repository.backedHeldQuantity(KEY));
        assertEquals(3L, repository.conservation().committedQuantity());
        assertEquals(4L, repository.conservation().releasedQuantity());
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void refreshBelowHeldQuantityPreservesEveryReservation() {
        PersistentStockRepository repository = seeded(10L);
        UUID transactionId = UUID.randomUUID();
        repository.reserve(UUID.randomUUID(), transactionId, KEY, 8L, 0L,
                NOW.plusSeconds(1));
        StockReservationId reservationId = StockReservationId.forTransaction(transactionId, KEY);

        repository.refresh(UUID.randomUUID(), limited(KEY, 3L, 'a'), 1L,
                NOW.plusSeconds(2));

        assertEquals(0L, repository.listing(KEY).availableQuantity());
        assertEquals(8L, repository.backedHeldQuantity(KEY));
        assertEquals(StockReservationState.HELD,
                repository.reservation(reservationId).state());
        repository.release(UUID.randomUUID(), transactionId, reservationId, 0L,
                NOW.plusSeconds(3));
        assertEquals(3L, repository.listing(KEY).availableQuantity());
        assertEquals(StockReservationState.RELEASED,
                repository.reservation(reservationId).state());
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void partialReleasesAfterLowerResetNeverExceedTheRemainingTarget() {
        PersistentStockRepository repository = seeded(10L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.reserve(UUID.randomUUID(), first, KEY, 6L, 0L,
                NOW.plusSeconds(1));
        repository.reserve(UUID.randomUUID(), second, KEY, 4L, 1L,
                NOW.plusSeconds(2));
        repository.adminReset(UUID.randomUUID(), limited(KEY, 5L, 'a'), 2L,
                NOW.plusSeconds(3));

        repository.release(UUID.randomUUID(), first,
                StockReservationId.forTransaction(first, KEY), 0L, NOW.plusSeconds(4));
        assertEquals(1L, repository.listing(KEY).availableQuantity());
        assertEquals(4L, repository.backedHeldQuantity(KEY));

        repository.release(UUID.randomUUID(), second,
                StockReservationId.forTransaction(second, KEY), 0L, NOW.plusSeconds(5));
        assertEquals(5L, repository.listing(KEY).availableQuantity());
        assertEquals(0L, repository.backedHeldQuantity(KEY));
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void fullReleaseAfterLowerResetStopsAtTheNewConfiguredQuantity() {
        PersistentStockRepository repository = seeded(10L);
        UUID transactionId = UUID.randomUUID();
        repository.reserve(UUID.randomUUID(), transactionId, KEY, 10L, 0L,
                NOW.plusSeconds(1));
        repository.adminReset(UUID.randomUUID(), limited(KEY, 5L, 'a'), 1L,
                NOW.plusSeconds(2));

        repository.release(UUID.randomUUID(), transactionId,
                StockReservationId.forTransaction(transactionId, KEY), 0L,
                NOW.plusSeconds(3));

        assertEquals(5L, repository.listing(KEY).availableQuantity());
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void reloadReconciliationRetiresMissingListingsWithoutErasingHolds() {
        PersistentStockRepository repository = seeded(6L);
        UUID transactionId = UUID.randomUUID();
        repository.reserve(UUID.randomUUID(), transactionId, KEY, 4L, 0L,
                NOW.plusSeconds(1));

        repository.reconcileReload(UUID.randomUUID(), List.of(), fingerprint('b'),
                NOW.plusSeconds(2));

        CatalogStockState retired = repository.listing(KEY);
        assertEquals(CatalogStockStatus.RETIRED, retired.status());
        assertEquals(0L, retired.availableQuantity());
        assertEquals(4L, repository.backedHeldQuantity(KEY));
        assertThrows(StockConflictException.class, () -> repository.reserve(UUID.randomUUID(),
                UUID.randomUUID(), KEY, 1L, retired.revision(), NOW.plusSeconds(3)));
        StockReservationId reservationId = StockReservationId.forTransaction(transactionId, KEY);
        repository.release(UUID.randomUUID(), transactionId, reservationId, 0L,
                NOW.plusSeconds(3));
        assertEquals(4L, repository.listing(KEY).availableQuantity());
        assertEquals(CatalogStockStatus.RETIRED, repository.listing(KEY).status());
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void policyChangePreservesHeldReservationsAndUnchangedReloadPreservesLiveStock() {
        PersistentStockRepository repository = seeded(10L);
        UUID transactionId = UUID.randomUUID();
        repository.reserve(UUID.randomUUID(), transactionId, KEY, 4L, 0L,
                NOW.plusSeconds(1));
        CatalogStockState before = repository.listing(KEY);

        repository.reconcileReload(UUID.randomUUID(), List.of(limited(KEY, 10L, 'a')),
                fingerprint('a'), NOW.plusSeconds(2));
        assertEquals(before, repository.listing(KEY));

        repository.reconcileReload(UUID.randomUUID(), List.of(limited(KEY, 7L, 'a')),
                fingerprint('b'), NOW.plusSeconds(3));
        CatalogStockState changed = repository.listing(KEY);
        assertEquals(3L, changed.availableQuantity());
        assertEquals(before.revision() + 1L, changed.revision());
        assertEquals(fingerprint('a'), changed.configFingerprint());
        assertEquals(4L, repository.backedHeldQuantity(KEY));
    }

    @Test
    void listingIdentityReuseFailsWithoutChangingAnyDurableState() {
        PersistentStockRepository repository = seeded(10L);
        UUID transactionId = UUID.randomUUID();
        repository.reserve(UUID.randomUUID(), transactionId, KEY, 4L, 0L,
                NOW.plusSeconds(1));
        StockStoreSnapshot before = repository.snapshot();

        assertThrows(StockConflictException.class,
                () -> repository.reconcileReload(UUID.randomUUID(),
                        List.of(limited(KEY, 7L, 'b')),
                        fingerprint('b'), NOW.plusSeconds(2)));

        assertEquals(before, repository.snapshot());
    }

    @Test
    void retiredListingCanReturnWithItsOriginalIdentity() {
        PersistentStockRepository repository = seeded(6L);
        repository.reconcileReload(UUID.randomUUID(), List.of(),
                fingerprint('b'), NOW.plusSeconds(1));

        repository.reconcileReload(UUID.randomUUID(),
                List.of(limited(KEY, 9L, 'a')),
                fingerprint('c'), NOW.plusSeconds(2));

        CatalogStockState restored = repository.listing(KEY);
        assertEquals(CatalogStockStatus.ACTIVE, restored.status());
        assertEquals(9L, restored.availableQuantity());
        assertEquals(fingerprint('a'), restored.configFingerprint());
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void unlimitedReservationsDoNotConsumeFutureFiniteStock() {
        PersistentStockRepository repository = new PersistentStockRepository(fingerprint('a'));
        repository.seed(UUID.randomUUID(), unlimited(KEY, 'a'), NOW);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.reserve(UUID.randomUUID(), first, KEY, StockLimits.MAX_QUANTITY,
                0L, NOW.plusSeconds(1));
        repository.reserve(UUID.randomUUID(), second, KEY, StockLimits.MAX_QUANTITY,
                0L, NOW.plusSeconds(2));

        assertEquals(-1L, repository.listing(KEY).displayQuantity());
        assertFalse(repository.reservationForTransaction(first, KEY).inventoryBacked());
        assertEquals(0L, repository.backedHeldQuantity(KEY));
        repository.reconcileReload(UUID.randomUUID(), List.of(limited(KEY, 5L, 'a')),
                fingerprint('b'), NOW.plusSeconds(3));
        assertEquals(5L, repository.listing(KEY).availableQuantity());
        repository.commit(UUID.randomUUID(), first,
                StockReservationId.forTransaction(first, KEY), 0L, NOW.plusSeconds(4));
        repository.release(UUID.randomUUID(), second,
                StockReservationId.forTransaction(second, KEY), 0L, NOW.plusSeconds(5));
        assertEquals(5L, repository.listing(KEY).availableQuantity());
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void insufficientReservationIsDurablyRejectedAndReplayed() {
        PersistentStockRepository repository = seeded(2L);
        UUID requestId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        StockApplyResult result = repository.reserve(requestId, transactionId, KEY, 3L,
                0L, NOW.plusSeconds(1));

        assertEquals(StockMutationOutcome.INSUFFICIENT_STOCK, result.receipt().outcome());
        assertEquals(2L, repository.listing(KEY).availableQuantity());
        assertEquals(null, repository.reservationForTransaction(transactionId, KEY));
        assertTrue(repository.reserve(requestId, transactionId, KEY, 3L, 0L,
                NOW.plusSeconds(5)).replayed());
    }

    @Test
    void mixedDirectionalBatchReservesAndCommitsAtomically() {
        StockKey outboundKey = new StockKey("default", "minecraft:diamond");
        StockKey inboundKey = new StockKey("default", "minecraft:emerald");
        PersistentStockRepository repository = new PersistentStockRepository(
                fingerprint('a'));
        repository.seed(UUID.randomUUID(), limited(outboundKey, 10L, 'a'), NOW);
        repository.seed(UUID.randomUUID(), limited(inboundKey, 5L, 'a'), NOW);

        UUID priorTransaction = UUID.randomUUID();
        repository.reserve(UUID.randomUUID(), priorTransaction, inboundKey,
                2L, 0L, NOW.plusSeconds(1));
        repository.commit(UUID.randomUUID(), priorTransaction,
                StockReservationId.forTransaction(priorTransaction, inboundKey),
                0L, NOW.plusSeconds(2));
        assertEquals(3L, repository.listing(inboundKey).availableQuantity());

        UUID transactionId = UUID.randomUUID();
        UUID reserveRequest = UUID.randomUUID();
        List<StockReservationRequest> lines = List.of(
                new StockReservationRequest(outboundKey,
                        StockReservationDirection.OUTBOUND, 3L, 0L),
                new StockReservationRequest(inboundKey,
                        StockReservationDirection.INBOUND, 2L, 2L));
        StockBatchApplyResult held = repository.reserveBatch(reserveRequest,
                transactionId, lines, NOW.plusSeconds(3));

        assertEquals(StockMutationOutcome.APPLIED, held.receipt().outcome());
        assertEquals(2, held.reservations().size());
        assertEquals(7L, repository.listing(outboundKey).availableQuantity());
        assertEquals(3L, repository.listing(inboundKey).availableQuantity());
        assertTrue(repository.reserveBatch(reserveRequest, transactionId,
                List.of(lines.get(1), lines.get(0)),
                NOW.plusSeconds(30)).replayed());
        assertThrows(StockConflictException.class, () -> repository.reserveBatch(
                reserveRequest, transactionId, List.of(
                        new StockReservationRequest(outboundKey,
                                StockReservationDirection.INBOUND, 3L, 0L),
                        lines.get(1)), NOW.plusSeconds(3)));

        List<StockReservationResolution> resolutions = held.reservations()
                .stream().map(value -> new StockReservationResolution(
                        value.reservationId(), value.revision())).toList();
        UUID commitRequest = UUID.randomUUID();
        StockBatchApplyResult committed = repository.commitBatch(commitRequest,
                transactionId, resolutions, NOW.plusSeconds(4));
        assertEquals(2, committed.reservations().size());
        assertTrue(committed.reservations().stream().allMatch(value ->
                value.state() == StockReservationState.COMMITTED));
        assertEquals(7L, repository.listing(outboundKey).availableQuantity());
        assertEquals(5L, repository.listing(inboundKey).availableQuantity());
        assertTrue(repository.commitBatch(commitRequest, transactionId,
                List.of(resolutions.get(1), resolutions.get(0)),
                NOW.plusSeconds(40)).replayed());
        assertTrue(repository.conservation().conserved());

        PersistentStockRepository restored = new PersistentStockRepository();
        restored.rebuild(StockStoreSnapshotCodec.decode(
                StockStoreSnapshotCodec.encode(repository.snapshot())));
        assertEquals(repository.snapshot(), restored.snapshot());
        assertTrue(restored.commitBatch(commitRequest, transactionId,
                resolutions, NOW.plusSeconds(50)).replayed());
        assertEquals(2, restored.reservationsForTransaction(
                transactionId).size());
        assertTrue(restored.conservation().conserved());
    }

    @Test
    void insufficientDirectionalBatchLeavesEveryListingAndReservationUntouched() {
        StockKey outboundKey = new StockKey("default", "minecraft:diamond");
        StockKey inboundKey = new StockKey("default", "minecraft:emerald");
        PersistentStockRepository repository = new PersistentStockRepository(
                fingerprint('a'));
        repository.seed(UUID.randomUUID(), limited(outboundKey, 2L, 'a'), NOW);
        repository.seed(UUID.randomUUID(), limited(inboundKey, 5L, 'a'), NOW);
        Map<StockKey, CatalogStockState> beforeListings =
                repository.snapshot().listings();

        UUID requestId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        List<StockReservationRequest> lines = List.of(
                new StockReservationRequest(outboundKey,
                        StockReservationDirection.OUTBOUND, 2L, 0L),
                new StockReservationRequest(inboundKey,
                        StockReservationDirection.INBOUND, 1L, 0L));
        StockBatchApplyResult result = repository.reserveBatch(requestId,
                transactionId, lines, NOW.plusSeconds(1));

        assertEquals(StockMutationOutcome.INSUFFICIENT_STOCK,
                result.receipt().outcome());
        assertTrue(result.reservations().isEmpty());
        assertEquals(beforeListings, repository.snapshot().listings());
        assertTrue(repository.snapshot().reservations().isEmpty());
        assertTrue(repository.reserveBatch(requestId, transactionId, lines,
                NOW.plusSeconds(9)).replayed());
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void releasingDirectionalBatchReturnsOnlyOutboundAvailability() {
        StockKey outboundKey = new StockKey("default", "minecraft:diamond");
        StockKey inboundKey = new StockKey("default", "minecraft:emerald");
        PersistentStockRepository repository = new PersistentStockRepository(
                fingerprint('a'));
        repository.seed(UUID.randomUUID(), limited(outboundKey, 10L, 'a'), NOW);
        repository.seed(UUID.randomUUID(), limited(inboundKey, 5L, 'a'), NOW);
        UUID priorTransaction = UUID.randomUUID();
        repository.reserve(UUID.randomUUID(), priorTransaction, inboundKey,
                2L, 0L, NOW.plusSeconds(1));
        repository.commit(UUID.randomUUID(), priorTransaction,
                StockReservationId.forTransaction(priorTransaction, inboundKey),
                0L, NOW.plusSeconds(2));

        UUID transactionId = UUID.randomUUID();
        StockBatchApplyResult held = repository.reserveBatch(UUID.randomUUID(),
                transactionId, List.of(
                        new StockReservationRequest(outboundKey,
                                StockReservationDirection.OUTBOUND, 3L, 0L),
                        new StockReservationRequest(inboundKey,
                                StockReservationDirection.INBOUND, 2L, 2L)),
                NOW.plusSeconds(3));
        List<StockReservationResolution> resolutions = held.reservations()
                .stream().map(value -> new StockReservationResolution(
                        value.reservationId(), value.revision())).toList();

        StockBatchApplyResult released = repository.releaseBatch(
                UUID.randomUUID(), transactionId, resolutions,
                NOW.plusSeconds(4));

        assertTrue(released.reservations().stream().allMatch(value ->
                value.state() == StockReservationState.RELEASED));
        assertEquals(10L, repository.listing(outboundKey).availableQuantity());
        assertEquals(3L, repository.listing(inboundKey).availableQuantity());
        assertTrue(repository.conservation().conserved());
    }

    @Test
    void concurrentReservationUsesOneAtomicRevisionDecision() throws Exception {
        PersistentStockRepository repository = seeded(1L);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger applied = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();
        Runnable reserve = () -> {
            ready.countDown();
            try {
                start.await();
                StockApplyResult result = repository.reserve(UUID.randomUUID(), UUID.randomUUID(),
                        KEY, 1L, 0L, NOW.plusSeconds(1));
                if (result.receipt().outcome() == StockMutationOutcome.APPLIED) {
                    applied.incrementAndGet();
                }
            } catch (StockConflictException exception) {
                conflicted.incrementAndGet();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };
        Thread first = new Thread(reserve);
        Thread second = new Thread(reserve);
        first.start();
        second.start();
        ready.await();
        start.countDown();
        first.join();
        second.join();

        assertEquals(1, applied.get());
        assertEquals(1, conflicted.get());
        assertEquals(0L, repository.listing(KEY).availableQuantity());
        assertEquals(1, repository.reservationsFor(KEY).size());
    }

    @Test
    void snapshotRebuildRestoresLookupAndCompletedRequestReplay() {
        PersistentStockRepository source = seeded(9L);
        UUID transactionId = UUID.randomUUID();
        UUID reserveRequest = UUID.randomUUID();
        source.reserve(reserveRequest, transactionId, KEY, 3L, 0L, NOW.plusSeconds(1));
        StockStoreSnapshot crashSnapshot = source.snapshot();

        PersistentStockRepository restored = new PersistentStockRepository();
        restored.rebuild(crashSnapshot);

        StockReservationId reservationId = StockReservationId.forTransaction(transactionId, KEY);
        assertNotNull(restored.reservation(reservationId));
        assertEquals(reservationId,
                restored.reservationForTransaction(transactionId, KEY).reservationId());
        assertTrue(restored.reserve(reserveRequest, transactionId, KEY, 3L, 0L,
                NOW.plusSeconds(8)).replayed());
        assertEquals(crashSnapshot, restored.snapshot());
    }

    @Test
    void reloadCapacityFailureLeavesTheEntireRepositoryUnchanged() {
        PersistentStockRepository repository = new PersistentStockRepository(
                2, 10, 20, fingerprint('a'));
        StockKey first = new StockKey("default", "minecraft:stone");
        StockKey second = new StockKey("default", "minecraft:dirt");
        StockKey replacement = new StockKey("default", "minecraft:gold_ingot");
        repository.seed(UUID.randomUUID(), limited(first, 2L, 'a'), NOW);
        repository.seed(UUID.randomUUID(), limited(second, 2L, 'a'), NOW.plusSeconds(1));
        StockStoreSnapshot before = repository.snapshot();

        assertThrows(StockConflictException.class, () -> repository.reconcileReload(
                UUID.randomUUID(), List.of(limited(replacement, 2L, 'b')),
                fingerprint('b'), NOW.plusSeconds(2)));

        assertEquals(before, repository.snapshot());
    }

    @Test
    void quantityRevisionAndSnapshotOverflowFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> StockPolicy.limited(StockLimits.MAX_QUANTITY + 1L));
        assertThrows(IllegalArgumentException.class, () -> new CatalogStockState(KEY,
                StockPolicy.limited(1L), CatalogStockStatus.ACTIVE, 1L, fingerprint('a'),
                StockLimits.MAX_REVISION + 1L, NOW));

        CatalogStockState exhausted = new CatalogStockState(KEY, StockPolicy.limited(1L),
                CatalogStockStatus.ACTIVE, 1L, fingerprint('a'), StockLimits.MAX_REVISION, NOW);
        StockStoreSnapshot snapshot = new StockStoreSnapshot(0L, fingerprint('a'),
                Map.of(KEY, exhausted), Map.of(), Map.of());
        PersistentStockRepository repository = new PersistentStockRepository();
        repository.rebuild(snapshot);
        assertThrows(StockConflictException.class, () -> repository.refresh(UUID.randomUUID(),
                limited(KEY, 1L, 'a'), StockLimits.MAX_REVISION, NOW.plusSeconds(1)));

        UUID transactionId = UUID.randomUUID();
        StockReservation reservation = StockReservation.held(transactionId, KEY, 1L,
                true, NOW);
        StockStoreSnapshot invalid = new StockStoreSnapshot(0L, fingerprint('a'),
                Map.of(KEY, new CatalogStockState(KEY, StockPolicy.limited(
                        StockLimits.MAX_QUANTITY), CatalogStockStatus.ACTIVE,
                        StockLimits.MAX_QUANTITY, fingerprint('a'), 0L, NOW)),
                Map.of(reservation.reservationId(), reservation), Map.of());
        assertThrows(StockConflictException.class,
                () -> PersistentStockRepository.validateSnapshot(invalid));
    }

    private static PersistentStockRepository seeded(long quantity) {
        PersistentStockRepository repository = new PersistentStockRepository(fingerprint('a'));
        repository.seed(UUID.randomUUID(), limited(KEY, quantity, 'a'), NOW);
        return repository;
    }

    private static StockDefinition limited(StockKey key, long quantity, char fingerprint) {
        return new StockDefinition(key, StockPolicy.limited(quantity),
                fingerprint(fingerprint));
    }

    private static StockDefinition unlimited(StockKey key, char fingerprint) {
        return new StockDefinition(key, StockPolicy.unlimitedStock(),
                fingerprint(fingerprint));
    }

    private static String fingerprint(char value) {
        return String.valueOf(value).repeat(64);
    }
}
