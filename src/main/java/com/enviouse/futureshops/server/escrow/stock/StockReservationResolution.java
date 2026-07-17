package com.enviouse.futureshops.server.escrow.stock;

import java.util.Objects;

public record StockReservationResolution(
        StockReservationId reservationId,
        long expectedReservationRevision
) implements Comparable<StockReservationResolution> {
    public StockReservationResolution {
        reservationId = Objects.requireNonNull(reservationId, "reservationId");
        StockLimits.requireRevision(expectedReservationRevision, false,
                "expected stock batch reservation revision");
    }

    @Override
    public int compareTo(StockReservationResolution other) {
        return reservationId.compareTo(other.reservationId);
    }
}
