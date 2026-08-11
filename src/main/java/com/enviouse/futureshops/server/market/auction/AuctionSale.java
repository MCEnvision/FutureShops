package com.enviouse.futureshops.server.market.auction;

import java.util.UUID;

public record AuctionSale(
    UUID buyerId,
    UUID holdAccountId,
    UUID holdTransactionId,
    UUID settlementTransactionId,
    long priceMinor,
    long soldAtMillis,
    boolean buyout
) {
    public AuctionSale {
        requireId(buyerId, "buyer");
        requireId(holdAccountId, "hold account");
        requireId(holdTransactionId, "hold transaction");
        requireId(settlementTransactionId, "settlement transaction");
        if (priceMinor <= 0L || soldAtMillis < 0L) {
            throw new IllegalArgumentException("Auction sale values are invalid.");
        }
    }

    private static void requireId(UUID id, String label) {
        if (id == null || id.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Auction " + label + " identifier is required.");
        }
    }
}
