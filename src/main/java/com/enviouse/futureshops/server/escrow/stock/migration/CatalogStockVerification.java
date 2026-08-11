package com.enviouse.futureshops.server.escrow.stock.migration;

import java.util.Objects;

public record CatalogStockVerification(
        boolean valid,
        long completionSequence,
        String detail
) {
    public CatalogStockVerification {
        detail = Objects.requireNonNull(detail, "detail");
        if (completionSequence < 0L) {
            throw new IllegalArgumentException(
                    "Catalog stock verification sequence is invalid");
        }
        if (valid == !detail.isEmpty()) {
            throw new IllegalArgumentException(
                    "Catalog stock verification result is inconsistent");
        }
    }

    public static CatalogStockVerification valid(long sequence) {
        return new CatalogStockVerification(true, sequence, "");
    }

    public static CatalogStockVerification invalid(
            long sequence,
            String detail
    ) {
        String safeDetail = Objects.requireNonNull(detail, "detail").trim();
        if (safeDetail.isEmpty()) {
            throw new IllegalArgumentException(
                    "Catalog stock verification detail is required");
        }
        return new CatalogStockVerification(false, sequence, safeDetail);
    }
}
