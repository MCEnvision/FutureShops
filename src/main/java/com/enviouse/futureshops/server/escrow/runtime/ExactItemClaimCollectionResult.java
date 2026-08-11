package com.enviouse.futureshops.server.escrow.runtime;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ExactItemClaimCollectionResult(
        UUID claimId,
        ExactItemClaimCollectionStatus status,
        long deliveredUnits,
        long remainingUnits,
        Optional<UUID> requestId,
        boolean replayed
) {
    public ExactItemClaimCollectionResult {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(status, "status");
        requestId = Objects.requireNonNull(requestId, "requestId");
        if (deliveredUnits < 0L || remainingUnits < 0L
                || replayed && requestId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Exact item claim collection result is invalid");
        }
    }

    public static ExactItemClaimCollectionResult pending(
            UUID claimId,
            ExactItemClaimCollectionStatus status,
            long remainingUnits
    ) {
        return new ExactItemClaimCollectionResult(claimId, status, 0L,
                remainingUnits, Optional.empty(), false);
    }
}
