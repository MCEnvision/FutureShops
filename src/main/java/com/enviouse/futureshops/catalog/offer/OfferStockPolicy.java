package com.enviouse.futureshops.catalog.offer;

import java.util.Objects;

public record OfferStockPolicy(
        Type type,
        long quantity,
        long refreshSeconds
) {
    public OfferStockPolicy {
        type = Objects.requireNonNull(type, "type");
    }

    public static OfferStockPolicy unlimited() {
        return new OfferStockPolicy(Type.UNLIMITED, 0L, 0L);
    }

    public static OfferStockPolicy limited(long quantity, long refreshSeconds) {
        return new OfferStockPolicy(Type.LIMITED_INDEPENDENT,
                quantity, refreshSeconds);
    }

    public enum Type {
        UNLIMITED,
        LIMITED_INDEPENDENT,
        LINKED
    }
}
