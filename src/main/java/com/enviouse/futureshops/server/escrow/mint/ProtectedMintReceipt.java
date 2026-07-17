package com.enviouse.futureshops.server.escrow.mint;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ProtectedMintReceipt(UUID receiptId, String requestKey,
                                   ProtectedMintOperation operation,
                                   UUID transactionId,
                                   Optional<UUID> sourceBatchId,
                                   Optional<UUID> resultingBatchId,
                                   int quantity,
                                   Optional<ProtectedMintState> sourceState,
                                   byte[] mutationHash, Instant occurredAt) {
    public static final int HASH_BYTES = 32;

    public ProtectedMintReceipt {
        Objects.requireNonNull(receiptId, "receiptId");
        requestKey = ProtectedMintText.requestKey(requestKey);
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(transactionId, "transactionId");
        sourceBatchId = Objects.requireNonNull(sourceBatchId, "sourceBatchId");
        resultingBatchId = Objects.requireNonNull(resultingBatchId, "resultingBatchId");
        sourceState = Objects.requireNonNull(sourceState, "sourceState");
        mutationHash = Arrays.copyOf(Objects.requireNonNull(mutationHash, "mutationHash"),
                mutationHash.length);
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (!receiptId.equals(ProtectedMintIds.receiptId(requestKey))) {
            throw new IllegalArgumentException("Protected mint receipt ID is not deterministic");
        }
        if (quantity <= 0 || quantity > ProtectedMintBatch.MAX_AUTHORIZED_COUNT
                || mutationHash.length != HASH_BYTES || allZero(mutationHash)) {
            throw new IllegalArgumentException("Protected mint receipt contents are invalid");
        }
        switch (operation) {
            case AUTHORIZE, ISSUE -> {
                if (sourceBatchId.isPresent() || resultingBatchId.isEmpty()
                        || sourceState.isPresent()) {
                    throw new IllegalArgumentException("Protected mint creation receipt is invalid");
                }
            }
            case MATERIALIZE, RESERVE, COMMIT, RELEASE, QUARANTINE -> {
                if (sourceBatchId.isEmpty() || resultingBatchId.isEmpty()
                        || !sourceBatchId.equals(resultingBatchId)
                        || sourceState.isEmpty()) {
                    throw new IllegalArgumentException("Protected mint transition receipt is invalid");
                }
            }
            case REFUND -> {
                if (sourceBatchId.isEmpty() || resultingBatchId.isEmpty()
                        || sourceBatchId.equals(resultingBatchId)
                        || sourceState.isEmpty()
                        || sourceState.orElseThrow() != ProtectedMintState.RESERVED
                        && sourceState.orElseThrow() != ProtectedMintState.SPENT) {
                    throw new IllegalArgumentException("Protected mint refund receipt is invalid");
                }
            }
        }
    }

    @Override
    public byte[] mutationHash() {
        return Arrays.copyOf(mutationHash, mutationHash.length);
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) {
            return true;
        }
        if (!(value instanceof ProtectedMintReceipt other)) {
            return false;
        }
        return receiptId.equals(other.receiptId)
                && requestKey.equals(other.requestKey)
                && operation == other.operation
                && transactionId.equals(other.transactionId)
                && sourceBatchId.equals(other.sourceBatchId)
                && resultingBatchId.equals(other.resultingBatchId)
                && quantity == other.quantity
                && sourceState.equals(other.sourceState)
                && Arrays.equals(mutationHash, other.mutationHash)
                && occurredAt.equals(other.occurredAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(receiptId, requestKey, operation, transactionId,
                sourceBatchId, resultingBatchId, quantity, sourceState, occurredAt);
        return 31 * result + Arrays.hashCode(mutationHash);
    }

    private static boolean allZero(byte[] value) {
        int combined = 0;
        for (byte element : value) {
            combined |= element;
        }
        return combined == 0;
    }
}
