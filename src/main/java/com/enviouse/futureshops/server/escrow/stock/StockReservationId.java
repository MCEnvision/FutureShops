package com.enviouse.futureshops.server.escrow.stock;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public record StockReservationId(UUID value) implements Comparable<StockReservationId> {
    public StockReservationId {
        value = StockLimits.requireNonzeroUuid(value, "stock reservation identifier");
    }

    public static StockReservationId forTransaction(UUID transactionId, StockKey key) {
        return forTransaction(transactionId, key,
                StockReservationDirection.OUTBOUND);
    }

    public static StockReservationId forTransaction(
            UUID transactionId,
            StockKey key,
            StockReservationDirection direction
    ) {
        StockLimits.requireNonzeroUuid(transactionId, "stock transaction identifier");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(direction, "direction");
        String material = direction == StockReservationDirection.OUTBOUND
                ? "futureshops.stock.reservation.1."
                + transactionId + "." + key.canonicalValue()
                : "futureshops.stock.reservation.2."
                + transactionId + "." + key.canonicalValue() + "."
                + direction.wireId();
        return new StockReservationId(UUID.nameUUIDFromBytes(
                material.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public int compareTo(StockReservationId other) {
        return value.toString().compareTo(other.value.toString());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
