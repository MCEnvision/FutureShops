package com.enviouse.futureshops.server.market.bazaar;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record BazaarProduct(
        String productId,
        long version,
        String registryId,
        String exactIdentity,
        String categoryId,
        int lotSize,
        long priceTickMinor,
        long minimumPriceMinor,
        long maximumPriceMinor,
        int maximumQuantity,
        BazaarProductStatus status
) {
    private static final Pattern PRODUCT_ID = Pattern.compile("[a-z0-9_.]{1,96}");

    public BazaarProduct {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(registryId, "registryId");
        Objects.requireNonNull(exactIdentity, "exactIdentity");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(status, "status");
        productId = productId.strip().toLowerCase(Locale.ROOT);
        registryId = registryId.strip().toLowerCase(Locale.ROOT);
        categoryId = categoryId.strip().toLowerCase(Locale.ROOT);
        if (!PRODUCT_ID.matcher(productId).matches() || version <= 0L
                || registryId.length() > 256 || registryId.indexOf(':') <= 0
                || exactIdentity.length() > 256 || categoryId.length() > 96
                || lotSize <= 0 || priceTickMinor <= 0L
                || minimumPriceMinor <= 0L || maximumPriceMinor < minimumPriceMinor
                || maximumQuantity <= 0) {
            throw new IllegalArgumentException("Bazaar product definition is invalid");
        }
    }

    public void requirePrice(long priceMinor) {
        if (priceMinor < minimumPriceMinor || priceMinor > maximumPriceMinor
                || priceMinor % priceTickMinor != 0L) {
            throw new IllegalArgumentException("Bazaar price is outside the product rules");
        }
    }

    public void requireQuantity(int quantity) {
        if (quantity <= 0 || quantity > maximumQuantity || quantity % lotSize != 0) {
            throw new IllegalArgumentException("Bazaar quantity is outside the product rules");
        }
    }
}
