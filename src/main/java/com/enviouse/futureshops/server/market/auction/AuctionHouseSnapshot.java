package com.enviouse.futureshops.server.market.auction;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AuctionHouseSnapshot(
    long nextAcceptedSequence,
    Map<AuctionTimeBasis, Long> lastObservedTimeMillisByBasis,
    Map<UUID, AuctionListing> listings,
    Map<UUID, AuctionRequestReceipt> requestReceipts
) {
    public static final int MAX_LISTINGS = 100000;
    public static final int MAX_REQUEST_RECEIPTS = 200000;

    public AuctionHouseSnapshot {
        if (nextAcceptedSequence < 0L) {
            throw new IllegalArgumentException("Auction snapshot sequence must not be negative.");
        }
        EnumMap<AuctionTimeBasis, Long> clocks = new EnumMap<>(AuctionTimeBasis.class);
        clocks.putAll(Objects.requireNonNull(
            lastObservedTimeMillisByBasis, "lastObservedTimeMillisByBasis"));
        if (clocks.size() != AuctionTimeBasis.values().length) {
            throw new IllegalArgumentException("Auction snapshot must persist every time basis clock.");
        }
        for (AuctionTimeBasis basis : AuctionTimeBasis.values()) {
            Long clock = clocks.get(basis);
            if (clock == null || clock < 0L) {
                throw new IllegalArgumentException("Auction snapshot clock must not be negative.");
            }
        }
        lastObservedTimeMillisByBasis = Map.copyOf(clocks);
        listings = Map.copyOf(new HashMap<>(Objects.requireNonNull(listings, "listings")));
        requestReceipts = Map.copyOf(new HashMap<>(
            Objects.requireNonNull(requestReceipts, "requestReceipts")));
        if (listings.size() > MAX_LISTINGS || requestReceipts.size() > MAX_REQUEST_RECEIPTS) {
            throw new IllegalArgumentException("Auction snapshot exceeds its record limits.");
        }
        AuctionSnapshotValidator.validate(
            nextAcceptedSequence, lastObservedTimeMillisByBasis, listings, requestReceipts);
    }

    public long lastObservedTimeMillis(AuctionTimeBasis basis) {
        return lastObservedTimeMillisByBasis.get(Objects.requireNonNull(basis, "basis"));
    }

    public static AuctionHouseSnapshot empty() {
        EnumMap<AuctionTimeBasis, Long> clocks = new EnumMap<>(AuctionTimeBasis.class);
        for (AuctionTimeBasis basis : AuctionTimeBasis.values()) {
            clocks.put(basis, 0L);
        }
        return new AuctionHouseSnapshot(0L, clocks, Map.of(), Map.of());
    }
}
