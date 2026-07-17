package com.enviouse.futureshops.server.escrow.mint;

import java.util.List;
import java.util.Objects;

public record ProtectedMintLiabilitySnapshot(List<ProtectedMintBatchLiability> batches,
                                             boolean locallyConserved,
                                             List<String> localViolations) {
    public ProtectedMintLiabilitySnapshot {
        batches = List.copyOf(Objects.requireNonNull(batches, "batches"));
        localViolations = List.copyOf(Objects.requireNonNull(
                localViolations, "localViolations"));
        if (locallyConserved != localViolations.isEmpty()) {
            throw new IllegalArgumentException(
                    "Protected mint snapshot conservation status is inconsistent");
        }
    }
}
