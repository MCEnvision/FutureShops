package com.enviouse.futureshops.server.escrow.runtime;

import java.util.Objects;
import java.util.UUID;

public record ProtectedCashRedemptionResult(
        UUID transactionId,
        long depositedMinorUnits,
        long walletCreditMinorUnits,
        long overflowClaimMinorUnits,
        boolean cleanupPending
) {
    public ProtectedCashRedemptionResult {
        Objects.requireNonNull(transactionId, "transactionId");
        if (depositedMinorUnits <= 0L
                || walletCreditMinorUnits < 0L
                || overflowClaimMinorUnits < 0L
                || Math.addExact(walletCreditMinorUnits,
                overflowClaimMinorUnits) != depositedMinorUnits) {
            throw new IllegalArgumentException(
                    "Protected cash redemption result is invalid");
        }
    }
}
