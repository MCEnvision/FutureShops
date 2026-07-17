package com.enviouse.futureshops.server.escrow.custody;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CustodyPreparedBatch(
        UUID batchId,
        UUID transactionId,
        String requestKey,
        List<CustodyPreparedOperation> operations,
        CustodyBatchStatus status,
        Instant preparedAt,
        Instant updatedAt,
        long revision,
        String detail
) {
    public static final int MAX_DETAIL_LENGTH = 2048;

    public CustodyPreparedBatch {
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(transactionId, "transactionId");
        requestKey = CustodyLot.requireRequestKey(requestKey);
        operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(preparedAt, "preparedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        detail = Objects.requireNonNull(detail, "detail").strip();
        if (operations.isEmpty() || operations.size() > CustodyBatchPlan.MAX_BATCH_LOTS) {
            throw new IllegalArgumentException("Invalid prepared custody batch size");
        }
        if (!batchId.equals(deterministicId(transactionId, requestKey))) {
            throw new IllegalArgumentException("Prepared custody batch ID does not match");
        }
        if (revision < 0L || updatedAt.isBefore(preparedAt)
                || detail.isEmpty() || detail.length() > MAX_DETAIL_LENGTH) {
            throw new IllegalArgumentException("Invalid prepared custody batch state");
        }
        Set<UUID> intentIds = new HashSet<>();
        Set<UUID> lotIds = new HashSet<>();
        Set<String> operationKeys = new HashSet<>();
        CustodyPreparedOperation first = operations.get(0);
        for (CustodyPreparedOperation operation : operations) {
            Objects.requireNonNull(operation, "operation");
            if (!operation.lotSnapshot().transactionId().equals(transactionId)
                    || !operation.simulationToken().equals(first.simulationToken())
                    || operation.operation() != first.operation()
                    || !operation.adapterId().equals(first.adapterId())
                    || operation.adapterCapability() != first.adapterCapability()
                    || operation.lotSnapshot().protectionTier()
                    != first.lotSnapshot().protectionTier()
                    || !operation.preparedAt().equals(preparedAt)
                    || operation.status() != CustodyPreparedStatus.PREPARED
                    || !intentIds.add(operation.intentId())
                    || !lotIds.add(operation.lotSnapshot().lotId())
                    || !operationKeys.add(operation.requestKey())) {
                throw new IllegalArgumentException("Prepared custody batch members do not match");
            }
            String expectedRequestKey = operationRequestKey(first.operation(), requestKey,
                    operations.size(), operation.lotSnapshot());
            if (!operation.requestKey().equals(expectedRequestKey)) {
                throw new IllegalArgumentException("Prepared custody batch request key does not match");
            }
        }
        if ((status == CustodyBatchStatus.PREPARED && revision != 0L)
                || (status != CustodyBatchStatus.PREPARED && revision == 0L)) {
            throw new IllegalArgumentException("Prepared custody batch revision does not match its status");
        }
    }

    public static CustodyPreparedBatch prepare(CustodyBatchPlan plan,
                                               String simulationToken,
                                               Map<UUID, CustodyTransferEvidence> plannedEvidence,
                                               Instant now) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(simulationToken, "simulationToken");
        Objects.requireNonNull(plannedEvidence, "plannedEvidence");
        Objects.requireNonNull(now, "now");
        if (!plannedEvidence.keySet().equals(plan.lotIds())) {
            throw new CustodyConflictException("Custody planned evidence must cover every batch lot");
        }
        UUID transactionId = plan.lots().get(0).transactionId();
        List<CustodyPreparedOperation> operations = new ArrayList<>(plan.lots().size());
        for (CustodyLot lot : plan.lots()) {
            if (!lot.transactionId().equals(transactionId)) {
                throw new CustodyConflictException("Custody batch cannot span transactions");
            }
            String operationKey = operationRequestKey(plan, lot);
            operations.add(CustodyPreparedOperation.prepare(plan.operation(), operationKey, lot,
                    plan.adapterId(), plan.capability(), simulationToken,
                    plannedEvidence.get(lot.lotId()), now));
        }
        return new CustodyPreparedBatch(deterministicId(transactionId, plan.requestKey()),
                transactionId, plan.requestKey(), operations, CustodyBatchStatus.PREPARED,
                now, now, 0L, "Prepared");
    }

    public CustodyPreparedBatch markApplying(long expectedRevision, Instant now) {
        requireTransition(CustodyBatchStatus.PREPARED, expectedRevision, now);
        return transitioned(CustodyBatchStatus.APPLYING, now, "Applying");
    }

    public CustodyPreparedBatch markApplied(long expectedRevision,
                                             Map<UUID, CustodyTransferEvidence> evidenceByLot,
                                             Instant now) {
        requireTransition(CustodyBatchStatus.APPLYING, expectedRevision, now);
        if (!plannedEvidenceByLot().equals(Objects.requireNonNull(evidenceByLot, "evidenceByLot"))) {
            throw new CustodyConflictException("Custody applied evidence does not match its batch plan");
        }
        return transitioned(CustodyBatchStatus.APPLIED, now, "Applied");
    }

    public CustodyPreparedBatch markNotApplied(long expectedRevision, Instant now, String reason) {
        if (status != CustodyBatchStatus.PREPARED && status != CustodyBatchStatus.APPLYING) {
            throw new CustodyConflictException("Only unresolved custody batches can be rejected");
        }
        requireRevisionAndTime(expectedRevision, now);
        return transitioned(CustodyBatchStatus.NOT_APPLIED, now,
                requireDetail(reason));
    }

    public CustodyPreparedBatch quarantine(long expectedRevision, Instant now, String reason) {
        if (status != CustodyBatchStatus.PREPARED && status != CustodyBatchStatus.APPLYING) {
            throw new CustodyConflictException("Only unresolved custody batches can be quarantined");
        }
        requireRevisionAndTime(expectedRevision, now);
        return transitioned(CustodyBatchStatus.QUARANTINED, now,
                requireDetail(reason));
    }

    public Map<UUID, CustodyTransferEvidence> plannedEvidenceByLot() {
        Map<UUID, CustodyTransferEvidence> evidence = new LinkedHashMap<>();
        for (CustodyPreparedOperation operation : operations) {
            evidence.put(operation.lotSnapshot().lotId(), operation.plannedEvidence());
        }
        return Map.copyOf(evidence);
    }

    public Set<UUID> lotIds() {
        Set<UUID> ids = new HashSet<>();
        for (CustodyPreparedOperation operation : operations) {
            ids.add(operation.lotSnapshot().lotId());
        }
        return Set.copyOf(ids);
    }

    public boolean unresolved() {
        return status == CustodyBatchStatus.PREPARED || status == CustodyBatchStatus.APPLYING;
    }

    public CustodyBatchPlan plan() {
        CustodyPreparedOperation first = operations.get(0);
        List<CustodyLot> lots = operations.stream()
                .map(CustodyPreparedOperation::lotSnapshot)
                .toList();
        long requiredUnits = lots.stream().mapToLong(CustodyLot::units)
                .reduce(0L, Math::addExact);
        return new CustodyBatchPlan(first.operation(), requestKey, first.adapterId(),
                first.adapterCapability(), first.lotSnapshot().protectionTier(), lots,
                requiredUnits);
    }

    public static UUID deterministicId(UUID transactionId, String requestKey) {
        Objects.requireNonNull(transactionId, "transactionId");
        String normalized = CustodyLot.requireRequestKey(requestKey);
        return UUID.nameUUIDFromBytes(CustodyHashes.strictUtf8(
                "futureshops custody batch " + transactionId + " " + normalized));
    }

    static String operationRequestKey(CustodyBatchPlan plan, CustodyLot lot) {
        return operationRequestKey(plan.operation(), plan.requestKey(), plan.lots().size(), lot);
    }

    private static String operationRequestKey(CustodyOperation operation,
                                              String batchRequestKey,
                                              int batchSize,
                                              CustodyLot lot) {
        if (operation == CustodyOperation.RESERVE) {
            return lot.reserveRequestKey();
        }
        if (batchSize == 1) {
            return batchRequestKey;
        }
        UUID keyId = UUID.nameUUIDFromBytes(CustodyHashes.strictUtf8("futureshops custody batch "
                + batchRequestKey + " " + lot.lotId()));
        return "custody " + keyId;
    }

    private CustodyPreparedBatch transitioned(CustodyBatchStatus next, Instant now, String nextDetail) {
        return new CustodyPreparedBatch(batchId, transactionId, requestKey, operations,
                next, preparedAt, now, Math.addExact(revision, 1L), nextDetail);
    }

    private void requireTransition(CustodyBatchStatus expected, long expectedRevision, Instant now) {
        if (status != expected) {
            throw new CustodyConflictException("Custody batch is not in the required state");
        }
        requireRevisionAndTime(expectedRevision, now);
    }

    private void requireRevisionAndTime(long expectedRevision, Instant now) {
        Objects.requireNonNull(now, "now");
        if (revision != expectedRevision || now.isBefore(updatedAt)) {
            throw new CustodyConflictException("Custody batch revision or time does not match");
        }
    }

    private static String requireDetail(String value) {
        String normalized = Objects.requireNonNull(value, "detail").strip();
        if (normalized.isEmpty() || normalized.length() > MAX_DETAIL_LENGTH) {
            throw new IllegalArgumentException("Invalid custody batch detail");
        }
        return normalized;
    }
}
