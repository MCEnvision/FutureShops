package com.enviouse.futureshops.server.escrow.mint;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ProtectedMintJournalEvent(ProtectedMintOperation operation,
                                        String requestKey,
                                        UUID transactionId,
                                        Optional<UUID> targetBatchId,
                                        int quantity,
                                        Optional<ProtectedMintState> sourceState,
                                        Optional<ProtectedMintBatch> batch,
                                        Instant occurredAt) {
    public ProtectedMintJournalEvent {
        Objects.requireNonNull(operation, "operation");
        requestKey = ProtectedMintText.requestKey(requestKey);
        Objects.requireNonNull(transactionId, "transactionId");
        targetBatchId = Objects.requireNonNull(targetBatchId, "targetBatchId");
        sourceState = Objects.requireNonNull(sourceState, "sourceState");
        batch = Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (quantity <= 0 || quantity > ProtectedMintBatch.MAX_AUTHORIZED_COUNT) {
            throw new IllegalArgumentException("Protected mint event quantity is invalid");
        }
        switch (operation) {
            case ISSUE -> requireIssue(requestKey, transactionId, targetBatchId,
                    quantity, sourceState, batch, occurredAt);
            case AUTHORIZE -> requireAuthorize(requestKey, transactionId, targetBatchId,
                    quantity, sourceState, batch, occurredAt);
            case MATERIALIZE -> requireSimple(targetBatchId, sourceState, batch,
                    ProtectedMintState.AUTHORIZED, "materialize");
            case RESERVE -> requireSimple(targetBatchId, sourceState, batch,
                    ProtectedMintState.AVAILABLE, "reserve");
            case COMMIT -> requireSimple(targetBatchId, sourceState, batch,
                    ProtectedMintState.RESERVED, "commit");
            case REFUND -> requireRefund(requestKey, transactionId, targetBatchId,
                    quantity, sourceState, batch, occurredAt);
            case QUARANTINE -> {
                if (targetBatchId.isEmpty() || batch.isPresent()
                        || sourceState.isEmpty()
                        || sourceState.orElseThrow() != ProtectedMintState.AUTHORIZED
                        && sourceState.orElseThrow() != ProtectedMintState.AVAILABLE
                        && sourceState.orElseThrow() != ProtectedMintState.RESERVED) {
                    throw new IllegalArgumentException("Protected mint quarantine event is invalid");
                }
            }
        }
    }

    public static ProtectedMintJournalEvent authorize(ProtectedMintBatch batch) {
        Objects.requireNonNull(batch, "batch");
        return new ProtectedMintJournalEvent(ProtectedMintOperation.AUTHORIZE,
                batch.authorizeRequestKey(), batch.transactionId(), Optional.empty(),
                batch.authorizedCount(), Optional.empty(), Optional.of(batch),
                batch.authorizedAt());
    }

    public static ProtectedMintJournalEvent issue(ProtectedMintBatch batch) {
        Objects.requireNonNull(batch, "batch");
        return new ProtectedMintJournalEvent(ProtectedMintOperation.ISSUE,
                batch.authorizeRequestKey(), batch.transactionId(), Optional.empty(),
                batch.authorizedCount(), Optional.empty(), Optional.of(batch),
                batch.updatedAt());
    }

    public static ProtectedMintJournalEvent materialize(UUID transactionId, UUID batchId,
                                                        String requestKey, int quantity,
                                                        Instant occurredAt) {
        return simple(ProtectedMintOperation.MATERIALIZE, transactionId, batchId,
                requestKey, quantity, ProtectedMintState.AUTHORIZED, occurredAt);
    }

    public static ProtectedMintJournalEvent reserve(UUID transactionId, UUID batchId,
                                                    String requestKey, int quantity,
                                                    Instant occurredAt) {
        return simple(ProtectedMintOperation.RESERVE, transactionId, batchId,
                requestKey, quantity, ProtectedMintState.AVAILABLE, occurredAt);
    }

    public static ProtectedMintJournalEvent commit(UUID transactionId, UUID batchId,
                                                   String requestKey, int quantity,
                                                   Instant occurredAt) {
        return simple(ProtectedMintOperation.COMMIT, transactionId, batchId,
                requestKey, quantity, ProtectedMintState.RESERVED, occurredAt);
    }

    public static ProtectedMintJournalEvent refund(UUID transactionId, UUID sourceBatchId,
                                                   String requestKey,
                                                   ProtectedMintState sourceState,
                                                   int quantity,
                                                   ProtectedMintBatch replacementBatch,
                                                   Instant occurredAt) {
        return new ProtectedMintJournalEvent(ProtectedMintOperation.REFUND,
                requestKey, transactionId, Optional.of(sourceBatchId), quantity,
                Optional.of(sourceState), Optional.of(replacementBatch), occurredAt);
    }

    public static ProtectedMintJournalEvent quarantine(UUID transactionId, UUID batchId,
                                                       String requestKey,
                                                       ProtectedMintState sourceState,
                                                       int quantity, Instant occurredAt) {
        return new ProtectedMintJournalEvent(ProtectedMintOperation.QUARANTINE,
                requestKey, transactionId, Optional.of(batchId), quantity,
                Optional.of(sourceState), Optional.empty(), occurredAt);
    }

    private static ProtectedMintJournalEvent simple(ProtectedMintOperation operation,
                                                     UUID transactionId, UUID batchId,
                                                     String requestKey, int quantity,
                                                     ProtectedMintState sourceState,
                                                     Instant occurredAt) {
        return new ProtectedMintJournalEvent(operation, requestKey, transactionId,
                Optional.of(batchId), quantity, Optional.of(sourceState),
                Optional.empty(), occurredAt);
    }

    private static void requireAuthorize(String requestKey, UUID transactionId,
                                         Optional<UUID> targetBatchId, int quantity,
                                         Optional<ProtectedMintState> sourceState,
                                         Optional<ProtectedMintBatch> batch,
                                         Instant occurredAt) {
        if (targetBatchId.isPresent() || sourceState.isPresent() || batch.isEmpty()) {
            throw new IllegalArgumentException("Protected mint authorize event is invalid");
        }
        ProtectedMintBatch value = batch.orElseThrow();
        if (!value.transactionId().equals(transactionId)
                || !value.authorizeRequestKey().equals(requestKey)
                || !value.authorizedAt().equals(occurredAt)
                || value.revision() != 0L || value.authorizedQuantity() != quantity
                || value.authorizedCount() != quantity) {
            throw new IllegalArgumentException("Protected mint authorize event does not match batch");
        }
    }

    private static void requireIssue(String requestKey, UUID transactionId,
                                     Optional<UUID> targetBatchId, int quantity,
                                     Optional<ProtectedMintState> sourceState,
                                     Optional<ProtectedMintBatch> batch,
                                     Instant occurredAt) {
        if (targetBatchId.isPresent() || sourceState.isPresent() || batch.isEmpty()) {
            throw new IllegalArgumentException("Protected mint issue event is invalid");
        }
        ProtectedMintBatch value = batch.orElseThrow();
        if (!value.transactionId().equals(transactionId)
                || !value.authorizeRequestKey().equals(requestKey)
                || !value.batchId().equals(ProtectedMintIds.batchId(transactionId, requestKey))
                || !value.authorizedAt().equals(occurredAt)
                || !value.updatedAt().equals(occurredAt)
                || value.revision() != 1L || value.authorizedQuantity() != 0
                || value.availableQuantity() != quantity
                || value.authorizedCount() != quantity
                || !value.reservedQuantities().isEmpty()
                || !value.spentQuantities().isEmpty()
                || value.refundedQuantity() != 0
                || value.quarantinedQuantity() != 0
                || value.replacementForBatchId().isPresent()) {
            throw new IllegalArgumentException("Protected mint issue event does not match batch");
        }
    }

    private static void requireSimple(Optional<UUID> targetBatchId,
                                      Optional<ProtectedMintState> sourceState,
                                      Optional<ProtectedMintBatch> batch,
                                      ProtectedMintState requiredSource,
                                      String label) {
        if (targetBatchId.isEmpty() || batch.isPresent()
                || sourceState.isEmpty() || sourceState.orElseThrow() != requiredSource) {
            throw new IllegalArgumentException("Protected mint " + label + " event is invalid");
        }
    }

    private static void requireRefund(String requestKey, UUID transactionId,
                                      Optional<UUID> targetBatchId, int quantity,
                                      Optional<ProtectedMintState> sourceState,
                                      Optional<ProtectedMintBatch> batch,
                                      Instant occurredAt) {
        if (targetBatchId.isEmpty() || sourceState.isEmpty() || batch.isEmpty()
                || sourceState.orElseThrow() != ProtectedMintState.RESERVED
                && sourceState.orElseThrow() != ProtectedMintState.SPENT) {
            throw new IllegalArgumentException("Protected mint refund event is invalid");
        }
        ProtectedMintBatch replacement = batch.orElseThrow();
        if (!replacement.transactionId().equals(transactionId)
                || !replacement.authorizeRequestKey().equals(requestKey)
                || !replacement.authorizedAt().equals(occurredAt)
                || replacement.authorizedCount() != quantity
                || replacement.replacementForBatchId().isEmpty()
                || !replacement.replacementForBatchId().orElseThrow()
                .equals(targetBatchId.orElseThrow())
                || replacement.revision() != 0L) {
            throw new IllegalArgumentException("Protected mint refund replacement is invalid");
        }
    }
}
