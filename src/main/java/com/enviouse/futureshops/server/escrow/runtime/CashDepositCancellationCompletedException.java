package com.enviouse.futureshops.server.escrow.runtime;

import java.util.Objects;
import java.util.UUID;

final class CashDepositCancellationCompletedException
        extends RuntimeException {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);

    private final UUID transactionId;

    CashDepositCancellationCompletedException(
            UUID transactionId,
            Throwable cause
    ) {
        super("Cash deposit cancellation completed", cause);
        this.transactionId = Objects.requireNonNull(
                transactionId, "transactionId");
        if (transactionId.equals(ZERO_UUID)) {
            throw new IllegalArgumentException(
                    "Cash deposit cancellation transaction id is invalid");
        }
    }

    UUID transactionId() {
        return transactionId;
    }
}
