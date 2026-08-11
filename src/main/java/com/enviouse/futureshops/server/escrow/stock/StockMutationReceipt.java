package com.enviouse.futureshops.server.escrow.stock;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record StockMutationReceipt(
        UUID requestId,
        StockMutationType operation,
        String requestFingerprint,
        long storeRevision,
        Optional<UUID> transactionId,
        Optional<StockKey> stockKey,
        Optional<StockReservationId> reservationId,
        java.util.List<StockReservationId> reservationIds,
        StockMutationOutcome outcome,
        long listingRevision,
        long reservationRevision,
        Instant appliedAt
) {
    public StockMutationReceipt {
        requestId = StockLimits.requireNonzeroUuid(requestId, "stock request identifier");
        operation = Objects.requireNonNull(operation, "operation");
        requestFingerprint = StockLimits.requireFingerprint(requestFingerprint,
                "stock request fingerprint");
        StockLimits.requireRevision(storeRevision, false, "stock store revision");
        if (storeRevision == 0L) {
            throw new IllegalArgumentException("Stock receipt revision must be positive");
        }
        transactionId = Objects.requireNonNull(transactionId, "transactionId");
        transactionId.ifPresent(value -> StockLimits.requireNonzeroUuid(
                value, "stock receipt transaction identifier"));
        stockKey = Objects.requireNonNull(stockKey, "stockKey");
        reservationId = Objects.requireNonNull(reservationId, "reservationId");
        reservationIds = java.util.List.copyOf(Objects.requireNonNull(
                reservationIds, "reservationIds"));
        if (reservationIds.size() > StockLimits.MAX_BATCH_LINES
                || new java.util.HashSet<>(reservationIds).size()
                != reservationIds.size()) {
            throw new IllegalArgumentException(
                    "Stock receipt batch reservation identities are invalid");
        }
        outcome = Objects.requireNonNull(outcome, "outcome");
        StockLimits.requireRevision(listingRevision, true, "stock receipt listing revision");
        StockLimits.requireRevision(reservationRevision, true,
                "stock receipt reservation revision");
        appliedAt = StockLimits.requireInstant(appliedAt, "appliedAt");
        if (operation == StockMutationType.RELOAD_RECONCILE && stockKey.isPresent()) {
            throw new IllegalArgumentException("Reload reconciliation cannot identify one listing");
        }
        if (operation != StockMutationType.RELOAD_RECONCILE
                && !operation.batchOperation() && stockKey.isEmpty()) {
            throw new IllegalArgumentException("Stock mutation receipt lacks a listing");
        }
        boolean reservationOperation = operation == StockMutationType.RESERVE
                || operation == StockMutationType.COMMIT
                || operation == StockMutationType.RELEASE;
        if (reservationOperation != reservationId.isPresent()) {
            throw new IllegalArgumentException("Stock mutation reservation identity is inconsistent");
        }
        boolean transactionOperation = reservationOperation
                || operation.batchOperation();
        if (transactionOperation != transactionId.isPresent()) {
            throw new IllegalArgumentException(
                    "Stock mutation transaction identity is inconsistent");
        }
        if (!operation.batchOperation() && !reservationIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "Single stock mutation has batch reservation identities");
        }
        switch (operation) {
            case SEED -> {
                if (outcome == StockMutationOutcome.INSUFFICIENT_STOCK
                        || listingRevision < 0L || reservationRevision != -1L) {
                    throw new IllegalArgumentException("Invalid stock seed receipt");
                }
            }
            case RESERVE -> {
                boolean applied = outcome == StockMutationOutcome.APPLIED
                        && listingRevision >= 0L && reservationRevision == 0L;
                boolean insufficient = outcome == StockMutationOutcome.INSUFFICIENT_STOCK
                        && listingRevision >= 0L && reservationRevision == -1L;
                if (!applied && !insufficient) {
                    throw new IllegalArgumentException("Invalid stock reserve receipt");
                }
            }
            case COMMIT, RELEASE -> {
                if (outcome == StockMutationOutcome.INSUFFICIENT_STOCK
                        || listingRevision < 0L || reservationRevision != 1L) {
                    throw new IllegalArgumentException("Invalid stock resolution receipt");
                }
            }
            case REFRESH, ADMIN_RESET -> {
                if (outcome != StockMutationOutcome.APPLIED
                        || listingRevision < 0L || reservationRevision != -1L) {
                    throw new IllegalArgumentException("Invalid stock reset receipt");
                }
            }
            case RELOAD_RECONCILE -> {
                if (outcome == StockMutationOutcome.INSUFFICIENT_STOCK
                        || listingRevision != -1L || reservationRevision != -1L) {
                    throw new IllegalArgumentException("Invalid stock reconciliation receipt");
                }
            }
            case RESERVE_BATCH -> {
                boolean applied = outcome == StockMutationOutcome.APPLIED
                        && !reservationIds.isEmpty();
                boolean insufficient = outcome
                        == StockMutationOutcome.INSUFFICIENT_STOCK
                        && reservationIds.isEmpty();
                if ((!applied && !insufficient) || stockKey.isPresent()
                        || reservationId.isPresent() || listingRevision != -1L
                        || reservationRevision != -1L) {
                    throw new IllegalArgumentException(
                            "Invalid stock batch reserve receipt");
                }
            }
            case COMMIT_BATCH, RELEASE_BATCH -> {
                if (reservationIds.isEmpty()
                        || outcome == StockMutationOutcome.INSUFFICIENT_STOCK
                        || stockKey.isPresent() || reservationId.isPresent()
                        || listingRevision != -1L
                        || reservationRevision != -1L) {
                    throw new IllegalArgumentException(
                            "Invalid stock batch resolution receipt");
                }
            }
        }
    }

    public StockMutationReceipt(
            UUID requestId,
            StockMutationType operation,
            String requestFingerprint,
            long storeRevision,
            Optional<StockKey> stockKey,
            Optional<StockReservationId> reservationId,
            StockMutationOutcome outcome,
            long listingRevision,
            long reservationRevision,
            Instant appliedAt
    ) {
        this(requestId, operation, requestFingerprint, storeRevision,
                Optional.empty(), stockKey, reservationId, java.util.List.of(),
                outcome, listingRevision, reservationRevision, appliedAt);
    }
}
