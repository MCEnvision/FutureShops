package com.enviouse.futureshops.server.market.auction;

import java.util.UUID;

public record CancelAuctionCommand(
    UUID requestId,
    UUID listingId,
    long expectedRevision,
    UUID actorId,
    UUID terminalTransactionId,
    long receivedAtMillis,
    boolean forced
) {
    public CancelAuctionCommand {
        requireId(requestId, "request");
        requireId(listingId, "listing");
        requireId(actorId, "actor");
        requireId(terminalTransactionId, "terminal transaction");
        if (expectedRevision < 0L || receivedAtMillis < 0L) {
            throw new IllegalArgumentException("Auction cancellation command values are invalid.");
        }
    }

    public CancelAuctionCommand(UUID requestId, UUID listingId,
                                long expectedRevision, UUID actorId,
                                UUID terminalTransactionId,
                                long receivedAtMillis) {
        this(requestId, listingId, expectedRevision, actorId,
                terminalTransactionId, receivedAtMillis, false);
    }

    private static void requireId(UUID id, String label) {
        if (id == null || id.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Auction " + label + " identifier is required.");
        }
    }
}
