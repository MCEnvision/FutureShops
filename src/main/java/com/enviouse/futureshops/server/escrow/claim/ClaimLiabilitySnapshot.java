package com.enviouse.futureshops.server.escrow.claim;

import java.util.List;
import java.util.Objects;

public record ClaimLiabilitySnapshot(List<ClaimLiabilityEntry> unfinishedClaims) {
    public ClaimLiabilitySnapshot {
        unfinishedClaims = List.copyOf(Objects.requireNonNull(
                unfinishedClaims, "unfinishedClaims"));
    }
}
