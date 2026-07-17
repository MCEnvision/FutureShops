package com.enviouse.futureshops.server.escrow.custody;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustodyRepositoryTest {
    @Test
    void reserveReleaseAndConsumeRequestsAreIdempotent() {
        CustodyRepository repository = new CustodyRepository();
        CustodyLot first = CustodyTestFixtures.itemLot("reserve one", 5);
        CustodyOperationResult reserved = repository.reserve(first);

        assertFalse(reserved.replayed());
        assertTrue(repository.reserve(first).replayed());

        CustodyTransferEvidence releaseEvidence = CustodyTestFixtures.terminalEvidence("release one");
        CustodyOperationResult released = repository.release(first.lotId(), "release one",
                releaseEvidence, CustodyTestFixtures.NOW.plusSeconds(1));
        assertEquals(CustodyLotState.RELEASED, released.lot().state());
        assertTrue(repository.release(first.lotId(), "release one", releaseEvidence,
                CustodyTestFixtures.NOW.plusSeconds(1)).replayed());
        assertTrue(repository.reserve(first).replayed());

        CustodyLot second = CustodyTestFixtures.walletLot("reserve two", 25L);
        repository.reserve(second);
        CustodyTransferEvidence consumeEvidence = CustodyTestFixtures.terminalEvidence("consume two");
        assertEquals(CustodyLotState.CONSUMED, repository.consume(second.lotId(), "consume two",
                consumeEvidence, CustodyTestFixtures.NOW.plusSeconds(2)).lot().state());
        assertTrue(repository.consume(second.lotId(), "consume two", consumeEvidence,
                CustodyTestFixtures.NOW.plusSeconds(2)).replayed());
    }

    @Test
    void duplicateRequestWithDifferentContentFailsClosed() {
        CustodyRepository repository = new CustodyRepository();
        repository.reserve(CustodyTestFixtures.itemLot("same request", 1));

        assertThrows(CustodyConflictException.class,
                () -> repository.reserve(CustodyTestFixtures.itemLot("same request", 2)));
    }

    @Test
    void liabilitiesAndConservationTrackEveryTerminalPath() {
        CustodyRepository repository = new CustodyRepository();
        CustodyLot wallet = CustodyTestFixtures.walletLot("wallet reserve", 80L);
        CustodyLot protectedCash = CustodyTestFixtures.protectedCurrencyLot("cash reserve", 20L, 2);
        CustodyLot foreignCash = CustodyTestFixtures.foreignCurrencyLot("foreign reserve", 60L, 3);
        CustodyLot items = CustodyTestFixtures.itemLot("item reserve", 4);
        repository.reserve(wallet);
        repository.reserve(protectedCash);
        repository.reserve(foreignCash);
        repository.reserve(items);

        CustodyLiabilityReport liabilities = repository.outstandingLiabilities();
        assertEquals(80L, liabilities.walletReservedMinorUnits());
        assertEquals(40L, liabilities.protectedCurrencyOutstandingMinorUnits());
        assertEquals(60L, liabilities.foreignCurrencyOutstandingMinorUnits());
        assertEquals(4L, liabilities.itemUnitsOutstanding());
        assertEquals(4L, liabilities.heldLotCount());

        repository.release(items.lotId(), "item release",
                CustodyTestFixtures.terminalEvidence("item release"),
                CustodyTestFixtures.NOW.plusSeconds(1));
        repository.consume(wallet.lotId(), "wallet consume",
                CustodyTestFixtures.terminalEvidence("wallet consume"),
                CustodyTestFixtures.NOW.plusSeconds(1));
        repository.quarantine(foreignCash.lotId(), "foreign quarantine",
                CustodyTestFixtures.terminalEvidence("foreign quarantine"),
                CustodyTestFixtures.NOW.plusSeconds(1));

        CustodyConservationReport conservation = repository.conservation();
        assertTrue(conservation.conserved());
        assertTrue(conservation.violations().isEmpty());
        assertEquals(1L, repository.outstandingLiabilities().heldLotCount());
    }

    @Test
    void protectedAndForeignCurrencyRulesCannotBeMixed() {
        CustodyLot protectedCash = CustodyTestFixtures.protectedCurrencyLot("protected", 25L, 2);
        CustodyLot foreignCash = CustodyTestFixtures.foreignCurrencyLot("foreign", 50L, 2);

        assertEquals(CustodyProtectionTier.PROTECTED, protectedCash.protectionTier());
        assertEquals(1, protectedCash.protectedProvenance().size());
        assertEquals(2, protectedCash.protectedProvenance().get(0).billCount());
        assertEquals(CustodyProtectionTier.UNPROTECTED_FOREIGN, foreignCash.protectionTier());
        assertTrue(foreignCash.protectedProvenance().isEmpty());
        assertEquals(CustodyAdapterCapability.UNPROTECTED_EXTERNAL, foreignCash.sourceCapability());

        assertThrows(IllegalArgumentException.class, () -> CustodyLot.held(UUID.randomUUID(),
                UUID.randomUUID(), "bad foreign", CustodyAssetType.FOREIGN_PHYSICAL_CURRENCY,
                CustodyProtectionTier.PROTECTED, 50L, CustodyLot.BUILT_IN_CURRENCY_PROVIDER,
                foreignCash.itemSnapshots(), protectedCash.protectedProvenance(),
                foreignCash.holdEvidence(), CustodyTestFixtures.NOW));
        assertThrows(IllegalArgumentException.class, () -> new ProtectedCurrencyProvenance(
                UUID.randomUUID(), 25L, 1, 2, "test server", "test checksum"));
        assertThrows(IllegalArgumentException.class,
                () -> CustodyBatchPlan.create(CustodyOperation.RESERVE, "mixed batch",
                        List.of(protectedCash, foreignCash)));
    }

    @Test
    void allOrNothingSimulationStopsBeforePartialCapacityMutation() {
        CustodyLot lot = CustodyTestFixtures.itemLot("capacity lot", 10);
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "capacity batch", List.of(lot));
        CapacityAdapter adapter = new CapacityAdapter(9L, false);

        AtomicInteger prepared = new AtomicInteger();
        CustodyBatchExecutionResult result = new CustodyBatchExecutor().execute(adapter, plan,
                Map.of(lot.lotId(), lot.holdEvidence()), CustodyTestFixtures.NOW,
                intent -> prepared.incrementAndGet());

        assertFalse(result.simulation().accepted());
        assertFalse(result.applied());
        assertFalse(adapter.applyCalled);
        assertEquals(0, prepared.get());
    }

    @Test
    void adapterPartialApplyReportFailsClosed() {
        CustodyLot first = CustodyTestFixtures.itemLot("partial one", 2);
        CustodyLot other = CustodyTestFixtures.itemLot("partial two", 3);
        CustodyLot second = new CustodyLot(other.lotId(), first.transactionId(),
                other.reserveRequestKey(), other.assetType(), other.protectionTier(),
                other.sourceCapability(), other.state(), other.units(), other.currencyProvider(),
                other.itemSnapshots(), other.protectedProvenance(), other.assetFingerprint(),
                other.holdEvidence(), other.createdAt(), other.updatedAt(), other.revision());
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "partial batch", List.of(first, second));
        CapacityAdapter adapter = new CapacityAdapter(5L, true);
        AtomicInteger prepared = new AtomicInteger();

        assertThrows(CustodyConflictException.class,
                () -> new CustodyBatchExecutor().execute(adapter, plan,
                        Map.of(first.lotId(), first.holdEvidence(),
                                second.lotId(), second.holdEvidence()),
                        CustodyTestFixtures.NOW, intent -> prepared.incrementAndGet()));
        assertEquals(2, prepared.get());
    }

    @Test
    void failedPrepareCommitCannotReachAdapterMutation() {
        CustodyLot lot = CustodyTestFixtures.itemLot("journal failure", 2);
        CustodyBatchPlan plan = CustodyBatchPlan.create(CustodyOperation.RESERVE,
                "journal failure batch", List.of(lot));
        CapacityAdapter adapter = new CapacityAdapter(2L, false);

        assertThrows(IllegalStateException.class,
                () -> new CustodyBatchExecutor().execute(adapter, plan,
                        Map.of(lot.lotId(), lot.holdEvidence()), CustodyTestFixtures.NOW,
                        intent -> {
                            throw new IllegalStateException("Journal write failed");
                        }));
        assertFalse(adapter.applyCalled);
    }

    @Test
    void batchPreflightUsesBoundedPointLookupsWithLargeUnrelatedState() {
        CustodyRepository repository = new CustodyRepository(
                5_000, 10_000, CustodyRepository.DEFAULT_MAXIMUM_NBT_BYTES);
        for (int index = 0; index < 2_000; index++) {
            repository.reserve(CustodyTestFixtures.itemLot(
                    "unrelated batch state " + index, 1));
        }
        CustodyLot candidate = CustodyTestFixtures.itemLot("bounded batch candidate", 1);

        List<CustodyOperationResult> result = repository.preflightBatch(
                List.of(CustodyMutation.reserve(candidate)));

        assertFalse(result.get(0).replayed());
        assertTrue(repository.lastBatchProbeCount() <= 3L);
        assertEquals(null, repository.get(candidate.lotId()));
    }

    @Test
    void reconciliationClassifiesExactAndChangedSnapshots() {
        CustodyLot lot = CustodyTestFixtures.itemLot("reconcile lot", 3);
        CustodyReconciler reconciler = new CustodyReconciler();

        CustodyReconciliationResult exact = reconciler.reconcile(lot, lot.itemSnapshots(),
                new byte[]{2}, new byte[]{4}, CustodyTestFixtures.NOW.plusSeconds(1));
        assertEquals(CustodyReconciliationStatus.MATCHED, exact.status());
        assertFalse(exact.requiresManualReview());

        CustodyItemSnapshot changed = CustodyItemSnapshot.capture("minecraft:diamond", 3,
                new byte[]{10, 0, 99});
        CustodyReconciliationResult mismatch = reconciler.reconcile(lot, List.of(changed),
                new byte[]{2}, new byte[]{4}, CustodyTestFixtures.NOW.plusSeconds(2));
        assertEquals(CustodyReconciliationStatus.ASSET_MISMATCH, mismatch.status());
        assertTrue(mismatch.requiresManualReview());
    }

    private static final class CapacityAdapter implements CustodyAdapter {
        private final long capacity;
        private final boolean reportPartial;
        private boolean applyCalled;

        private CapacityAdapter(long capacity, boolean reportPartial) {
            this.capacity = capacity;
            this.reportPartial = reportPartial;
        }

        @Override
        public String adapterId() {
            return "player_inventory";
        }

        @Override
        public CustodyAdapterCapability capability() {
            return CustodyAdapterCapability.RECONCILABLE;
        }

        @Override
        public CustodySimulationResult simulate(CustodyBatchPlan plan) {
            return capacity >= plan.requiredUnits()
                    ? CustodySimulationResult.accepted(plan.requiredUnits(), capacity, "simulation")
                    : CustodySimulationResult.rejected(plan.requiredUnits(), capacity,
                    "Insufficient exact capacity");
        }

        @Override
        public CustodyAdapterApplyResult apply(CustodyBatchPlan plan, String simulationToken) {
            applyCalled = true;
            Set<UUID> appliedIds = reportPartial
                    ? Set.of(plan.lots().get(0).lotId())
                    : plan.lotIds();
            Map<UUID, CustodyTransferEvidence> evidence = appliedIds.stream().collect(
                    java.util.stream.Collectors.toUnmodifiableMap(id -> id,
                            id -> CustodyTestFixtures.terminalEvidence("adapter apply")));
            return new CustodyAdapterApplyResult(true, appliedIds, evidence, "");
        }

        @Override
        public CustodyAdapterInspection inspect(String simulationToken) {
            return applyCalled
                    ? CustodyAdapterInspection.unknown("Test adapter does not retain evidence")
                    : CustodyAdapterInspection.notApplied("Test adapter was not called");
        }
    }
}
