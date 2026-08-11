package com.enviouse.futureshops.server.escrow.stock;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StockReservation(
        StockReservationId reservationId,
        UUID transactionId,
        StockKey stockKey,
        StockReservationDirection direction,
        long quantity,
        boolean inventoryBacked,
        StockReservationState state,
        long revision,
        Instant createdAt,
        Instant updatedAt
) {
    public StockReservation {
        reservationId = Objects.requireNonNull(reservationId, "reservationId");
        transactionId = StockLimits.requireNonzeroUuid(transactionId,
                "stock transaction identifier");
        stockKey = Objects.requireNonNull(stockKey, "stockKey");
        direction = Objects.requireNonNull(direction, "direction");
        if (!reservationId.equals(StockReservationId.forTransaction(
                transactionId, stockKey, direction))) {
            throw new IllegalArgumentException("Stock reservation identifier is not bound to its transaction");
        }
        StockLimits.requireQuantity(quantity, false, "stock reservation quantity");
        state = Objects.requireNonNull(state, "state");
        StockLimits.requireRevision(revision, false, "stock reservation revision");
        createdAt = StockLimits.requireInstant(createdAt, "createdAt");
        updatedAt = StockLimits.requireInstant(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Stock reservation update precedes creation");
        }
        if (state == StockReservationState.HELD && revision != 0L) {
            throw new IllegalArgumentException("Held stock reservation must have revision zero");
        }
        if (state != StockReservationState.HELD && revision != 1L) {
            throw new IllegalArgumentException("Resolved stock reservation must have revision one");
        }
    }

    public StockReservation(
            StockReservationId reservationId,
            UUID transactionId,
            StockKey stockKey,
            long quantity,
            boolean inventoryBacked,
            StockReservationState state,
            long revision,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(reservationId, transactionId, stockKey,
                StockReservationDirection.OUTBOUND, quantity, inventoryBacked,
                state, revision, createdAt, updatedAt);
    }

    public static StockReservation held(UUID transactionId, StockKey key, long quantity,
                                        boolean inventoryBacked, Instant now) {
        return held(transactionId, key, StockReservationDirection.OUTBOUND,
                quantity, inventoryBacked, now);
    }

    public static StockReservation held(
            UUID transactionId,
            StockKey key,
            StockReservationDirection direction,
            long quantity,
            boolean inventoryBacked,
            Instant now
    ) {
        return new StockReservation(StockReservationId.forTransaction(
                transactionId, key, direction),
                transactionId, key, direction, quantity, inventoryBacked,
                StockReservationState.HELD,
                0L, now, now);
    }

    public StockReservation resolve(StockReservationState terminalState, Instant now) {
        if (state != StockReservationState.HELD
                || terminalState == StockReservationState.HELD) {
            throw new StockConflictException("Stock reservation cannot make that transition");
        }
        if (now.isBefore(updatedAt)) {
            throw new StockConflictException("Stock reservation time moved backwards");
        }
        return new StockReservation(reservationId, transactionId, stockKey,
                direction, quantity, inventoryBacked, terminalState, 1L,
                createdAt, now);
    }
}
