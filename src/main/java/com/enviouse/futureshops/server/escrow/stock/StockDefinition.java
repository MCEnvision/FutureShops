package com.enviouse.futureshops.server.escrow.stock;

import java.util.Objects;

public record StockDefinition(StockKey key, StockPolicy policy, String configFingerprint) {
    public StockDefinition {
        key = Objects.requireNonNull(key, "key");
        policy = Objects.requireNonNull(policy, "policy");
        configFingerprint = StockLimits.requireFingerprint(configFingerprint,
                "stock config fingerprint");
    }
}
