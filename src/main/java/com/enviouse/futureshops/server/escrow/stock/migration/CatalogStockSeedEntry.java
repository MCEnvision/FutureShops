package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.server.escrow.stock.StockDefinition;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockLimits;
import com.enviouse.futureshops.server.escrow.stock.StockPolicy;

import java.util.Objects;

public record CatalogStockSeedEntry(
        StockKey key,
        boolean unlimited,
        long configuredQuantity,
        long availableQuantity,
        String configFingerprint
) implements Comparable<CatalogStockSeedEntry> {
    public CatalogStockSeedEntry {
        key = Objects.requireNonNull(key, "key");
        configFingerprint = Objects.requireNonNull(
                configFingerprint, "configFingerprint");
        if (!configFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Catalog stock config fingerprint is invalid");
        }
        if (unlimited) {
            if (configuredQuantity != 0L || availableQuantity != 0L) {
                throw new IllegalArgumentException(
                        "Unlimited catalog stock cannot carry quantities");
            }
        } else if (configuredQuantity < 0L
                || configuredQuantity > StockLimits.MAX_QUANTITY
                || availableQuantity < 0L
                || availableQuantity > StockLimits.MAX_QUANTITY) {
            throw new IllegalArgumentException(
                    "Finite catalog stock quantity is invalid");
        }
    }

    public long durableCapacity() {
        return unlimited ? 0L
                : Math.max(configuredQuantity, availableQuantity);
    }

    public StockDefinition definition() {
        StockPolicy policy = unlimited
                ? StockPolicy.unlimitedStock()
                : StockPolicy.limited(durableCapacity());
        return new StockDefinition(key, policy, configFingerprint);
    }

    @Override
    public int compareTo(CatalogStockSeedEntry other) {
        return key.compareTo(other.key);
    }
}
