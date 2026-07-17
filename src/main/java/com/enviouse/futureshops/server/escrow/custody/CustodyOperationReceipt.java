package com.enviouse.futureshops.server.escrow.custody;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record CustodyOperationReceipt(
        UUID receiptId,
        UUID lotId,
        UUID transactionId,
        CustodyOperation operation,
        String requestKey,
        Optional<CustodyLotState> previousState,
        CustodyLotState resultingState,
        long units,
        byte[] assetFingerprint,
        CustodyTransferEvidence evidence,
        Instant createdAt
) {
    public CustodyOperationReceipt {
        Objects.requireNonNull(receiptId, "receiptId");
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(operation, "operation");
        requestKey = CustodyLot.requireRequestKey(requestKey);
        Objects.requireNonNull(previousState, "previousState");
        Objects.requireNonNull(resultingState, "resultingState");
        Objects.requireNonNull(assetFingerprint, "assetFingerprint");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(createdAt, "createdAt");
        assetFingerprint = assetFingerprint.clone();
        CustodyHashes.requireHash(assetFingerprint, "Custody receipt asset fingerprint");
        if (units <= 0L) {
            throw new IllegalArgumentException("Custody receipt units must be positive");
        }
        validateState(operation, previousState, resultingState);
        UUID expectedId = deterministicId(requestKey);
        if (!receiptId.equals(expectedId)) {
            throw new IllegalArgumentException("Custody receipt ID does not match its request key");
        }
    }

    public static CustodyOperationReceipt reserve(CustodyLot lot) {
        return new CustodyOperationReceipt(deterministicId(lot.reserveRequestKey()), lot.lotId(),
                lot.transactionId(), CustodyOperation.RESERVE, lot.reserveRequestKey(),
                Optional.empty(), CustodyLotState.HELD, lot.units(), lot.assetFingerprint(),
                lot.holdEvidence(), lot.createdAt());
    }

    public static CustodyOperationReceipt terminal(CustodyLot held,
                                                   CustodyOperation operation,
                                                   String requestKey,
                                                   CustodyTransferEvidence evidence,
                                                   Instant now) {
        CustodyLotState resultingState = switch (operation) {
            case RELEASE -> CustodyLotState.RELEASED;
            case CONSUME -> CustodyLotState.CONSUMED;
            case QUARANTINE -> CustodyLotState.QUARANTINED;
            case RESERVE -> throw new IllegalArgumentException("Reserve is not a terminal custody operation");
        };
        return new CustodyOperationReceipt(deterministicId(requestKey), held.lotId(),
                held.transactionId(), operation, requestKey, Optional.of(CustodyLotState.HELD),
                resultingState, held.units(), held.assetFingerprint(), evidence, now);
    }

    @Override
    public byte[] assetFingerprint() {
        return assetFingerprint.clone();
    }

    static UUID deterministicId(String requestKey) {
        String normalized = CustodyLot.requireRequestKey(requestKey);
        return UUID.nameUUIDFromBytes(("futureshops custody " + normalized)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static void validateState(CustodyOperation operation,
                                      Optional<CustodyLotState> previous,
                                      CustodyLotState result) {
        if (operation == CustodyOperation.RESERVE) {
            if (previous.isPresent() || result != CustodyLotState.HELD) {
                throw new IllegalArgumentException("Reserve receipt has invalid custody states");
            }
            return;
        }
        CustodyLotState expected = switch (operation) {
            case RELEASE -> CustodyLotState.RELEASED;
            case CONSUME -> CustodyLotState.CONSUMED;
            case QUARANTINE -> CustodyLotState.QUARANTINED;
            case RESERVE -> throw new IllegalStateException("Unexpected reserve operation");
        };
        if (previous.orElse(null) != CustodyLotState.HELD || result != expected) {
            throw new IllegalArgumentException("Terminal receipt has invalid custody states");
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CustodyOperationReceipt other)) {
            return false;
        }
        return units == other.units
                && receiptId.equals(other.receiptId)
                && lotId.equals(other.lotId)
                && transactionId.equals(other.transactionId)
                && operation == other.operation
                && requestKey.equals(other.requestKey)
                && previousState.equals(other.previousState)
                && resultingState == other.resultingState
                && Arrays.equals(assetFingerprint, other.assetFingerprint)
                && evidence.equals(other.evidence)
                && createdAt.equals(other.createdAt);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(receiptId, lotId, transactionId, operation, requestKey,
                previousState, resultingState, units, evidence, createdAt)
                + Arrays.hashCode(assetFingerprint);
    }
}
