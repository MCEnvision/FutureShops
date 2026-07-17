package com.enviouse.futureshops.server.escrow.runtime;

public enum AtmWithdrawalStatus {
    DELIVERED,
    CLAIMED,
    PARTIALLY_DELIVERED,
    INVALID_AMOUNT,
    INVALID_PLAN,
    CURRENCY_CHANGED,
    INSUFFICIENT_FUNDS,
    CANCELLED,
    CONFLICT,
    RATE_LIMITED,
    MIGRATION_PENDING,
    ESCROW_UNAVAILABLE,
    RECOVERY_PENDING,
    MANUAL_REVIEW,
    SERVER_ERROR;

    public boolean success() {
        return this == DELIVERED
                || this == CLAIMED
                || this == PARTIALLY_DELIVERED;
    }
}
