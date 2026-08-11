package com.enviouse.futureshops.server.market.auction;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AuctionListing(
    UUID listingId,
    UUID sellerId,
    UUID activationTransactionId,
    AuctionItemLot itemLot,
    AuctionListingType type,
    AuctionListingState state,
    long revision,
    long startingBidMinor,
    long buyoutMinor,
    AuctionRuleSnapshot rules,
    long createdAtMillis,
    long deadlineMillis,
    long originalDurationMillis,
    long lastObservedTimeMillis,
    long frozenRemainingMillis,
    int antiSnipeExtensionCount,
    long antiSnipeCumulativeMillis,
    long acceptedBidCount,
    Optional<AuctionBidStanding> highestBid,
    Optional<AuctionSale> sale,
    Optional<UUID> terminalTransactionId
) {
    public AuctionListing {
        requireId(listingId, "listing");
        requireId(sellerId, "seller");
        requireId(activationTransactionId, "activation transaction");
        Objects.requireNonNull(itemLot, "itemLot");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rules, "rules");
        highestBid = Objects.requireNonNull(highestBid, "highestBid");
        sale = Objects.requireNonNull(sale, "sale");
        terminalTransactionId = Objects.requireNonNull(terminalTransactionId, "terminalTransactionId");
        terminalTransactionId.ifPresent(id -> requireId(id, "terminal transaction"));
        if (revision < 0L || createdAtMillis < 0L || lastObservedTimeMillis < createdAtMillis
            || frozenRemainingMillis < 0L || antiSnipeExtensionCount < 0
            || antiSnipeCumulativeMillis < 0L || acceptedBidCount < 0L) {
            throw new IllegalArgumentException("Auction listing counters and timestamps are invalid.");
        }
        if (antiSnipeExtensionCount > rules.maximumAntiSnipeExtensionCount()
            || antiSnipeCumulativeMillis > rules.maximumAntiSnipeCumulativeMillis()) {
            throw new IllegalArgumentException("Auction listing exceeds its anti snipe rule snapshot.");
        }
        if (type == AuctionListingType.BUY_NOW) {
            if (startingBidMinor != 0L || buyoutMinor <= 0L
                || deadlineMillis != 0L || originalDurationMillis != 0L) {
                throw new IllegalArgumentException("Buy now listing prices or duration are invalid.");
            }
        } else {
            if (startingBidMinor <= 0L || deadlineMillis <= createdAtMillis
                || originalDurationMillis <= 0L
                || deadlineMillis < Math.addExact(createdAtMillis, originalDurationMillis)) {
                throw new IllegalArgumentException("Timed auction prices or duration are invalid.");
            }
            if (type == AuctionListingType.TIMED_AUCTION && buyoutMinor != 0L) {
                throw new IllegalArgumentException("Timed auction cannot expose a buyout price.");
            }
            if (type == AuctionListingType.AUCTION_WITH_BUYOUT && buyoutMinor <= startingBidMinor) {
                throw new IllegalArgumentException("Auction buyout must exceed the starting bid.");
            }
        }
        if (!type.acceptsBids() && highestBid.isPresent()) {
            throw new IllegalArgumentException("Buy now listing cannot contain a bid standing.");
        }
        if (highestBid.isEmpty() != (acceptedBidCount == 0L)) {
            throw new IllegalArgumentException("Auction accepted bid count does not match its standing.");
        }
        Optional<AuctionBidStanding> validatedHighestBid = highestBid;
        validatedHighestBid.ifPresent(bid -> {
            if (bid.bidderId().equals(sellerId)
                || bid.amountMinor() < startingBidMinor
                || type == AuctionListingType.AUCTION_WITH_BUYOUT
                    && bid.amountMinor() >= buyoutMinor
                || bid.acceptedAtMillis() > lastObservedTimeMillis
                || type.acceptsBids() && bid.acceptedAtMillis() >= deadlineMillis) {
                throw new IllegalArgumentException("Auction highest bid does not match the listing.");
            }
        });
        boolean soldState = state == AuctionListingState.SOLD_PENDING
            || state == AuctionListingState.ENDED_SOLD
            || state == AuctionListingState.SETTLED;
        if (soldState != sale.isPresent()) {
            throw new IllegalArgumentException("Auction sale evidence does not match the listing state.");
        }
        sale.ifPresent(value -> {
            if (value.buyerId().equals(sellerId)
                || value.soldAtMillis() > lastObservedTimeMillis
                || value.priceMinor() <= 0L) {
                throw new IllegalArgumentException("Auction sale does not match the listing.");
            }
            if (value.buyout() && (!type.hasBuyout() || value.priceMinor() != buyoutMinor)) {
                throw new IllegalArgumentException("Auction buyout sale does not match the listing.");
            }
            if (!value.buyout()
                && (validatedHighestBid.map(AuctionBidStanding::amountMinor).orElse(0L)
                    != value.priceMinor()
                    || !validatedHighestBid.map(AuctionBidStanding::bidderId)
                        .filter(value.buyerId()::equals).isPresent()
                    || !validatedHighestBid.map(AuctionBidStanding::holdAccountId)
                        .filter(value.holdAccountId()::equals).isPresent()
                    || !validatedHighestBid.map(AuctionBidStanding::holdTransactionId)
                        .filter(value.holdTransactionId()::equals).isPresent())) {
                throw new IllegalArgumentException("Auction winning bid does not match the sale.");
            }
            if (value.buyout() && validatedHighestBid
                .map(AuctionBidStanding::bidderId).filter(value.buyerId()::equals).isPresent()
                && !validatedHighestBid.map(AuctionBidStanding::holdAccountId)
                    .filter(value.holdAccountId()::equals).isPresent()) {
                throw new IllegalArgumentException("Auction buyout hold account differs from its standing.");
            }
        });
        boolean needsTerminalTransaction = state == AuctionListingState.SOLD_PENDING
            || state == AuctionListingState.ENDED_SOLD
            || state == AuctionListingState.ENDED_UNSOLD
            || state == AuctionListingState.CANCELLED
            || state == AuctionListingState.SETTLED;
        if (needsTerminalTransaction != terminalTransactionId.isPresent()) {
            throw new IllegalArgumentException("Auction terminal transaction does not match the listing state.");
        }
        if (sale.isPresent() && !terminalTransactionId.equals(
            Optional.of(sale.orElseThrow().settlementTransactionId()))) {
            throw new IllegalArgumentException("Auction sale and terminal transaction identifiers differ.");
        }
        if (state == AuctionListingState.FROZEN && type.acceptsBids() && frozenRemainingMillis <= 0L) {
            throw new IllegalArgumentException("Frozen timed auction must preserve positive remaining time.");
        }
        if (state != AuctionListingState.FROZEN && frozenRemainingMillis != 0L) {
            throw new IllegalArgumentException("Only frozen auctions can preserve remaining time.");
        }
    }

    public long minimumNextBid() {
        return rules.minimumBid(highestBid.map(AuctionBidStanding::amountMinor).orElse(0L), startingBidMinor);
    }

    public long clampTime(long nowMillis) {
        if (nowMillis < 0L) {
            throw new IllegalArgumentException("Auction time must not be negative.");
        }
        return Math.max(nowMillis, lastObservedTimeMillis);
    }

    public boolean deadlineReached(long nowMillis) {
        return type.acceptsBids() && clampTime(nowMillis) >= deadlineMillis;
    }

    private static void requireId(UUID id, String label) {
        if (id == null || id.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("Auction " + label + " identifier is required.");
        }
    }
}
