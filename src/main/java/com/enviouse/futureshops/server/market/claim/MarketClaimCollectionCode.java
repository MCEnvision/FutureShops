package com.enviouse.futureshops.server.market.claim;

public enum MarketClaimCollectionCode {
    COLLECTED,
    PARTIALLY_COLLECTED,
    ALREADY_COLLECTED,
    WALLET_FULL,
    INVENTORY_FULL,
    RETRYABLE,
    RECOVERY_REQUIRED,
    CANCELLED,
    CONFIG_CHANGED,
    REENTRANT_REQUEST,
    NOT_FOUND,
    UNSUPPORTED_KIND,
    REQUEST_CONFLICT,
    MISSING_SESSION,
    STALE_ROUTE,
    WRONG_MODULE,
    WRONG_VIEW,
    SESSION_EXPIRED,
    RATE_LIMITED,
    MODULE_UNAVAILABLE,
    ESCROW_UNAVAILABLE,
    SERVER_ERROR;

    public boolean refreshClaims() {
        return this == COLLECTED
                || this == PARTIALLY_COLLECTED
                || this == ALREADY_COLLECTED
                || this == RECOVERY_REQUIRED;
    }

    public boolean terminal() {
        return this == COLLECTED || this == ALREADY_COLLECTED;
    }
}
