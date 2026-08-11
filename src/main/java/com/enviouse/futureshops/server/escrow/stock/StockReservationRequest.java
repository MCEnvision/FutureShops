package com.enviouse.futureshops.server.escrow.stock;

import java.util.Objects;

public record StockReservationRequest(
        StockKey stockKey,
        StockReservationDirection direction,
        long quantity,
        long expectedListingRevision
) implements Comparable<StockReservationRequest> {
    public StockReservationRequest {
        stockKey = Objects.requireNonNull(stockKey, "stockKey");
        direction = Objects.requireNonNull(direction, "direction");
        StockLimits.requireQuantity(quantity, false,
                "stock batch reservation quantity");
        StockLimits.requireRevision(expectedListingRevision, false,
                "expected stock batch listing revision");
    }

    @Override
    public int compareTo(StockReservationRequest other) {
        int keyOrder = stockKey.compareTo(other.stockKey);
        return keyOrder != 0 ? keyOrder
                : Integer.compare(direction.wireId(), other.direction.wireId());
    }
}
