package com.enviouse.futureshops.server.escrow.claim;

import java.util.Objects;

public record ClaimMaintenanceApplyResult(EscrowClaim claim, boolean replayed) {
    public ClaimMaintenanceApplyResult {
        Objects.requireNonNull(claim, "claim");
    }
}
