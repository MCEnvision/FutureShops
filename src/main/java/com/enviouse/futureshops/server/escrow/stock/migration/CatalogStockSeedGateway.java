package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.server.escrow.stock.CatalogStockState;
import com.enviouse.futureshops.server.escrow.stock.CatalogStockStatus;
import com.enviouse.futureshops.server.escrow.stock.PersistentStockRepository;
import com.enviouse.futureshops.server.escrow.stock.StockCommandResult;
import com.enviouse.futureshops.server.escrow.stock.StockConservationReport;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockMutationOutcome;
import com.enviouse.futureshops.server.escrow.stock.StockMutationType;
import com.enviouse.futureshops.server.escrow.stock.StockReservation;
import com.enviouse.futureshops.server.escrow.stock.StockReservationId;
import com.enviouse.futureshops.server.escrow.stock.StockReservationState;
import com.enviouse.futureshops.server.escrow.stock.StockStoreSnapshot;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class CatalogStockSeedGateway {
    private final CatalogStockSeedBackend backend;

    public CatalogStockSeedGateway(CatalogStockSeedBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    public boolean ready() {
        return backend.ready();
    }

    public boolean pristine() {
        StockStoreSnapshot snapshot = backend.snapshot();
        return snapshot.storeRevision() == 0L
                && snapshot.listings().isEmpty()
                && snapshot.reservations().isEmpty()
                && snapshot.receipts().isEmpty()
                && snapshot.catalogFingerprint().equals(
                PersistentStockRepository.EMPTY_CATALOG_FINGERPRINT);
    }

    public CatalogStockImportDisposition importEntry(
            CatalogStockSeedSnapshot snapshot,
            CatalogStockSeedEntry entry,
            Instant now
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(now, "now");
        if (!ready()) {
            return CatalogStockImportDisposition.RETRY_LATER;
        }
        boolean replayed = backend.commit(new StockMutationCommand.Seed(
                CatalogStockMigrationIds.seedRequest(snapshot, entry),
                entry.definition(), now)).replayed();
        long depletion = entry.durableCapacity()
                - entry.availableQuantity();
        if (entry.unlimited() || depletion == 0L) {
            return replayed ? CatalogStockImportDisposition.REPLAYED
                    : CatalogStockImportDisposition.APPLIED;
        }

        UUID transactionId = CatalogStockMigrationIds
                .depletionTransaction(snapshot, entry);
        StockCommandResult reserved = backend.commit(
                new StockMutationCommand.Reserve(
                        CatalogStockMigrationIds.depletionReserveRequest(
                                snapshot, entry),
                        transactionId, entry.key(), depletion,
                        0L, now));
        if (reserved.receipt().outcome()
                != StockMutationOutcome.APPLIED) {
            throw new IllegalStateException(
                    "Catalog stock depletion reservation was rejected");
        }
        StockReservationId reservationId =
                StockReservationId.forTransaction(
                        transactionId, entry.key());
        StockCommandResult committed = backend.commit(
                new StockMutationCommand.Resolve(
                        CatalogStockMigrationIds.depletionCommitRequest(
                                snapshot, entry),
                        StockMutationType.COMMIT, transactionId,
                        reservationId, 0L, now));
        if (committed.receipt().outcome()
                != StockMutationOutcome.APPLIED) {
            throw new IllegalStateException(
                    "Catalog stock depletion commit was rejected");
        }
        return replayed && reserved.replayed() && committed.replayed()
                ? CatalogStockImportDisposition.REPLAYED
                : CatalogStockImportDisposition.APPLIED;
    }

    public CatalogStockImportDisposition reconcile(
            CatalogStockSeedSnapshot snapshot,
            Instant now
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(now, "now");
        if (!ready()) {
            return CatalogStockImportDisposition.RETRY_LATER;
        }
        StockCommandResult result = backend.commit(
                new StockMutationCommand.Reconcile(
                        CatalogStockMigrationIds.reconcileRequest(snapshot),
                        snapshot.definitions(), snapshot.fingerprint(), now));
        return result.replayed()
                ? CatalogStockImportDisposition.REPLAYED
                : CatalogStockImportDisposition.APPLIED;
    }

    public CatalogStockVerification verify(
            CatalogStockSeedSnapshot expected
    ) {
        Objects.requireNonNull(expected, "expected");
        StockStoreSnapshot actual = backend.snapshot();
        if (!actual.catalogFingerprint().equals(expected.fingerprint())) {
            return invalid(actual, "Catalog stock checksum differs");
        }
        if (actual.listings().size() != expected.entries().size()) {
            return invalid(actual, "Catalog stock listing count differs");
        }
        java.util.LinkedHashMap<StockReservationId, CatalogStockSeedEntry>
                expectedReservations = new java.util.LinkedHashMap<>();
        java.util.LinkedHashSet<UUID> expectedReceipts =
                new java.util.LinkedHashSet<>();
        for (CatalogStockSeedEntry expectedEntry : expected.entries()) {
            expectedReceipts.add(CatalogStockMigrationIds.seedRequest(
                    expected, expectedEntry));
            CatalogStockState state = actual.listings()
                    .get(expectedEntry.key());
            if (state == null
                    || state.status() != CatalogStockStatus.ACTIVE
                    || !state.policy().equals(
                    expectedEntry.definition().policy())
                    || !state.configFingerprint().equals(
                    expectedEntry.configFingerprint())
                    || state.availableQuantity()
                    != expectedEntry.availableQuantity()) {
                return invalid(actual,
                        "Catalog stock listing differs from its seed");
            }
            long depletion = expectedEntry.durableCapacity()
                    - expectedEntry.availableQuantity();
            if (!expectedEntry.unlimited() && depletion > 0L) {
                UUID transactionId = CatalogStockMigrationIds
                        .depletionTransaction(expected, expectedEntry);
                expectedReservations.put(
                        StockReservationId.forTransaction(
                                transactionId, expectedEntry.key()),
                        expectedEntry);
                expectedReceipts.add(CatalogStockMigrationIds
                        .depletionReserveRequest(expected, expectedEntry));
                expectedReceipts.add(CatalogStockMigrationIds
                        .depletionCommitRequest(expected, expectedEntry));
            }
        }
        if (actual.reservations().size()
                != expectedReservations.size()) {
            return invalid(actual,
                    "Catalog stock migration reservation count differs");
        }
        for (Map.Entry<StockReservationId, CatalogStockSeedEntry> value
                : expectedReservations.entrySet()) {
            StockReservation reservation = actual.reservations()
                    .get(value.getKey());
            CatalogStockSeedEntry entry = value.getValue();
            long expectedQuantity = entry.durableCapacity()
                    - entry.availableQuantity();
            if (reservation == null
                    || reservation.state()
                    != StockReservationState.COMMITTED
                    || !reservation.stockKey().equals(entry.key())
                    || reservation.quantity() != expectedQuantity
                    || !reservation.transactionId().equals(
                    CatalogStockMigrationIds.depletionTransaction(
                            expected, entry))) {
                return invalid(actual,
                        "Catalog stock migration reservation differs");
            }
        }
        expectedReceipts.add(
                CatalogStockMigrationIds.reconcileRequest(expected));
        Set<UUID> actualReceipts = actual.receipts().keySet().stream()
                .collect(Collectors.toUnmodifiableSet());
        if (!actualReceipts.equals(expectedReceipts)) {
            return invalid(actual,
                    "Catalog stock migration receipt set differs");
        }
        StockConservationReport conservation = backend.conservation();
        if (!conservation.conserved()) {
            return invalid(actual,
                    "Catalog stock conservation failed");
        }
        if (conservation.finiteAvailableQuantity()
                != expected.finiteAvailableQuantity()) {
            return invalid(actual,
                    "Catalog stock finite total differs");
        }
        long unlimited = actual.listings().values().stream()
                .filter(CatalogStockState::unlimited).count();
        if (unlimited != expected.unlimitedListings()) {
            return invalid(actual,
                    "Catalog stock unlimited total differs");
        }
        return CatalogStockVerification.valid(actual.storeRevision());
    }

    public CatalogStockVerification verifyRestoredLineage(
            CatalogStockSeedSnapshot expected
    ) {
        Objects.requireNonNull(expected, "expected");
        StockStoreSnapshot actual = backend.snapshot();
        for (CatalogStockSeedEntry entry : expected.entries()) {
            CatalogStockState state = actual.listings().get(entry.key());
            if (state == null || !state.configFingerprint().equals(
                    entry.configFingerprint())) {
                return invalid(actual,
                        "Catalog stock seed identity is missing");
            }
            if (!actual.receipts().containsKey(
                    CatalogStockMigrationIds.seedRequest(expected, entry))) {
                return invalid(actual,
                        "Catalog stock seed receipt is missing");
            }
            long depletion = entry.durableCapacity()
                    - entry.availableQuantity();
            if (!entry.unlimited() && depletion > 0L) {
                UUID transactionId = CatalogStockMigrationIds
                        .depletionTransaction(expected, entry);
                StockReservationId reservationId =
                        StockReservationId.forTransaction(
                                transactionId, entry.key());
                StockReservation reservation = actual.reservations().get(
                        reservationId);
                if (reservation == null
                        || reservation.state()
                        != StockReservationState.COMMITTED
                        || reservation.quantity() != depletion
                        || !reservation.transactionId().equals(
                        transactionId)
                        || !actual.receipts().containsKey(
                        CatalogStockMigrationIds.depletionReserveRequest(
                                expected, entry))
                        || !actual.receipts().containsKey(
                        CatalogStockMigrationIds.depletionCommitRequest(
                                expected, entry))) {
                    return invalid(actual,
                            "Catalog stock depletion lineage is missing");
                }
            }
        }
        if (!actual.receipts().containsKey(
                CatalogStockMigrationIds.reconcileRequest(expected))) {
            return invalid(actual,
                    "Catalog stock reconcile lineage is missing");
        }
        StockConservationReport conservation = backend.conservation();
        if (!conservation.conserved()) {
            return invalid(actual,
                    "Catalog stock conservation failed");
        }
        return CatalogStockVerification.valid(actual.storeRevision());
    }

    private static CatalogStockVerification invalid(
            StockStoreSnapshot snapshot,
            String detail
    ) {
        return CatalogStockVerification.invalid(
                snapshot.storeRevision(), detail);
    }
}
