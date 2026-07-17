package com.enviouse.futureshops.server.escrow.claim;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClaimAttemptResult(UUID claimId, String requestKey, long deliveredUnits,
                                 long remainingUnits, ClaimStatus status, Instant deliveredAt,
                                 boolean replayed) {
    public ClaimAttemptResult {
        Objects.requireNonNull(claimId, "claimId");
        requestKey = Objects.requireNonNull(requestKey, "requestKey").trim();
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        if (requestKey.isEmpty() || requestKey.length() > 192
                || deliveredUnits < 0L || remainingUnits < 0L) {
            throw new IllegalArgumentException("Invalid claim attempt result");
        }
        if ((status == ClaimStatus.COMPLETED) != (remainingUnits == 0L)) {
            throw new IllegalArgumentException("Claim attempt status does not match remaining units");
        }
        if ((status == ClaimStatus.COMPLETED || status == ClaimStatus.PARTIALLY_DELIVERED)
                && deliveredUnits == 0L) {
            throw new IllegalArgumentException("Claim attempt delivered no units");
        }
        if (status == ClaimStatus.QUARANTINED) {
            throw new IllegalArgumentException("Quarantined claims cannot have delivery attempts");
        }
    }

    public ClaimAttemptResult asReplay() {
        return new ClaimAttemptResult(claimId, requestKey, deliveredUnits, remainingUnits,
                status, deliveredAt, true);
    }
}
