package com.enviouse.futureshops.server.escrow.custody;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record CustodyReconciliationResult(
        UUID lotId,
        CustodyReconciliationStatus status,
        byte[] expectedAssetFingerprint,
        byte[] observedAssetFingerprint,
        boolean sourceMatches,
        boolean destinationMatches,
        boolean requiresManualReview,
        String detail,
        Instant checkedAt
) {
    public CustodyReconciliationResult {
        Objects.requireNonNull(lotId, "lotId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(expectedAssetFingerprint, "expectedAssetFingerprint");
        Objects.requireNonNull(observedAssetFingerprint, "observedAssetFingerprint");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(checkedAt, "checkedAt");
        expectedAssetFingerprint = expectedAssetFingerprint.clone();
        observedAssetFingerprint = observedAssetFingerprint.clone();
        CustodyHashes.requireHash(expectedAssetFingerprint, "Expected custody asset fingerprint");
        CustodyHashes.requireHash(observedAssetFingerprint, "Observed custody asset fingerprint");
        detail = detail.strip();
        if (detail.isEmpty() || detail.length() > 2048) {
            throw new IllegalArgumentException("Invalid custody reconciliation detail");
        }
        if ((status == CustodyReconciliationStatus.MATCHED) != !requiresManualReview) {
            throw new IllegalArgumentException("Custody reconciliation review flag is inconsistent");
        }
    }

    @Override
    public byte[] expectedAssetFingerprint() {
        return expectedAssetFingerprint.clone();
    }

    @Override
    public byte[] observedAssetFingerprint() {
        return observedAssetFingerprint.clone();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CustodyReconciliationResult other)) {
            return false;
        }
        return sourceMatches == other.sourceMatches
                && destinationMatches == other.destinationMatches
                && requiresManualReview == other.requiresManualReview
                && lotId.equals(other.lotId)
                && status == other.status
                && Arrays.equals(expectedAssetFingerprint, other.expectedAssetFingerprint)
                && Arrays.equals(observedAssetFingerprint, other.observedAssetFingerprint)
                && detail.equals(other.detail)
                && checkedAt.equals(other.checkedAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(lotId, status, sourceMatches, destinationMatches,
                requiresManualReview, detail, checkedAt);
        result = 31 * result + Arrays.hashCode(expectedAssetFingerprint);
        return 31 * result + Arrays.hashCode(observedAssetFingerprint);
    }
}
