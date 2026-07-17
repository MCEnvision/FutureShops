package com.enviouse.futureshops.server.escrow.runtime;

import java.util.Objects;
import java.util.UUID;

record ForeignCashDepositResult(
        UUID transactionId,
        long depositedMinorUnits,
        long walletCreditMinorUnits,
        long overflowClaimMinorUnits,
        boolean cleanupPending
) {
    ForeignCashDepositResult {
        Objects.requireNonNull(transactionId, "transactionId");
        if (depositedMinorUnits <= 0L
                || walletCreditMinorUnits < 0L
                || overflowClaimMinorUnits < 0L
                || Math.addExact(walletCreditMinorUnits,
                overflowClaimMinorUnits) != depositedMinorUnits) {
            throw new IllegalArgumentException(
                    "Foreign cash deposit result is invalid");
        }
    }
}
