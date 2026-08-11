package com.enviouse.futureshops.server.market.auction;

import java.util.Objects;
import java.util.UUID;

public record CreateAuctionCommand(
    UUID requestId,
    UUID listingId,
    UUID sellerId,
    UUID activationTransactionId,
    AuctionItemLot itemLot,
    AuctionListingType type,
    long startingBidMinor,
    long buyoutMinor,
    AuctionRuleSnapshot rules,
    long createdAtMillis,
    long deadlineMillis
) {
    public CreateAuctionCommand {
        requireId(requestId, "request");
        requireId(listingId, "listing");
        requireId(sellerId, "seller");
        requireId(activationTransactionId, "activation transaction");
        Objects.requireNonNull(itemLot, "itemLot");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(rules, "rules");
        if (createdAtMillis < 0L) {
            throw new IllegalArgumentException("Auction creation time must not be negative.");
        }
        if (type == AuctionListingType.BUY_NOW) {
            if (startingBidMinor != 0L || buyoutMinor <= 0L || deadlineMillis != 0L) {
                throw new IllegalArgumentException("Buy now command values are invalid.");
            }
        } else if (startingBidMinor <= 0L || deadlineMillis <= createdAtMillis
            || type == AuctionListingType.TIMED_AUCTION && buyoutMinor != 0L
            || type == AuctionListingType.AUCTION_WITH_BUYOUT && buyoutMinor <= startingBidMinor) {
            throw new IllegalArgumentException("Timed auction command values are invalid.");
        }
    }

    private static void requireId(UUID id, String label) {
        if (id == null || id.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Auction " + label + " identifier is required.");
        }
    }
}
