package com.enviouse.futureshops.server.market.auction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class AuctionSnapshotValidator {
    private AuctionSnapshotValidator() {
    }

    static void validate(
        long nextAcceptedSequence,
        Map<AuctionTimeBasis, Long> clocks,
        Map<UUID, AuctionListing> listings,
        Map<UUID, AuctionRequestReceipt> receipts
    ) {
        Map<UUID, List<AuctionOperationResult>> applied = new HashMap<>();
        for (Map.Entry<UUID, AuctionListing> entry : listings.entrySet()) {
            AuctionListing listing = Objects.requireNonNull(entry.getValue(), "listing");
            if (!entry.getKey().equals(listing.listingId())) {
                throw invalid("Auction snapshot listing key is invalid.");
            }
        }
        for (Map.Entry<UUID, AuctionRequestReceipt> entry : receipts.entrySet()) {
            AuctionRequestReceipt receipt = Objects.requireNonNull(entry.getValue(), "requestReceipt");
            AuctionOperationResult result = receipt.result();
            if (!entry.getKey().equals(result.requestId())) {
                throw invalid("Auction snapshot request key is invalid.");
            }
            if (result.durablyApplied()) {
                applied.computeIfAbsent(result.listingId(), ignored -> new ArrayList<>()).add(result);
            }
        }

        AuctionIdentityRegistry identities = new AuctionIdentityRegistry();
        Set<Long> acceptedSequences = new HashSet<>();
        Map<UUID, Map<Long, AuctionListing>> canonicalRevisions = new HashMap<>();
        for (AuctionListing current : listings.values()) {
            List<AuctionOperationResult> chain = applied.remove(current.listingId());
            if (chain == null) {
                throw invalid("Auction listing has no applied creation evidence.");
            }
            chain.sort(Comparator.comparingLong(AuctionOperationResult::observedRevision));
            validateChain(current, chain, identities, acceptedSequences);
            Map<Long, AuctionListing> revisions = new HashMap<>();
            for (AuctionOperationResult result : chain) {
                AuctionListing previous = revisions.put(
                    result.observedRevision(), result.listing().orElseThrow());
                if (previous != null) {
                    throw invalid("Auction listing revision has duplicate applied evidence.");
                }
            }
            canonicalRevisions.put(current.listingId(), revisions);
        }
        if (!applied.isEmpty()) {
            throw invalid("Auction applied receipt refers to a missing canonical listing.");
        }
        for (AuctionRequestReceipt receipt : receipts.values()) {
            AuctionOperationResult result = receipt.result();
            result.listing().ifPresent(observed -> {
                Map<Long, AuctionListing> revisions = canonicalRevisions.get(observed.listingId());
                if (revisions == null || !observed.equals(revisions.get(observed.revision()))) {
                    throw invalid("Auction receipt contains noncanonical listing evidence.");
                }
            });
        }
        if (acceptedSequences.size() != nextAcceptedSequence) {
            throw invalid("Auction accepted sequence counter differs from applied bid evidence.");
        }
        for (long sequence = 1L; sequence <= nextAcceptedSequence; sequence++) {
            if (!acceptedSequences.contains(sequence)) {
                throw invalid("Auction accepted sequence evidence contains a gap.");
            }
        }
        for (AuctionListing listing : listings.values()) {
            long clock = clocks.get(listing.rules().timeBasis());
            if (clock < listing.lastObservedTimeMillis()) {
                throw invalid("Auction time basis clock is behind canonical evidence.");
            }
        }
    }

    private static void validateChain(
        AuctionListing current,
        List<AuctionOperationResult> chain,
        AuctionIdentityRegistry identities,
        Set<Long> acceptedSequences
    ) {
        long expectedSize;
        try {
            expectedSize = Math.incrementExact(current.revision());
        } catch (ArithmeticException exception) {
            throw invalid("Auction canonical revision arithmetic overflowed.");
        }
        if (chain.isEmpty() || chain.size() != expectedSize) {
            throw invalid("Auction applied receipt chain is incomplete.");
        }
        AuctionListing previous = null;
        for (int index = 0; index < chain.size(); index++) {
            AuctionOperationResult result = chain.get(index);
            AuctionListing next = result.listing().orElseThrow();
            if (result.observedRevision() != index || next.revision() != index) {
                throw invalid("Auction applied receipt revisions are not contiguous.");
            }
            if (index == 0) {
                validateCreate(result, next);
            } else {
                validateTransition(previous, result, next, acceptedSequences);
            }
            identities.claimListing(next);
            previous = next;
        }
        if (!current.equals(previous)) {
            throw invalid("Auction canonical listing is not the final applied revision.");
        }
    }

    private static void validateCreate(AuctionOperationResult result, AuctionListing listing) {
        long expectedDeadline = 0L;
        if (listing.type().acceptsBids()) {
            try {
                expectedDeadline = Math.addExact(
                    listing.createdAtMillis(), listing.originalDurationMillis());
            } catch (ArithmeticException exception) {
                throw invalid("Auction creation deadline arithmetic overflowed.");
            }
        }
        if (result.operation() != AuctionOperationType.CREATE
            || listing.state() != AuctionListingState.ACTIVE
            || listing.revision() != 0L
            || listing.lastObservedTimeMillis() != listing.createdAtMillis()
            || listing.deadlineMillis() != expectedDeadline
            || listing.frozenRemainingMillis() != 0L
            || listing.antiSnipeExtensionCount() != 0
            || listing.antiSnipeCumulativeMillis() != 0L
            || listing.acceptedBidCount() != 0L
            || listing.highestBid().isPresent()
            || listing.sale().isPresent()
            || listing.terminalTransactionId().isPresent()) {
            throw invalid("Auction creation receipt is invalid.");
        }
        requireNoValueMovement(result);
    }

    private static void validateTransition(
        AuctionListing previous,
        AuctionOperationResult result,
        AuctionListing next,
        Set<Long> acceptedSequences
    ) {
        requireImmutableFields(previous, next);
        long expectedRevision;
        try {
            expectedRevision = Math.incrementExact(previous.revision());
        } catch (ArithmeticException exception) {
            throw invalid("Auction revision arithmetic overflowed.");
        }
        if (next.revision() != expectedRevision
            || next.lastObservedTimeMillis() < previous.lastObservedTimeMillis()) {
            throw invalid("Auction applied transition counters are invalid.");
        }
        switch (result.operation()) {
            case CREATE -> throw invalid("Auction creation cannot follow another revision.");
            case BID -> validateBid(previous, result, next, acceptedSequences);
            case BUY_NOW -> validateBuyNow(previous, result, next);
            case CANCEL -> validateCancel(previous, result, next);
            case EXPIRE -> validateExpire(previous, result, next);
            case FREEZE -> validateFreeze(previous, result, next);
            case RESUME -> validateResume(previous, result, next);
            case SETTLE -> validateSettle(previous, result, next);
        }
    }

    private static void validateBid(
        AuctionListing previous,
        AuctionOperationResult result,
        AuctionListing next,
        Set<Long> acceptedSequences
    ) {
        AuctionBidStanding bid = next.highestBid().orElseThrow(
            () -> invalid("Applied auction bid requires a standing."));
        Optional<AuctionBidStanding> displaced = previous.highestBid();
        long expectedDelta;
        long expectedRefund;
        long expectedAdded;
        long expectedCumulative;
        long expectedCount;
        long minimumBid;
        long ruleExtension;
        int expectedExtensionCount;
        boolean ownRaise = displaced.map(AuctionBidStanding::bidderId)
            .filter(bid.bidderId()::equals).isPresent();
        try {
            expectedDelta = ownRaise
                ? Math.subtractExact(bid.amountMinor(), displaced.orElseThrow().amountMinor())
                : bid.amountMinor();
            expectedRefund = ownRaise ? 0L
                : displaced.map(AuctionBidStanding::amountMinor).orElse(0L);
            expectedAdded = Math.subtractExact(next.deadlineMillis(), previous.deadlineMillis());
            expectedCumulative = Math.addExact(
                previous.antiSnipeCumulativeMillis(), expectedAdded);
            expectedCount = Math.incrementExact(previous.acceptedBidCount());
            expectedExtensionCount = Math.addExact(previous.antiSnipeExtensionCount(),
                expectedAdded > 0L ? 1 : 0);
            minimumBid = previous.minimumNextBid();
            ruleExtension = antiSnipeExtension(previous, next.lastObservedTimeMillis());
        } catch (ArithmeticException exception) {
            throw invalid("Auction bid evidence arithmetic overflowed.");
        }
        if (previous.state() != AuctionListingState.ACTIVE
            || next.state() != AuctionListingState.ACTIVE
            || bid.bidderId().equals(previous.sellerId())
            || bid.acceptedAtMillis() >= previous.deadlineMillis()
            || displaced.isPresent() && bid.acceptedSequence()
                <= displaced.orElseThrow().acceptedSequence()
            || bid.amountMinor() < minimumBid
            || previous.type() == AuctionListingType.AUCTION_WITH_BUYOUT
                && bid.amountMinor() >= previous.buyoutMinor()
            || bid.bidId().equals(displaced.map(AuctionBidStanding::bidId).orElse(null))
            || bid.holdTransactionId().equals(
                displaced.map(AuctionBidStanding::holdTransactionId).orElse(null))
            || ownRaise && !bid.holdAccountId().equals(
                displaced.orElseThrow().holdAccountId())
            || !ownRaise && displaced.isPresent()
                && bid.holdAccountId().equals(displaced.orElseThrow().holdAccountId())
            || expectedDelta <= 0L
            || result.requiredHoldDeltaMinor() != expectedDelta
            || result.refundMinor() != expectedRefund
            || !result.displacedBid().equals(displaced)
            || !result.refundPlayerId().equals(expectedRefund == 0L
                ? Optional.empty()
                : Optional.of(displaced.orElseThrow().bidderId()))
            || result.antiSnipeAddedMillis() != expectedAdded
            || result.antiSnipeAddedMillis() != ruleExtension
            || expectedAdded < 0L
            || next.antiSnipeCumulativeMillis() != expectedCumulative
            || next.antiSnipeExtensionCount() != expectedExtensionCount
            || next.acceptedBidCount() != expectedCount
            || bid.acceptedAtMillis() != next.lastObservedTimeMillis()
            || next.frozenRemainingMillis() != 0L
            || next.sale().isPresent()
            || next.terminalTransactionId().isPresent()) {
            throw invalid("Auction bid receipt does not match its state transition.");
        }
        if (!acceptedSequences.add(bid.acceptedSequence())) {
            throw invalid("Auction bid sequence is not unique.");
        }
    }

    private static void validateBuyNow(
        AuctionListing previous,
        AuctionOperationResult result,
        AuctionListing next
    ) {
        AuctionSale sale = next.sale().orElseThrow(
            () -> invalid("Auction buyout requires sale evidence."));
        Optional<AuctionBidStanding> displaced = previous.highestBid();
        boolean ownsTop = displaced.map(AuctionBidStanding::bidderId)
            .filter(sale.buyerId()::equals).isPresent();
        long expectedDelta;
        long expectedRefund;
        try {
            expectedDelta = ownsTop
                ? Math.subtractExact(previous.buyoutMinor(), displaced.orElseThrow().amountMinor())
                : previous.buyoutMinor();
            expectedRefund = ownsTop ? 0L
                : displaced.map(AuctionBidStanding::amountMinor).orElse(0L);
        } catch (ArithmeticException exception) {
            throw invalid("Auction buyout evidence arithmetic overflowed.");
        }
        if (previous.state() != AuctionListingState.ACTIVE
            || next.state() != AuctionListingState.SOLD_PENDING
            || !previous.type().hasBuyout()
            || sale.buyerId().equals(previous.sellerId())
            || previous.type().acceptsBids()
                && next.lastObservedTimeMillis() >= previous.deadlineMillis()
            || !sale.buyout()
            || sale.priceMinor() != previous.buyoutMinor()
            || sale.soldAtMillis() != next.lastObservedTimeMillis()
            || ownsTop && !sale.holdAccountId().equals(
                displaced.orElseThrow().holdAccountId())
            || ownsTop && sale.holdTransactionId().equals(
                displaced.orElseThrow().holdTransactionId())
            || !ownsTop && displaced.isPresent()
                && sale.holdAccountId().equals(displaced.orElseThrow().holdAccountId())
            || result.requiredHoldDeltaMinor() != expectedDelta
            || result.refundMinor() != expectedRefund
            || !result.displacedBid().equals(displaced)
            || !result.refundPlayerId().equals(expectedRefund == 0L
                ? Optional.empty()
                : Optional.of(displaced.orElseThrow().bidderId()))
            || result.antiSnipeAddedMillis() != 0L
            || !next.highestBid().equals(previous.highestBid())
            || next.deadlineMillis() != previous.deadlineMillis()
            || next.frozenRemainingMillis() != 0L
            || next.antiSnipeExtensionCount() != previous.antiSnipeExtensionCount()
            || next.antiSnipeCumulativeMillis() != previous.antiSnipeCumulativeMillis()
            || next.acceptedBidCount() != previous.acceptedBidCount()
            || !next.terminalTransactionId().equals(Optional.of(sale.settlementTransactionId()))) {
            throw invalid("Auction buyout receipt does not match its state transition.");
        }
    }

    private static void validateCancel(
        AuctionListing previous,
        AuctionOperationResult result,
        AuctionListing next
    ) {
        if ((previous.state() != AuctionListingState.ACTIVE
            && previous.state() != AuctionListingState.FROZEN)
            || next.state() != AuctionListingState.CANCELLED
            || !next.highestBid().equals(previous.highestBid())
            || next.sale().isPresent()
            || next.terminalTransactionId().isEmpty()
            || next.deadlineMillis() != previous.deadlineMillis()
            || next.frozenRemainingMillis() != 0L
            || next.antiSnipeExtensionCount() != previous.antiSnipeExtensionCount()
            || next.antiSnipeCumulativeMillis() != previous.antiSnipeCumulativeMillis()
            || next.acceptedBidCount() != previous.acceptedBidCount()) {
            throw invalid("Auction cancellation receipt does not match its state transition.");
        }
        if (previous.highestBid().isPresent()) {
            AuctionBidStanding bid = previous.highestBid().orElseThrow();
            if (result.refundMinor() != bid.amountMinor()
                    || !result.refundPlayerId().equals(
                    Optional.of(bid.bidderId()))
                    || !result.displacedBid().equals(Optional.of(bid))
                    || result.requiredHoldDeltaMinor() != 0L
                    || result.antiSnipeAddedMillis() != 0L) {
                throw invalid(
                        "Auction forced cancellation refund is invalid.");
            }
        } else {
            requireNoValueMovement(result);
        }
    }

    private static void validateExpire(
        AuctionListing previous,
        AuctionOperationResult result,
        AuctionListing next
    ) {
        boolean sold = previous.highestBid().isPresent();
        if (previous.state() != AuctionListingState.ACTIVE
            || !previous.type().acceptsBids()
            || next.lastObservedTimeMillis() < previous.deadlineMillis()
            || next.state() != (sold
                ? AuctionListingState.SOLD_PENDING
                : AuctionListingState.ENDED_UNSOLD)
            || !next.highestBid().equals(previous.highestBid())
            || next.deadlineMillis() != previous.deadlineMillis()
            || next.frozenRemainingMillis() != 0L
            || next.antiSnipeExtensionCount() != previous.antiSnipeExtensionCount()
            || next.antiSnipeCumulativeMillis() != previous.antiSnipeCumulativeMillis()
            || next.acceptedBidCount() != previous.acceptedBidCount()
            || next.terminalTransactionId().isEmpty()) {
            throw invalid("Auction expiration receipt does not match its state transition.");
        }
        if (sold) {
            AuctionBidStanding bid = previous.highestBid().orElseThrow();
            AuctionSale sale = next.sale().orElseThrow(
                () -> invalid("Expired sold auction requires sale evidence."));
            if (sale.buyout()
                || !sale.buyerId().equals(bid.bidderId())
                || !sale.holdAccountId().equals(bid.holdAccountId())
                || !sale.holdTransactionId().equals(bid.holdTransactionId())
                || sale.priceMinor() != bid.amountMinor()
                || sale.soldAtMillis() != next.lastObservedTimeMillis()
                || !next.terminalTransactionId().equals(
                    Optional.of(sale.settlementTransactionId()))) {
                throw invalid("Expired auction sale differs from its winning bid.");
            }
        } else if (next.sale().isPresent()) {
            throw invalid("Unsold auction cannot contain sale evidence.");
        }
        requireNoValueMovement(result);
    }

    private static void validateFreeze(
        AuctionListing previous,
        AuctionOperationResult result,
        AuctionListing next
    ) {
        long expectedRemaining = 0L;
        if (previous.type().acceptsBids()) {
            try {
                expectedRemaining = Math.subtractExact(
                    previous.deadlineMillis(), next.lastObservedTimeMillis());
            } catch (ArithmeticException exception) {
                throw invalid("Auction freeze evidence arithmetic overflowed.");
            }
        }
        if (previous.state() != AuctionListingState.ACTIVE
            || next.state() != AuctionListingState.FROZEN
            || expectedRemaining < 0L
            || previous.type().acceptsBids() && expectedRemaining == 0L
            || next.frozenRemainingMillis() != expectedRemaining
            || next.deadlineMillis() != previous.deadlineMillis()
            || !next.highestBid().equals(previous.highestBid())
            || next.sale().isPresent()
            || next.terminalTransactionId().isPresent()
            || next.antiSnipeExtensionCount() != previous.antiSnipeExtensionCount()
            || next.antiSnipeCumulativeMillis() != previous.antiSnipeCumulativeMillis()
            || next.acceptedBidCount() != previous.acceptedBidCount()) {
            throw invalid("Auction freeze receipt does not match its state transition.");
        }
        requireNoValueMovement(result);
    }

    private static void validateResume(
        AuctionListing previous,
        AuctionOperationResult result,
        AuctionListing next
    ) {
        long expectedDeadline = previous.deadlineMillis();
        if (previous.type().acceptsBids() && previous.rules().pauseWhileFrozen()) {
            try {
                expectedDeadline = Math.addExact(
                    next.lastObservedTimeMillis(), previous.frozenRemainingMillis());
            } catch (ArithmeticException exception) {
                throw invalid("Auction resume evidence arithmetic overflowed.");
            }
        }
        if (previous.state() != AuctionListingState.FROZEN
            || next.state() != AuctionListingState.ACTIVE
            || next.deadlineMillis() != expectedDeadline
            || next.frozenRemainingMillis() != 0L
            || !next.highestBid().equals(previous.highestBid())
            || next.sale().isPresent()
            || next.terminalTransactionId().isPresent()
            || next.antiSnipeExtensionCount() != previous.antiSnipeExtensionCount()
            || next.antiSnipeCumulativeMillis() != previous.antiSnipeCumulativeMillis()
            || next.acceptedBidCount() != previous.acceptedBidCount()) {
            throw invalid("Auction resume receipt does not match its state transition.");
        }
        requireNoValueMovement(result);
    }

    private static void validateSettle(
        AuctionListing previous,
        AuctionOperationResult result,
        AuctionListing next
    ) {
        if (previous.state() != AuctionListingState.SOLD_PENDING
            || next.state() != AuctionListingState.SETTLED
            || !next.highestBid().equals(previous.highestBid())
            || !next.sale().equals(previous.sale())
            || !next.terminalTransactionId().equals(previous.terminalTransactionId())
            || next.deadlineMillis() != previous.deadlineMillis()
            || next.frozenRemainingMillis() != 0L
            || next.antiSnipeExtensionCount() != previous.antiSnipeExtensionCount()
            || next.antiSnipeCumulativeMillis() != previous.antiSnipeCumulativeMillis()
            || next.acceptedBidCount() != previous.acceptedBidCount()) {
            throw invalid("Auction settlement receipt does not match its state transition.");
        }
        requireNoValueMovement(result);
    }

    private static void requireImmutableFields(AuctionListing previous, AuctionListing next) {
        if (!previous.listingId().equals(next.listingId())
            || !previous.sellerId().equals(next.sellerId())
            || !previous.activationTransactionId().equals(next.activationTransactionId())
            || !previous.itemLot().equals(next.itemLot())
            || previous.type() != next.type()
            || previous.startingBidMinor() != next.startingBidMinor()
            || previous.buyoutMinor() != next.buyoutMinor()
            || !previous.rules().equals(next.rules())
            || previous.createdAtMillis() != next.createdAtMillis()
            || previous.originalDurationMillis() != next.originalDurationMillis()) {
            throw invalid("Auction immutable listing fields changed after activation.");
        }
    }

    private static void requireNoValueMovement(AuctionOperationResult result) {
        if (result.requiredHoldDeltaMinor() != 0L
            || result.refundMinor() != 0L
            || result.antiSnipeAddedMillis() != 0L
            || result.refundPlayerId().isPresent()
            || result.displacedBid().isPresent()) {
            throw invalid("Auction operation contains unexpected value movement evidence.");
        }
    }

    private static long antiSnipeExtension(AuctionListing listing, long effectiveNow) {
        AuctionRuleSnapshot rules = listing.rules();
        if (!rules.antiSnipeEnabled()
            || listing.antiSnipeExtensionCount() >= rules.maximumAntiSnipeExtensionCount()
            || Math.subtractExact(listing.deadlineMillis(), effectiveNow)
                > rules.antiSnipeTriggerMillis()) {
            return 0L;
        }
        long capacity = Math.subtractExact(
            rules.maximumAntiSnipeCumulativeMillis(), listing.antiSnipeCumulativeMillis());
        return Math.min(rules.antiSnipeExtensionMillis(), Math.max(0L, capacity));
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
