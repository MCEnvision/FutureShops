package com.enviouse.futureshops.server.market.auction;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AuctionOperationResult(
    UUID requestId,
    UUID listingId,
    AuctionOperationType operation,
    AuctionOperationStatus status,
    boolean replayed,
    long observedRevision,
    long requiredHoldDeltaMinor,
    long refundMinor,
    long antiSnipeAddedMillis,
    Optional<UUID> refundPlayerId,
    Optional<AuctionBidStanding> displacedBid,
    Optional<AuctionListing> listing
) {
    public AuctionOperationResult {
        requireId(requestId, "request");
        requireId(listingId, "listing");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(status, "status");
        refundPlayerId = Objects.requireNonNull(refundPlayerId, "refundPlayerId");
        displacedBid = Objects.requireNonNull(displacedBid, "displacedBid");
        listing = Objects.requireNonNull(listing, "listing");
        refundPlayerId.ifPresent(id -> requireId(id, "refund player"));
        if (observedRevision < -1L || requiredHoldDeltaMinor < 0L
            || refundMinor < 0L || antiSnipeAddedMillis < 0L) {
            throw new IllegalArgumentException("Auction operation result values are invalid.");
        }
        if ((refundMinor > 0L) != refundPlayerId.isPresent()) {
            throw new IllegalArgumentException("Auction refund owner does not match its value.");
        }
        if (status == AuctionOperationStatus.APPLIED) {
            AuctionListing applied = listing.orElseThrow(
                () -> new IllegalArgumentException("Applied auction result requires a listing."));
            if (applied.revision() != observedRevision) {
                throw new IllegalArgumentException("Applied auction result revision differs from its listing.");
            }
        } else if (requiredHoldDeltaMinor != 0L || refundMinor != 0L || antiSnipeAddedMillis != 0L
            || refundPlayerId.isPresent() || displacedBid.isPresent()) {
            throw new IllegalArgumentException("Rejected auction result cannot move value or time.");
        }
        if (listing.isPresent() && !listing.orElseThrow().listingId().equals(listingId)) {
            throw new IllegalArgumentException("Auction result listing identifier differs from its snapshot.");
        }
        if (listing.isPresent() && listing.orElseThrow().revision() != observedRevision) {
            throw new IllegalArgumentException("Auction result revision differs from its snapshot.");
        }
        if (listing.isEmpty() && observedRevision != -1L) {
            throw new IllegalArgumentException("Auction result without a listing must use missing revision evidence.");
        }
    }

    public boolean applied() {
        return status == AuctionOperationStatus.APPLIED && !replayed;
    }

    public boolean durablyApplied() {
        return status == AuctionOperationStatus.APPLIED;
    }

    public boolean newlyCommitted() {
        return applied();
    }

    public AuctionOperationResult asReplay() {
        return replayed ? this : new AuctionOperationResult(
            requestId,
            listingId,
            operation,
            status,
            true,
            observedRevision,
            requiredHoldDeltaMinor,
            refundMinor,
            antiSnipeAddedMillis,
            refundPlayerId,
            displacedBid,
            listing
        );
    }

    private static void requireId(UUID id, String label) {
        if (id == null || id.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Auction " + label + " identifier is required.");
        }
    }
}
