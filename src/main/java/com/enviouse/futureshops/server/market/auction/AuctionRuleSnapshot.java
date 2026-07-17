package com.enviouse.futureshops.server.market.auction;

import java.util.Objects;

public record AuctionRuleSnapshot(
    long listingFeeMinor,
    int saleTaxBasisPoints,
    long minimumIncrementMinor,
    int minimumIncrementBasisPoints,
    boolean antiSnipeEnabled,
    long antiSnipeTriggerMillis,
    long antiSnipeExtensionMillis,
    long maximumAntiSnipeCumulativeMillis,
    int maximumAntiSnipeExtensionCount,
    boolean allowSellerCancelBeforeBid,
    AuctionTimeBasis timeBasis,
    boolean pauseWhileFrozen,
    long configRevision
) {
    public AuctionRuleSnapshot {
        if (listingFeeMinor < 0L
            || saleTaxBasisPoints < 0
            || saleTaxBasisPoints > 10000
            || minimumIncrementMinor <= 0L
            || minimumIncrementBasisPoints < 0
            || minimumIncrementBasisPoints > 100000
            || configRevision < 0L) {
            throw new IllegalArgumentException("Auction rule values are invalid.");
        }
        Objects.requireNonNull(timeBasis, "timeBasis");
        if (antiSnipeTriggerMillis <= 0L || antiSnipeExtensionMillis <= 0L
            || maximumAntiSnipeCumulativeMillis < 0L || maximumAntiSnipeExtensionCount < 0) {
            throw new IllegalArgumentException("Auction anti snipe values are invalid.");
        }
        if (antiSnipeEnabled
            && (maximumAntiSnipeCumulativeMillis < antiSnipeExtensionMillis
            || maximumAntiSnipeExtensionCount == 0)) {
            throw new IllegalArgumentException("Enabled auction anti snipe rules cannot extend an auction.");
        }
    }

    public long minimumBid(long currentBid, long startingBid) {
        if (currentBid < 0L || startingBid <= 0L) {
            throw new IllegalArgumentException("Auction bid values are invalid.");
        }
        if (currentBid == 0L) {
            return startingBid;
        }
        long percentageIncrement = ceilingBasisPoints(currentBid, minimumIncrementBasisPoints);
        long increment = Math.max(minimumIncrementMinor, percentageIncrement);
        return Math.addExact(currentBid, increment);
    }

    public long saleTax(long grossMinor) {
        if (grossMinor < 0L) {
            throw new IllegalArgumentException("Auction gross value must not be negative.");
        }
        return floorBasisPoints(grossMinor, saleTaxBasisPoints);
    }

    private static long ceilingBasisPoints(long value, int basisPoints) {
        if (basisPoints == 0 || value == 0L) {
            return 0L;
        }
        long whole = Math.multiplyExact(value / 10000L, basisPoints);
        long remainderProduct = Math.multiplyExact(value % 10000L, basisPoints);
        return Math.addExact(whole, Math.floorDiv(Math.addExact(remainderProduct, 9999L), 10000L));
    }

    private static long floorBasisPoints(long value, int basisPoints) {
        if (basisPoints == 0 || value == 0L) {
            return 0L;
        }
        long whole = Math.multiplyExact(value / 10000L, basisPoints);
        long remainder = Math.multiplyExact(value % 10000L, basisPoints) / 10000L;
        return Math.addExact(whole, remainder);
    }
}
