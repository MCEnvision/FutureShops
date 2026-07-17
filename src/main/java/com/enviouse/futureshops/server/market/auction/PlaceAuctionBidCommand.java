package com.enviouse.futureshops.server.market.auction;

import java.util.UUID;

public record PlaceAuctionBidCommand(
    UUID requestId,
    UUID listingId,
    long expectedRevision,
    UUID bidId,
    UUID bidderId,
    UUID holdAccountId,
    UUID holdTransactionId,
    long amountMinor,
    long heldDeltaMinor,
    long receivedAtMillis
) {
    public PlaceAuctionBidCommand {
        requireId(requestId, "request");
        requireId(listingId, "listing");
        requireId(bidId, "bid");
        requireId(bidderId, "bidder");
        requireId(holdAccountId, "hold account");
        requireId(holdTransactionId, "hold transaction");
        if (expectedRevision < 0L || amountMinor <= 0L || heldDeltaMinor <= 0L || receivedAtMillis < 0L) {
            throw new IllegalArgumentException("Auction bid command values are invalid.");
        }
    }

    private static void requireId(UUID id, String label) {
        if (id == null || id.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Auction " + label + " identifier is required.");
        }
    }
}
