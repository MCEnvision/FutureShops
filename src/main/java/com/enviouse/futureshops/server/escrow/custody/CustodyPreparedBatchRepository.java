package com.enviouse.futureshops.server.escrow.custody;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

final class CustodyPreparedBatchRepository {
    static final int MAX_BATCHES = 1_000_000;
    static final int MAX_QUERY_LIMIT = 10_000;

    private final Map<UUID, CustodyPreparedBatch> batches = new HashMap<>();
    private final Map<String, UUID> requestIndex = new HashMap<>();
    private final Map<String, UUID> operationRequestIndex = new HashMap<>();
    private final NavigableMap<BatchOrderKey, UUID> unresolved = new TreeMap<>(
            Comparator.comparing(BatchOrderKey::preparedAt)
                    .thenComparing(value -> value.batchId().toString()));

    synchronized CustodyPreparedBatchResult preflight(CustodyPreparedBatch batch) {
        return evaluate(batch, false);
    }

    synchronized CustodyPreparedBatchResult apply(CustodyPreparedBatch batch) {
        return evaluate(batch, true);
    }

    private CustodyPreparedBatchResult evaluate(CustodyPreparedBatch candidate,
                                                 boolean commit) {
        Objects.requireNonNull(candidate, "candidate");
        CustodyPreparedBatch current = batches.get(candidate.batchId());
        UUID requestBatchId = requestIndex.get(candidate.requestKey());
        if (current == null && requestBatchId != null) {
            throw new CustodyConflictException("Custody batch request key was reused");
        }
        if (current == null) {
            requireNewBatch(candidate);
            if (batches.size() >= MAX_BATCHES) {
                throw new CustodyConflictException("Prepared custody batch limit is exceeded");
            }
            if (commit) {
                putNew(candidate);
            }
            return new CustodyPreparedBatchResult(candidate, false);
        }
        if (current.equals(candidate)) {
            return new CustodyPreparedBatchResult(current, true);
        }
        if (candidate.status() == CustodyBatchStatus.PREPARED
                && candidate.revision() == 0L
                && samePreparation(current, candidate)) {
            return new CustodyPreparedBatchResult(current, true);
        }
        requireTransition(current, candidate);
        if (commit) {
            if (current.unresolved()) {
                unresolved.remove(BatchOrderKey.from(current));
            }
            batches.put(candidate.batchId(), candidate);
            if (candidate.unresolved()) {
                unresolved.put(BatchOrderKey.from(candidate), candidate.batchId());
            }
        }
        return new CustodyPreparedBatchResult(candidate, false);
    }

    synchronized CustodyPreparedBatch get(UUID batchId) {
        return batches.get(Objects.requireNonNull(batchId, "batchId"));
    }

    synchronized CustodyPreparedBatch forOperationRequest(String requestKey) {
        UUID batchId = operationRequestIndex.get(CustodyLot.requireRequestKey(requestKey));
        return batchId == null ? null : batches.get(batchId);
    }

    synchronized List<CustodyPreparedBatch> unresolved(int limit) {
        if (limit <= 0 || limit > MAX_QUERY_LIMIT) {
            throw new IllegalArgumentException("Invalid custody batch query limit");
        }
        return unresolved.values().stream().map(batches::get).limit(limit).toList();
    }

    synchronized List<CustodyPreparedOperation> unresolvedOperations(int limit) {
        if (limit <= 0 || limit > MAX_QUERY_LIMIT) {
            throw new IllegalArgumentException("Invalid custody operation query limit");
        }
        java.util.ArrayList<CustodyPreparedOperation> operations = new java.util.ArrayList<>();
        for (UUID batchId : unresolved.values()) {
            for (CustodyPreparedOperation operation : batches.get(batchId).operations()) {
                operations.add(operation);
                if (operations.size() == limit) {
                    return List.copyOf(operations);
                }
            }
        }
        return List.copyOf(operations);
    }

    synchronized boolean hasUnresolved() {
        return !unresolved.isEmpty();
    }

    synchronized Map<UUID, CustodyPreparedBatch> snapshot() {
        return Map.copyOf(batches);
    }

    synchronized void restore(Map<UUID, CustodyPreparedBatch> restored) {
        Objects.requireNonNull(restored, "restored");
        if (restored.size() > MAX_BATCHES) {
            throw new CustodyConflictException("Persisted custody batches exceed their limit");
        }
        Map<String, UUID> restoredRequests = new HashMap<>();
        Map<String, UUID> restoredOperations = new HashMap<>();
        for (Map.Entry<UUID, CustodyPreparedBatch> entry : restored.entrySet()) {
            CustodyPreparedBatch batch = Objects.requireNonNull(entry.getValue(), "batch");
            if (!entry.getKey().equals(batch.batchId())
                    || restoredRequests.put(batch.requestKey(), batch.batchId()) != null) {
                throw new CustodyConflictException("Persisted custody batch index is invalid");
            }
            for (CustodyPreparedOperation operation : batch.operations()) {
                if (restoredOperations.put(operation.requestKey(), batch.batchId()) != null) {
                    throw new CustodyConflictException("Persisted custody operation request is duplicated");
                }
            }
        }
        batches.clear();
        requestIndex.clear();
        operationRequestIndex.clear();
        unresolved.clear();
        for (Map.Entry<UUID, CustodyPreparedBatch> entry : restored.entrySet()) {
            putNew(entry.getValue());
        }
    }

    private void requireNewBatch(CustodyPreparedBatch candidate) {
        if (candidate.status() != CustodyBatchStatus.PREPARED || candidate.revision() != 0L) {
            throw new CustodyConflictException("New custody batch must begin prepared");
        }
        if (requestIndex.containsKey(candidate.requestKey())) {
            throw new CustodyConflictException("Custody batch request key was reused");
        }
        for (CustodyPreparedOperation operation : candidate.operations()) {
            if (operationRequestIndex.containsKey(operation.requestKey())) {
                throw new CustodyConflictException("Custody operation request key was reused by another batch");
            }
        }
    }

    private void putNew(CustodyPreparedBatch batch) {
        if (batches.put(batch.batchId(), batch) != null
                || requestIndex.put(batch.requestKey(), batch.batchId()) != null) {
            throw new CustodyConflictException("Duplicate prepared custody batch");
        }
        for (CustodyPreparedOperation operation : batch.operations()) {
            if (operationRequestIndex.put(operation.requestKey(), batch.batchId()) != null) {
                throw new CustodyConflictException("Duplicate custody batch operation request");
            }
        }
        if (batch.unresolved()) {
            unresolved.put(BatchOrderKey.from(batch), batch.batchId());
        }
    }

    private static void requireTransition(CustodyPreparedBatch current,
                                          CustodyPreparedBatch candidate) {
        if (!samePreparation(current, candidate)
                || candidate.revision() != Math.addExact(current.revision(), 1L)
                || candidate.updatedAt().isBefore(current.updatedAt())) {
            throw new CustodyConflictException("Custody batch transition does not match current state");
        }
        boolean valid = switch (current.status()) {
            case PREPARED -> candidate.status() == CustodyBatchStatus.APPLYING
                    || candidate.status() == CustodyBatchStatus.NOT_APPLIED
                    || candidate.status() == CustodyBatchStatus.QUARANTINED;
            case APPLYING -> candidate.status() == CustodyBatchStatus.APPLIED
                    || candidate.status() == CustodyBatchStatus.NOT_APPLIED
                    || candidate.status() == CustodyBatchStatus.QUARANTINED;
            case APPLIED, NOT_APPLIED, QUARANTINED -> false;
        };
        if (!valid) {
            throw new CustodyConflictException("Custody batch state transition is invalid");
        }
    }

    private static boolean samePreparation(CustodyPreparedBatch first,
                                           CustodyPreparedBatch second) {
        return first.batchId().equals(second.batchId())
                && first.transactionId().equals(second.transactionId())
                && first.requestKey().equals(second.requestKey())
                && first.operations().equals(second.operations())
                && first.preparedAt().equals(second.preparedAt());
    }

    private record BatchOrderKey(Instant preparedAt, UUID batchId) {
        private static BatchOrderKey from(CustodyPreparedBatch batch) {
            return new BatchOrderKey(batch.preparedAt(), batch.batchId());
        }
    }
}
