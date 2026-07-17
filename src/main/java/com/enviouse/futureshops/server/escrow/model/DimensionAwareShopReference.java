package com.enviouse.futureshops.server.escrow.model;

import java.util.Objects;

public record DimensionAwareShopReference(
        String shopId,
        String dimensionId,
        int blockX,
        int blockY,
        int blockZ
) {
    public static final int MAX_SHOP_ID_LENGTH = 160;
    public static final int MAX_DIMENSION_ID_LENGTH = 160;

    public DimensionAwareShopReference {
        Objects.requireNonNull(shopId, "shopId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        shopId = shopId.strip();
        dimensionId = dimensionId.strip();
        if (shopId.isEmpty() || shopId.length() > MAX_SHOP_ID_LENGTH) {
            throw new IllegalArgumentException("Invalid shop id");
        }
        if (dimensionId.isEmpty() || dimensionId.length() > MAX_DIMENSION_ID_LENGTH) {
            throw new IllegalArgumentException("Invalid dimension id");
        }
    }
}
