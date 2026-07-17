package com.enviouse.futureshops.server.escrow.claim;

import java.util.Objects;
import java.util.UUID;

public record ClaimLiabilityEntry(UUID claimId,
                                  UUID transactionId,
                                  ClaimLiabilityCategory category,
                                  ClaimStatus status,
                                  long remainingUnits) {
    public ClaimLiabilityEntry {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(status, "status");
        if (remainingUnits <= 0L || status == ClaimStatus.COMPLETED) {
            throw new IllegalArgumentException("Claim liability entry is not unfinished");
        }
    }
}
