package com.enviouse.futureshops.server.market.bazaar;

import java.util.Objects;
import java.util.Optional;

public record BazaarProductReconciliationAction(
        Type type,
        Optional<BazaarProduct> product,
        String productId,
        Optional<BazaarProductStatus> status
) {
    public BazaarProductReconciliationAction {
        type = Objects.requireNonNull(type, "type");
        product = Objects.requireNonNull(product, "product");
        productId = Objects.requireNonNull(productId, "productId");
        status = Objects.requireNonNull(status, "status");
        boolean valid = type == Type.REGISTER
                ? product.isPresent() && productId.isEmpty()
                && status.isEmpty()
                : product.isEmpty() && !productId.isBlank()
                && status.isPresent();
        if (!valid) {
            throw new IllegalArgumentException(
                    "Bazaar product reconciliation action is invalid");
        }
    }

    public static BazaarProductReconciliationAction register(
            BazaarProduct product) {
        return new BazaarProductReconciliationAction(Type.REGISTER,
                Optional.of(product), "", Optional.empty());
    }

    public static BazaarProductReconciliationAction status(
            String productId,
            BazaarProductStatus status) {
        return new BazaarProductReconciliationAction(Type.SET_STATUS,
                Optional.empty(), productId, Optional.of(status));
    }

    public enum Type {
        REGISTER,
        SET_STATUS
    }
}
