package com.enviouse.futureshops.server.market.auction;

import java.util.UUID;

public record AuctionBuyNowCommand(
    UUID requestId,
    UUID listingId,
    long expectedRevision,
    UUID buyerId,
    UUID holdAccountId,
    UUID holdTransactionId,
    UUID settlementTransactionId,
    long heldDeltaMinor,
    long receivedAtMillis
) {
    public AuctionBuyNowCommand {
        requireId(requestId, "request");
        requireId(listingId, "listing");
        requireId(buyerId, "buyer");
        requireId(holdAccountId, "hold account");
        requireId(holdTransactionId, "hold transaction");
        requireId(settlementTransactionId, "settlement transaction");
        if (expectedRevision < 0L || heldDeltaMinor <= 0L || receivedAtMillis < 0L) {
            throw new IllegalArgumentException("Auction buy now command values are invalid.");
        }
    }

    private static void requireId(UUID id, String label) {
        if (id == null || id.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Auction " + label + " identifier is required.");
        }
    }
}
