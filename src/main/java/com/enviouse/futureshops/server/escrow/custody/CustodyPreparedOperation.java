package com.enviouse.futureshops.server.escrow.custody;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CustodyPreparedOperation(
        UUID intentId,
        CustodyOperation operation,
        String requestKey,
        CustodyLot lotSnapshot,
        String adapterId,
        CustodyAdapterCapability adapterCapability,
        String simulationToken,
        CustodyTransferEvidence plannedEvidence,
        Instant preparedAt,
        CustodyPreparedStatus status,
        Optional<UUID> resolvedReceiptId,
        Optional<Instant> resolvedAt
) {
    public static final int MAX_SIMULATION_TOKEN_LENGTH = 2048;

    public CustodyPreparedOperation {
        Objects.requireNonNull(intentId, "intentId");
        Objects.requireNonNull(operation, "operation");
        requestKey = CustodyLot.requireRequestKey(requestKey);
        Objects.requireNonNull(lotSnapshot, "lotSnapshot");
        Objects.requireNonNull(adapterId, "adapterId");
        Objects.requireNonNull(adapterCapability, "adapterCapability");
        Objects.requireNonNull(simulationToken, "simulationToken");
        Objects.requireNonNull(plannedEvidence, "plannedEvidence");
        Objects.requireNonNull(preparedAt, "preparedAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(resolvedReceiptId, "resolvedReceiptId");
        Objects.requireNonNull(resolvedAt, "resolvedAt");
        adapterId = adapterId.strip();
        simulationToken = simulationToken.strip();
        if (adapterId.isEmpty() || adapterId.length() > CustodyEndpointEvidence.MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Invalid prepared custody adapter ID");
        }
        if (simulationToken.isEmpty()
                || simulationToken.length() > MAX_SIMULATION_TOKEN_LENGTH) {
            throw new IllegalArgumentException("Invalid prepared custody simulation token");
        }
        if (!intentId.equals(deterministicId(requestKey))) {
            throw new IllegalArgumentException("Prepared custody intent ID does not match its request key");
        }
        if (lotSnapshot.state() != CustodyLotState.HELD) {
            throw new IllegalArgumentException("Prepared custody operation requires a held lot snapshot");
        }
        CustodyEndpointEvidence adapterEndpoint = operation == CustodyOperation.RESERVE
                ? plannedEvidence.source() : plannedEvidence.destination();
        if (!adapterEndpoint.adapterId().equals(adapterId)
                || adapterEndpoint.capability() != adapterCapability) {
            throw new IllegalArgumentException("Prepared custody adapter does not match planned evidence");
        }
        if (operation == CustodyOperation.RESERVE
                && (!lotSnapshot.reserveRequestKey().equals(requestKey)
                || !lotSnapshot.holdEvidence().equals(plannedEvidence))) {
            throw new IllegalArgumentException("Prepared custody reserve does not match its lot snapshot");
        }
        if (status == CustodyPreparedStatus.PREPARED) {
            if (resolvedReceiptId.isPresent() || resolvedAt.isPresent()) {
                throw new IllegalArgumentException("Unresolved custody intent has resolution data");
            }
        } else if (resolvedReceiptId.isEmpty() || resolvedAt.isEmpty()
                || resolvedAt.orElseThrow().isBefore(preparedAt)) {
            throw new IllegalArgumentException("Resolved custody intent lacks valid resolution data");
        }
    }

    public static CustodyPreparedOperation prepare(CustodyOperation operation,
                                                   String requestKey,
                                                   CustodyLot lotSnapshot,
                                                   String adapterId,
                                                   CustodyAdapterCapability adapterCapability,
                                                   String simulationToken,
                                                   CustodyTransferEvidence plannedEvidence,
                                                   Instant now) {
        String normalized = CustodyLot.requireRequestKey(requestKey);
        return new CustodyPreparedOperation(deterministicId(normalized), operation, normalized,
                lotSnapshot, adapterId, adapterCapability, simulationToken, plannedEvidence, now,
                CustodyPreparedStatus.PREPARED, Optional.empty(), Optional.empty());
    }

    public CustodyPreparedOperation resolve(CustodyOperationReceipt receipt, Instant now) {
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(now, "now");
        if (receipt.operation() != operation
                || !receipt.requestKey().equals(requestKey)
                || !receipt.lotId().equals(lotSnapshot.lotId())
                || !receipt.transactionId().equals(lotSnapshot.transactionId())
                || !CustodyHashes.equal(receipt.assetFingerprint(), lotSnapshot.assetFingerprint())
                || !receipt.evidence().equals(plannedEvidence)
                || (operation != CustodyOperation.RESERVE
                && receipt.createdAt().isBefore(preparedAt))) {
            throw new CustodyConflictException("Custody receipt does not resolve its prepared intent");
        }
        if (status == CustodyPreparedStatus.RESOLVED) {
            if (!resolvedReceiptId.orElseThrow().equals(receipt.receiptId())) {
                throw new CustodyConflictException("Prepared custody intent resolved to another receipt");
            }
            return this;
        }
        return new CustodyPreparedOperation(intentId, operation, requestKey, lotSnapshot,
                adapterId, adapterCapability, simulationToken, plannedEvidence, preparedAt,
                CustodyPreparedStatus.RESOLVED, Optional.of(receipt.receiptId()), Optional.of(now));
    }

    public static UUID deterministicId(String requestKey) {
        String normalized = CustodyLot.requireRequestKey(requestKey);
        return UUID.nameUUIDFromBytes(CustodyHashes.strictUtf8(
                "futureshops custody prepare " + normalized));
    }
}
