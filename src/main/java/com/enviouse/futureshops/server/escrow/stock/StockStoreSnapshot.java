package com.enviouse.futureshops.server.escrow.stock;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record StockStoreSnapshot(
        long storeRevision,
        String catalogFingerprint,
        Map<StockKey, CatalogStockState> listings,
        Map<StockReservationId, StockReservation> reservations,
        Map<UUID, StockMutationReceipt> receipts
) {
    public StockStoreSnapshot {
        StockLimits.requireRevision(storeRevision, false, "stock snapshot revision");
        catalogFingerprint = StockLimits.requireFingerprint(catalogFingerprint,
                "stock catalog fingerprint");
        listings = Map.copyOf(Objects.requireNonNull(listings, "listings"));
        reservations = Map.copyOf(Objects.requireNonNull(reservations, "reservations"));
        receipts = Map.copyOf(Objects.requireNonNull(receipts, "receipts"));
        if (listings.size() > StockLimits.MAX_LISTINGS
                || reservations.size() > StockLimits.MAX_RESERVATIONS
                || receipts.size() > StockLimits.MAX_REQUESTS) {
            throw new IllegalArgumentException("Stock snapshot exceeds entry limits");
        }
    }

    public static StockStoreSnapshot empty(String catalogFingerprint) {
        return new StockStoreSnapshot(0L, catalogFingerprint, Map.of(), Map.of(), Map.of());
    }
}
