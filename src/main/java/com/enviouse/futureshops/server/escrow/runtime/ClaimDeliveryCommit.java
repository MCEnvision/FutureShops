package com.enviouse.futureshops.server.escrow.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClaimDeliveryCommit(UUID ownerId, UUID claimId, String requestKey, long units,
                                  Instant deliveredAt) {
    public ClaimDeliveryCommit {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        requestKey = Objects.requireNonNull(requestKey, "requestKey").trim();
        if (requestKey.isEmpty() || requestKey.length() > 192 || units <= 0L) {
            throw new IllegalArgumentException("Invalid claim delivery commit");
        }
    }
}
