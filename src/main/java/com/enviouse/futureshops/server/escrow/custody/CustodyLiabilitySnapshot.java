package com.enviouse.futureshops.server.escrow.custody;

import java.util.List;
import java.util.Objects;

public record CustodyLiabilitySnapshot(List<CustodyHeldLiability> heldLiabilities,
                                       boolean locallyConserved,
                                       List<String> localViolations) {
    public CustodyLiabilitySnapshot {
        heldLiabilities = List.copyOf(Objects.requireNonNull(
                heldLiabilities, "heldLiabilities"));
        localViolations = List.copyOf(Objects.requireNonNull(
                localViolations, "localViolations"));
        if (locallyConserved != localViolations.isEmpty()) {
            throw new IllegalArgumentException(
                    "Custody liability snapshot conservation status is inconsistent");
        }
    }
}
