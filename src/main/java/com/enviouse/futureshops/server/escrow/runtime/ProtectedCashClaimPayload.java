package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;

import java.util.Objects;
import java.util.UUID;

public record ProtectedCashClaimPayload(
        UUID batchId,
        long denominationMinorUnits,
        int authorizedCount,
        int portionIndex,
        int portionCount,
        int billCount,
        String serverIdentityEvidence,
        String checksumEvidence
) {
    public static final int MAX_STACK_BILLS = 64;
    public static final int MAX_SERVER_EVIDENCE_LENGTH = 256;
    public static final int MAX_CHECKSUM_EVIDENCE_LENGTH = 512;

    public ProtectedCashClaimPayload {
        Objects.requireNonNull(batchId, "batchId");
        serverIdentityEvidence = requireText(
                serverIdentityEvidence, MAX_SERVER_EVIDENCE_LENGTH,
                "Protected cash server evidence is invalid");
        checksumEvidence = requireText(
                checksumEvidence, MAX_CHECKSUM_EVIDENCE_LENGTH,
                "Protected cash checksum evidence is invalid");
        if (denominationMinorUnits <= 0L
                || authorizedCount <= 0
                || authorizedCount > ProtectedMintBatch.MAX_AUTHORIZED_COUNT
                || portionCount <= 0
                || portionCount > authorizedCount
                || portionIndex < 0
                || portionIndex >= portionCount
                || billCount <= 0
                || billCount > MAX_STACK_BILLS
                || billCount > authorizedCount) {
            throw new IllegalArgumentException("Protected cash claim payload is invalid");
        }
        Math.multiplyExact(denominationMinorUnits, (long) billCount);
    }

    public static ProtectedCashClaimPayload fromBatch(
            ProtectedMintBatch batch,
            int portionIndex,
            int portionCount,
            int billCount
    ) {
        Objects.requireNonNull(batch, "batch");
        return new ProtectedCashClaimPayload(
                batch.batchId(), batch.denominationMinorUnits(), batch.authorizedCount(),
                portionIndex, portionCount, billCount,
                batch.serverIdentityEvidence(), batch.checksumEvidence());
    }

    private static String requireText(String value, int maximumLength, String message) {
        String normalized = Objects.requireNonNull(value, "value");
        if (normalized.isEmpty() || normalized.length() > maximumLength
                || !normalized.equals(normalized.strip()) || !wellFormedUtf16(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static boolean wellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                return false;
            }
        }
        return true;
    }
}
