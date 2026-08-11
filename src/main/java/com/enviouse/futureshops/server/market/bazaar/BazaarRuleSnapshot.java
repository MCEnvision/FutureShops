package com.enviouse.futureshops.server.market.bazaar;

import java.util.Objects;

public record BazaarRuleSnapshot(
        int makerFeeBasisPoints,
        int takerFeeBasisPoints,
        int maximumOrderQuantity,
        long maximumNotionalMinor,
        int maximumOpenOrdersPerPlayer,
        int maximumOpenOrdersPerProductPerPlayer,
        long maximumEscrowedValuePerPlayerMinor,
        BazaarSelfTradePolicy selfTradePolicy,
        BazaarExecutionPricePolicy executionPricePolicy,
        boolean circuitBreakerEnabled,
        int priceBandBasisPoints,
        long minimumLifetimeMillis,
        long configRevision
) {
    public BazaarRuleSnapshot {
        Objects.requireNonNull(selfTradePolicy, "selfTradePolicy");
        Objects.requireNonNull(executionPricePolicy, "executionPricePolicy");
        if (makerFeeBasisPoints < 0 || makerFeeBasisPoints > 10_000
                || takerFeeBasisPoints < 0 || takerFeeBasisPoints > 10_000
                || maximumOrderQuantity <= 0 || maximumNotionalMinor <= 0L
                || maximumOpenOrdersPerPlayer <= 0
                || maximumOpenOrdersPerProductPerPlayer <= 0
                || maximumOpenOrdersPerProductPerPlayer > maximumOpenOrdersPerPlayer
                || maximumEscrowedValuePerPlayerMinor <= 0L
                || priceBandBasisPoints <= 0 || priceBandBasisPoints > 100_000
                || minimumLifetimeMillis < 0L || configRevision < 0L) {
            throw new IllegalArgumentException("Bazaar rule snapshot is invalid");
        }
    }
}
