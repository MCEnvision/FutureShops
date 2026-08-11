package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.server.escrow.stock.CatalogStockState;
import com.enviouse.futureshops.server.escrow.stock.CatalogStockStatus;
import com.enviouse.futureshops.server.escrow.stock.StockDefinition;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockLimits;
import com.enviouse.futureshops.server.escrow.stock.StockPolicy;
import com.enviouse.futureshops.server.escrow.stock.StockReservation;
import com.enviouse.futureshops.server.escrow.stock.StockReservationState;
import com.enviouse.futureshops.server.escrow.stock.StockStoreSnapshot;

import java.util.Objects;

final class CatalogStockMutationPlanner {
    private CatalogStockMutationPlanner() {
    }

    static StockDefinition adminResetDefinition(
            StockDefinition identity,
            long requestedAvailable,
            StockStoreSnapshot snapshot
    ) {
        Objects.requireNonNull(identity, "identity");
        if (requestedAvailable < 0L
                || requestedAvailable > StockLimits.MAX_QUANTITY) {
            throw new IllegalArgumentException(
                    "Catalog stock exceeds its bound");
        }
        long configured;
        try {
            configured = Math.addExact(requestedAvailable,
                    backedHeldQuantity(snapshot, identity.key()));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "Catalog stock exceeds its bound", exception);
        }
        if (configured > StockLimits.MAX_QUANTITY) {
            throw new IllegalArgumentException(
                    "Catalog stock exceeds its bound");
        }
        return new StockDefinition(identity.key(),
                StockPolicy.limited(configured),
                identity.configFingerprint());
    }

    static boolean refreshNeeded(
            CatalogStockState state,
            StockDefinition definition,
            StockStoreSnapshot snapshot
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(definition, "definition");
        long desiredAvailable = definition.policy().unlimited() ? 0L
                : Math.max(0L,
                definition.policy().configuredQuantity()
                        - backedHeldQuantity(snapshot, definition.key()));
        return state.status() != CatalogStockStatus.ACTIVE
                || !state.policy().equals(definition.policy())
                || !state.configFingerprint().equals(
                definition.configFingerprint())
                || state.availableQuantity() != desiredAvailable;
    }

    static long backedHeldQuantity(
            StockStoreSnapshot snapshot,
            StockKey key
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(key, "key");
        long held = 0L;
        for (StockReservation reservation
                : snapshot.reservations().values()) {
            if (reservation.stockKey().equals(key)
                    && reservation.inventoryBacked()
                    && reservation.state()
                    == StockReservationState.HELD) {
                held = Math.addExact(held, reservation.quantity());
                if (held > StockLimits.MAX_QUANTITY) {
                    throw new IllegalStateException(
                            "Catalog stock holds exceed their bound");
                }
            }
        }
        return held;
    }
}
