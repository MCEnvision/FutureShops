package com.enviouse.futureshops.server.escrow.stock;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public sealed interface StockMutationCommand permits
        StockMutationCommand.Seed,
        StockMutationCommand.Reserve,
        StockMutationCommand.Resolve,
        StockMutationCommand.DefinitionChange,
        StockMutationCommand.Reconcile,
        StockMutationCommand.ReserveBatch,
        StockMutationCommand.ResolveBatch {

    UUID requestId();

    StockMutationType operation();

    Instant appliedAt();

    record Seed(
            UUID requestId,
            StockDefinition definition,
            Instant appliedAt
    ) implements StockMutationCommand {
        public Seed {
            requestId = requireRequest(requestId);
            definition = Objects.requireNonNull(definition, "definition");
            appliedAt = requireTime(appliedAt);
        }

        @Override
        public StockMutationType operation() {
            return StockMutationType.SEED;
        }
    }

    record Reserve(
            UUID requestId,
            UUID transactionId,
            StockKey stockKey,
            long quantity,
            long expectedListingRevision,
            Instant appliedAt
    ) implements StockMutationCommand {
        public Reserve {
            requestId = requireRequest(requestId);
            transactionId = StockLimits.requireNonzeroUuid(transactionId,
                    "stock transaction identifier");
            stockKey = Objects.requireNonNull(stockKey, "stockKey");
            StockLimits.requireQuantity(quantity, false,
                    "stock reserve quantity");
            StockLimits.requireRevision(expectedListingRevision, false,
                    "expected stock listing revision");
            appliedAt = requireTime(appliedAt);
        }

        @Override
        public StockMutationType operation() {
            return StockMutationType.RESERVE;
        }
    }

    record Resolve(
            UUID requestId,
            StockMutationType operation,
            UUID transactionId,
            StockReservationId reservationId,
            long expectedReservationRevision,
            Instant appliedAt
    ) implements StockMutationCommand {
        public Resolve {
            requestId = requireRequest(requestId);
            if (operation != StockMutationType.COMMIT
                    && operation != StockMutationType.RELEASE) {
                throw new IllegalArgumentException(
                        "Invalid stock resolution command");
            }
            transactionId = StockLimits.requireNonzeroUuid(transactionId,
                    "stock transaction identifier");
            reservationId = Objects.requireNonNull(
                    reservationId, "reservationId");
            StockLimits.requireRevision(expectedReservationRevision, false,
                    "expected stock reservation revision");
            appliedAt = requireTime(appliedAt);
        }
    }

    record DefinitionChange(
            UUID requestId,
            StockMutationType operation,
            StockDefinition definition,
            long expectedListingRevision,
            Instant appliedAt
    ) implements StockMutationCommand {
        public DefinitionChange {
            requestId = requireRequest(requestId);
            if (operation != StockMutationType.REFRESH
                    && operation != StockMutationType.ADMIN_RESET) {
                throw new IllegalArgumentException(
                        "Invalid stock definition change command");
            }
            definition = Objects.requireNonNull(definition, "definition");
            StockLimits.requireRevision(expectedListingRevision, false,
                    "expected stock listing revision");
            appliedAt = requireTime(appliedAt);
        }
    }

    record Reconcile(
            UUID requestId,
            List<StockDefinition> definitions,
            String catalogFingerprint,
            Instant appliedAt
    ) implements StockMutationCommand {
        public Reconcile {
            requestId = requireRequest(requestId);
            definitions = List.copyOf(Objects.requireNonNull(
                    definitions, "definitions"));
            if (definitions.size() > StockLimits.MAX_BATCH_LINES) {
                throw new IllegalArgumentException(
                        "Stock reconcile command exceeds its line limit");
            }
            definitions = definitions.stream()
                    .map(value -> Objects.requireNonNull(value,
                            "stock definition"))
                    .sorted(java.util.Comparator.comparing(
                            StockDefinition::key)).toList();
            if (new HashSet<>(definitions.stream().map(
                    StockDefinition::key).toList()).size()
                    != definitions.size()) {
                throw new IllegalArgumentException(
                        "Stock reconcile command repeats a listing");
            }
            catalogFingerprint = StockLimits.requireFingerprint(
                    catalogFingerprint, "stock catalog fingerprint");
            appliedAt = requireTime(appliedAt);
        }

        @Override
        public StockMutationType operation() {
            return StockMutationType.RELOAD_RECONCILE;
        }
    }

    record ReserveBatch(
            UUID requestId,
            UUID transactionId,
            List<StockReservationRequest> reservations,
            Instant appliedAt
    ) implements StockMutationCommand {
        public ReserveBatch {
            requestId = requireRequest(requestId);
            transactionId = StockLimits.requireNonzeroUuid(transactionId,
                    "stock transaction identifier");
            reservations = copyReservationRequests(reservations);
            appliedAt = requireTime(appliedAt);
        }

        @Override
        public StockMutationType operation() {
            return StockMutationType.RESERVE_BATCH;
        }
    }

    record ResolveBatch(
            UUID requestId,
            StockMutationType operation,
            UUID transactionId,
            List<StockReservationResolution> reservations,
            Instant appliedAt
    ) implements StockMutationCommand {
        public ResolveBatch {
            requestId = requireRequest(requestId);
            if (operation != StockMutationType.COMMIT_BATCH
                    && operation != StockMutationType.RELEASE_BATCH) {
                throw new IllegalArgumentException(
                        "Invalid stock batch resolution command");
            }
            transactionId = StockLimits.requireNonzeroUuid(transactionId,
                    "stock transaction identifier");
            reservations = copyReservationResolutions(reservations);
            appliedAt = requireTime(appliedAt);
        }
    }

    private static UUID requireRequest(UUID requestId) {
        return StockLimits.requireNonzeroUuid(requestId,
                "stock request identifier");
    }

    private static Instant requireTime(Instant appliedAt) {
        return StockLimits.requireInstant(appliedAt, "stock command time");
    }

    private static List<StockReservationRequest> copyReservationRequests(
            List<StockReservationRequest> values
    ) {
        List<StockReservationRequest> copied = List.copyOf(
                Objects.requireNonNull(values, "reservations")).stream()
                .map(value -> Objects.requireNonNull(value,
                        "stock reservation request"))
                .sorted().toList();
        if (copied.isEmpty() || copied.size() > StockLimits.MAX_BATCH_LINES
                || new HashSet<>(copied.stream().map(
                StockReservationRequest::stockKey).toList()).size()
                != copied.size()) {
            throw new IllegalArgumentException(
                    "Stock reservation command lines are invalid");
        }
        return copied;
    }

    private static List<StockReservationResolution>
    copyReservationResolutions(List<StockReservationResolution> values) {
        List<StockReservationResolution> copied = List.copyOf(
                Objects.requireNonNull(values, "reservations")).stream()
                .map(value -> Objects.requireNonNull(value,
                        "stock reservation resolution"))
                .sorted().toList();
        if (copied.isEmpty() || copied.size() > StockLimits.MAX_BATCH_LINES
                || new HashSet<>(copied.stream().map(
                StockReservationResolution::reservationId).toList()).size()
                != copied.size()) {
            throw new IllegalArgumentException(
                    "Stock resolution command lines are invalid");
        }
        return copied;
    }
}
