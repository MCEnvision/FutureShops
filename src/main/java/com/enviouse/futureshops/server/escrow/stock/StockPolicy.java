package com.enviouse.futureshops.server.escrow.stock;

public record StockPolicy(boolean unlimited, long configuredQuantity) {
    public StockPolicy {
        if (unlimited) {
            if (configuredQuantity != 0L) {
                throw new IllegalArgumentException("Unlimited stock cannot have a configured quantity");
            }
        } else {
            StockLimits.requireQuantity(configuredQuantity, true, "configured stock quantity");
        }
    }

    public static StockPolicy limited(long configuredQuantity) {
        return new StockPolicy(false, configuredQuantity);
    }

    public static StockPolicy unlimitedStock() {
        return new StockPolicy(true, 0L);
    }
}
