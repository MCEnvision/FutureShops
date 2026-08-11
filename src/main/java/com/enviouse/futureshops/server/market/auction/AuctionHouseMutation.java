package com.enviouse.futureshops.server.market.auction;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record AuctionHouseMutation(
        UUID requestId,
        String previousSnapshotFingerprint,
        long nextAcceptedSequence,
        Map<AuctionTimeBasis, Long> lastObservedTimeMillisByBasis,
        AuctionRequestReceipt requestReceipt
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public AuctionHouseMutation {
        requestId = Objects.requireNonNull(requestId, "requestId");
        previousSnapshotFingerprint = requireFingerprint(
                previousSnapshotFingerprint);
        requestReceipt = Objects.requireNonNull(requestReceipt,
                "requestReceipt");
        EnumMap<AuctionTimeBasis, Long> clocks =
                new EnumMap<>(AuctionTimeBasis.class);
        clocks.putAll(Objects.requireNonNull(lastObservedTimeMillisByBasis,
                "lastObservedTimeMillisByBasis"));
        if (requestId.equals(ZERO)
                || !requestId.equals(requestReceipt.result().requestId())
                || nextAcceptedSequence < 0L
                || clocks.size() != AuctionTimeBasis.values().length) {
            throw new IllegalArgumentException("Auction mutation identity is invalid");
        }
        for (AuctionTimeBasis basis : AuctionTimeBasis.values()) {
            Long value = clocks.get(basis);
            if (value == null || value < 0L) {
                throw new IllegalArgumentException("Auction mutation clock is invalid");
            }
        }
        lastObservedTimeMillisByBasis = Map.copyOf(clocks);
    }

    public static AuctionHouseMutation between(
            AuctionHouseSnapshot previous,
            AuctionHouseSnapshot next,
            UUID requestId
    ) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(requestId, "requestId");
        AuctionRequestReceipt receipt = next.requestReceipts().get(requestId);
        if (receipt == null) {
            throw new IllegalArgumentException("Auction mutation receipt is missing");
        }
        AuctionHouseMutation mutation = new AuctionHouseMutation(requestId,
                AuctionHouseSnapshotCodec.fingerprint(previous),
                next.nextAcceptedSequence(),
                next.lastObservedTimeMillisByBasis(), receipt);
        if (!mutation.apply(previous).snapshot().equals(next)) {
            throw new IllegalArgumentException("Auction mutation is not a single canonical transition");
        }
        return mutation;
    }

    public ApplyResult apply(AuctionHouseSnapshot previous) {
        Objects.requireNonNull(previous, "previous");
        AuctionRequestReceipt existing = previous.requestReceipts().get(
                requestId);
        if (existing != null) {
            if (!existing.equals(requestReceipt)) {
                throw new IllegalArgumentException("Auction mutation replay conflicts");
            }
            return new ApplyResult(previous, true);
        }
        if (!AuctionHouseSnapshotCodec.fingerprint(previous).equals(
                previousSnapshotFingerprint)) {
            throw new IllegalArgumentException("Auction mutation snapshot changed");
        }
        AuctionOperationResult result = requestReceipt.result();
        long expectedSequence = previous.nextAcceptedSequence();
        if (result.durablyApplied()
                && result.operation() == AuctionOperationType.BID) {
            expectedSequence = Math.incrementExact(expectedSequence);
        }
        if (nextAcceptedSequence != expectedSequence) {
            throw new IllegalArgumentException("Auction mutation sequence is invalid");
        }
        for (AuctionTimeBasis basis : AuctionTimeBasis.values()) {
            if (lastObservedTimeMillisByBasis.get(basis)
                    < previous.lastObservedTimeMillis(basis)) {
                throw new IllegalArgumentException("Auction mutation clock moved backward");
            }
        }
        Map<UUID, AuctionRequestReceipt> receipts = new HashMap<>(
                previous.requestReceipts());
        receipts.put(requestId, requestReceipt);
        if (receipts.size() != previous.requestReceipts().size() + 1) {
            throw new IllegalArgumentException("Auction mutation receipt count is invalid");
        }
        Map<UUID, AuctionListing> listings = new HashMap<>(
                previous.listings());
        if (result.durablyApplied()) {
            AuctionListing listing = result.listing().orElseThrow();
            AuctionListing current = listings.get(listing.listingId());
            if (result.operation() == AuctionOperationType.CREATE
                    ? current != null : current == null) {
                throw new IllegalArgumentException("Auction mutation listing ancestry is invalid");
            }
            listings.put(listing.listingId(), listing);
        }
        AuctionHouseSnapshot next = new AuctionHouseSnapshot(
                nextAcceptedSequence, lastObservedTimeMillisByBasis,
                listings, receipts);
        return new ApplyResult(next, false);
    }

    private static String requireFingerprint(String value) {
        String normalized = Objects.requireNonNull(value, "value")
                .strip().toLowerCase(Locale.ROOT);
        if (normalized.length() != 64
                || !normalized.chars().allMatch(character ->
                Character.digit(character, 16) >= 0)) {
            throw new IllegalArgumentException("Auction mutation fingerprint is invalid");
        }
        return normalized;
    }

    public record ApplyResult(AuctionHouseSnapshot snapshot,
                              boolean replayed) {
        public ApplyResult {
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }
    }
}
