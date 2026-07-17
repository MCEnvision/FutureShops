package com.enviouse.futureshops.server.escrow.custody;

import java.util.Objects;
import java.util.UUID;

public record ProtectedCurrencyProvenance(
        UUID mintId,
        long denominationMinorUnits,
        int authorizedCount,
        int billCount,
        String serverIdentityEvidence,
        String checksumEvidence
) {
    public static final int MAX_AUTHORIZED_COUNT = 4096;
    public static final int MAX_SERVER_EVIDENCE_LENGTH = 256;
    public static final int MAX_CHECKSUM_EVIDENCE_LENGTH = 512;

    public ProtectedCurrencyProvenance {
        Objects.requireNonNull(mintId, "mintId");
        serverIdentityEvidence = requireText(serverIdentityEvidence,
                MAX_SERVER_EVIDENCE_LENGTH, "server identity evidence");
        checksumEvidence = requireText(checksumEvidence,
                MAX_CHECKSUM_EVIDENCE_LENGTH, "checksum evidence");
        if (denominationMinorUnits <= 0L || authorizedCount <= 0
                || authorizedCount > MAX_AUTHORIZED_COUNT
                || billCount <= 0 || billCount > authorizedCount) {
            throw new IllegalArgumentException("Protected currency provenance quantity is invalid");
        }
        Math.multiplyExact(denominationMinorUnits, (long) billCount);
    }

    public long totalMinorUnits() {
        return Math.multiplyExact(denominationMinorUnits, (long) billCount);
    }

    private static String requireText(String value, int maximumLength, String label) {
        String normalized = Objects.requireNonNull(value, label);
        if (normalized.isEmpty() || normalized.length() > maximumLength
                || !normalized.equals(normalized.trim())) {
            throw new IllegalArgumentException("Protected currency " + label + " is invalid");
        }
        CustodyHashes.strictUtf8(normalized);
        return normalized;
    }
}
