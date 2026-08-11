package com.enviouse.futureshops.server.escrow.stock;

import java.time.Instant;
import java.util.Objects;

public record CatalogStockState(
        StockKey key,
        StockPolicy policy,
        CatalogStockStatus status,
        long availableQuantity,
        String configFingerprint,
        long revision,
        Instant updatedAt
) {
    public CatalogStockState {
        key = Objects.requireNonNull(key, "key");
        policy = Objects.requireNonNull(policy, "policy");
        status = Objects.requireNonNull(status, "status");
        StockLimits.requireQuantity(availableQuantity, true, "available stock quantity");
        if (policy.unlimited() && availableQuantity != 0L) {
            throw new IllegalArgumentException("Unlimited stock cannot carry finite availability");
        }
        configFingerprint = StockLimits.requireFingerprint(configFingerprint,
                "stock state config fingerprint");
        StockLimits.requireRevision(revision, false, "stock state revision");
        updatedAt = StockLimits.requireInstant(updatedAt, "updatedAt");
    }

    public static CatalogStockState seed(StockDefinition definition, Instant now) {
        Objects.requireNonNull(definition, "definition");
        long available = definition.policy().unlimited()
                ? 0L : definition.policy().configuredQuantity();
        return new CatalogStockState(definition.key(), definition.policy(),
                CatalogStockStatus.ACTIVE, available, definition.configFingerprint(), 0L, now);
    }

    public boolean unlimited() {
        return policy.unlimited();
    }

    public long displayQuantity() {
        return unlimited() ? -1L : availableQuantity;
    }
}
