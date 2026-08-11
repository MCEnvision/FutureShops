package com.enviouse.futureshops.server.market.auction;

import java.util.UUID;

public record AuctionBidStanding(
    UUID bidId,
    UUID bidderId,
    UUID holdAccountId,
    UUID holdTransactionId,
    long amountMinor,
    long acceptedSequence,
    long acceptedAtMillis
) {
    public AuctionBidStanding {
        requireId(bidId, "bid");
        requireId(bidderId, "bidder");
        requireId(holdAccountId, "hold account");
        requireId(holdTransactionId, "hold transaction");
        if (amountMinor <= 0L || acceptedSequence <= 0L || acceptedAtMillis < 0L) {
            throw new IllegalArgumentException("Auction bid standing values are invalid.");
        }
    }

    private static void requireId(UUID id, String label) {
        if (id == null || id.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Auction " + label + " identifier is required.");
        }
    }
}
