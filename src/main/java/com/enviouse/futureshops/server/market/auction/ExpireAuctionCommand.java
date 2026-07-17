package com.enviouse.futureshops.server.market.auction;

import java.util.UUID;

public record ExpireAuctionCommand(
    UUID requestId,
    UUID listingId,
    long expectedRevision,
    UUID terminalTransactionId,
    long receivedAtMillis
) {
    public ExpireAuctionCommand {
        requireId(requestId, "request");
        requireId(listingId, "listing");
        requireId(terminalTransactionId, "terminal transaction");
        if (expectedRevision < 0L || receivedAtMillis < 0L) {
            throw new IllegalArgumentException("Auction expiration command values are invalid.");
        }
    }

    private static void requireId(UUID id, String label) {
        if (id == null || id.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Auction " + label + " identifier is required.");
        }
    }
}
