package com.enviouse.futureshops.server.escrow.custody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CustodyBatchCommit(
        CustodyPreparedBatch batch,
        List<CustodyMutation> mutations
) {
    public CustodyBatchCommit {
        Objects.requireNonNull(batch, "batch");
        mutations = List.copyOf(Objects.requireNonNull(mutations, "mutations"));
        if (batch.status() != CustodyBatchStatus.APPLIED && !mutations.isEmpty()) {
            throw new IllegalArgumentException("Only applied custody batches can contain mutations");
        }
        if (batch.status() == CustodyBatchStatus.APPLIED) {
            if (mutations.size() != batch.operations().size()) {
                throw new IllegalArgumentException("Applied custody batch mutations are incomplete");
            }
            Map<String, CustodyPreparedOperation> operations = new HashMap<>();
            for (CustodyPreparedOperation operation : batch.operations()) {
                operations.put(operation.requestKey(), operation);
            }
            for (CustodyMutation mutation : mutations) {
                CustodyPreparedOperation operation = operations.remove(
                        mutation.receipt().requestKey());
                if (operation == null
                        || mutation.receipt().operation() != operation.operation()
                        || !mutation.receipt().lotId().equals(operation.lotSnapshot().lotId())
                        || !mutation.receipt().transactionId().equals(batch.transactionId())
                        || !mutation.receipt().evidence().equals(operation.plannedEvidence())
                        || (operation.operation() != CustodyOperation.RESERVE
                        && mutation.receipt().createdAt().isBefore(batch.preparedAt()))
                        || mutation.receipt().createdAt().isAfter(batch.updatedAt())) {
                    throw new IllegalArgumentException("Applied custody batch mutation does not match its plan");
                }
                CustodyMutation expected = operation.operation() == CustodyOperation.RESERVE
                        ? CustodyMutation.reserve(operation.lotSnapshot())
                        : CustodyMutation.terminal(operation.lotSnapshot(), operation.operation(),
                        operation.requestKey(), operation.plannedEvidence(),
                        mutation.receipt().createdAt());
                if (!expected.equals(mutation)) {
                    throw new IllegalArgumentException("Applied custody batch mutation state does not match its plan");
                }
            }
            if (!operations.isEmpty()) {
                throw new IllegalArgumentException("Applied custody batch mutation membership is incomplete");
            }
        }
    }

    public static CustodyBatchCommit state(CustodyPreparedBatch batch) {
        return new CustodyBatchCommit(batch, List.of());
    }

    public static CustodyBatchCommit applied(CustodyPreparedBatch batch,
                                             List<CustodyMutation> mutations) {
        return new CustodyBatchCommit(batch, mutations);
    }
}
