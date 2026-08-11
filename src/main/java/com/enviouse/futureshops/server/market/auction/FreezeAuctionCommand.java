package com.enviouse.futureshops.server.market.auction;

import java.util.UUID;

public record FreezeAuctionCommand(
    UUID requestId,
    UUID listingId,
    long expectedRevision,
    long receivedAtMillis
) {
    public FreezeAuctionCommand {
        requireId(requestId, "request");
        requireId(listingId, "listing");
        if (expectedRevision < 0L || receivedAtMillis < 0L) {
            throw new IllegalArgumentException("Auction freeze command values are invalid.");
        }
    }

    private static void requireId(UUID id, String label) {
        if (id == null || id.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Auction " + label + " identifier is required.");
        }
    }
}
