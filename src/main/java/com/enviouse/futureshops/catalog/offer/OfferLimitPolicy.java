package com.enviouse.futureshops.catalog.offer;

public record OfferLimitPolicy(
        int maximumPerRequest,
        long lifetimeLimit,
        long periodLimit,
        long periodSeconds,
        long cooldownSeconds
) {
    public static final int DEFAULT_MAXIMUM_PER_REQUEST = 2304;

    public static OfferLimitPolicy defaults() {
        return new OfferLimitPolicy(DEFAULT_MAXIMUM_PER_REQUEST,
                0L, 0L, 0L, 0L);
    }
}
