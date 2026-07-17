package com.enviouse.futureshops.server.market.bazaar;

import java.util.Objects;

public record BazaarProductVersionKey(String productId, long version) {
    public BazaarProductVersionKey {
        Objects.requireNonNull(productId, "productId");
        if (productId.isBlank() || version <= 0L) {
            throw new IllegalArgumentException("Bazaar product version key is invalid");
        }
    }

    public static BazaarProductVersionKey of(BazaarProduct product) {
        return new BazaarProductVersionKey(product.productId(), product.version());
    }
}
