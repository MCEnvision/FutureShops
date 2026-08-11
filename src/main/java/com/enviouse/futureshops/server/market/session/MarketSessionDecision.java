package com.enviouse.futureshops.server.market.session;

public enum MarketSessionDecision {
    ACCEPT,
    REPLAY,
    CONFLICT,
    MISSING,
    STALE_ROUTE,
    WRONG_MODULE,
    WRONG_VIEW,
    EXPIRED,
    RATE_LIMITED
}
