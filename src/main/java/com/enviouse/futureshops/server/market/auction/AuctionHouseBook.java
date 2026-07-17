package com.enviouse.futureshops.server.market.auction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AuctionHouseBook {
    private final Map<UUID, AuctionListing> listings = new HashMap<>();
    private final Map<UUID, AuctionRequestReceipt> requests = new HashMap<>();
    private final EnumMap<AuctionTimeBasis, Long> lastObservedTimeMillisByBasis =
        new EnumMap<>(AuctionTimeBasis.class);
    private final AuctionIdentityRegistry identities = new AuctionIdentityRegistry();
    private long nextAcceptedSequence;

    public AuctionHouseBook() {
        for (AuctionTimeBasis basis : AuctionTimeBasis.values()) {
            lastObservedTimeMillisByBasis.put(basis, 0L);
        }
    }

    public AuctionHouseBook(AuctionHouseSnapshot snapshot) {
        this();
        Objects.requireNonNull(snapshot, "snapshot");
        listings.putAll(snapshot.listings());
        requests.putAll(snapshot.requestReceipts());
        nextAcceptedSequence = snapshot.nextAcceptedSequence();
        lastObservedTimeMillisByBasis.putAll(snapshot.lastObservedTimeMillisByBasis());
        snapshot.requestReceipts().values().stream()
            .map(AuctionRequestReceipt::result)
            .filter(AuctionOperationResult::durablyApplied)
            .flatMap(result -> result.listing().stream())
            .forEach(identities::claimListing);
    }

    public synchronized AuctionOperationResult create(CreateAuctionCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = AuctionRequestFingerprints.create(command);
        AuctionOperationResult replay = replay(
            command.requestId(), command.listingId(), AuctionOperationType.CREATE, fingerprint);
        if (replay != null) {
            return replay;
        }
        AuctionListing existing = listings.get(command.listingId());
        if (existing != null) {
            return remember(fingerprint, rejected(
                command.requestId(), command.listingId(), AuctionOperationType.CREATE,
                AuctionOperationStatus.LISTING_EXISTS, existing));
        }
        if (listings.size() >= AuctionHouseSnapshot.MAX_LISTINGS) {
            return remember(fingerprint, rejected(
                command.requestId(), command.listingId(), AuctionOperationType.CREATE,
                AuctionOperationStatus.CAPACITY_EXCEEDED, null));
        }
        if (command.activationTransactionId().equals(command.itemLot().custodyLotId())
            || !identities.isUnused(command.activationTransactionId())
            || !identities.isUnused(command.itemLot().custodyLotId())) {
            return remember(fingerprint, rejected(
                command.requestId(), command.listingId(), AuctionOperationType.CREATE,
                AuctionOperationStatus.IDENTITY_CONFLICT, null));
        }
        long createdAt = observeTime(command.rules().timeBasis(), command.createdAtMillis());
        AuctionListing listing;
        try {
            long duration = command.type().acceptsBids()
                ? Math.subtractExact(command.deadlineMillis(), command.createdAtMillis())
                : 0L;
            long deadline = command.type().acceptsBids()
                ? Math.addExact(createdAt, duration)
                : 0L;
            listing = new AuctionListing(
                command.listingId(),
                command.sellerId(),
                command.activationTransactionId(),
                command.itemLot(),
                command.type(),
                AuctionListingState.ACTIVE,
                0L,
                command.startingBidMinor(),
                command.buyoutMinor(),
                command.rules(),
                createdAt,
                deadline,
                duration,
                createdAt,
                0L,
                0,
                0L,
                0L,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
            );
        } catch (ArithmeticException exception) {
            return remember(fingerprint, rejected(
                command.requestId(), command.listingId(), AuctionOperationType.CREATE,
                AuctionOperationStatus.INVALID_TRANSITION, null));
        }
        identities.claimListing(listing);
        listings.put(listing.listingId(), listing);
        return remember(fingerprint, applied(
            command.requestId(), listing, AuctionOperationType.CREATE,
            0L, 0L, 0L, Optional.empty(), Optional.empty()));
    }

    public synchronized AuctionOperationResult bid(PlaceAuctionBidCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = AuctionRequestFingerprints.bid(command);
        AuctionOperationResult replay = replay(
            command.requestId(), command.listingId(), AuctionOperationType.BID, fingerprint);
        if (replay != null) {
            return replay;
        }
        AuctionListing listing = listings.get(command.listingId());
        long effectiveNow = listing == null
            ? command.receivedAtMillis()
            : observeTime(listing.rules().timeBasis(), command.receivedAtMillis());
        AuctionOperationResult rejection = commonActiveRejection(
            command.requestId(), command.listingId(), AuctionOperationType.BID,
            command.expectedRevision(), effectiveNow, listing, true);
        if (rejection != null) {
            return remember(fingerprint, rejection);
        }
        if (listing.sellerId().equals(command.bidderId())) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.BID, AuctionOperationStatus.SELLER_SELF_ACTION, listing));
        }
        long minimum;
        try {
            minimum = listing.minimumNextBid();
        } catch (ArithmeticException exception) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.BID, AuctionOperationStatus.INVALID_TRANSITION, listing));
        }
        if (command.amountMinor() < minimum) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.BID, AuctionOperationStatus.BID_TOO_LOW, listing));
        }
        if (listing.type() == AuctionListingType.AUCTION_WITH_BUYOUT
            && command.amountMinor() >= listing.buyoutMinor()) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.BID, AuctionOperationStatus.BID_AT_OR_ABOVE_BUYOUT, listing));
        }
        Optional<AuctionBidStanding> previous = listing.highestBid();
        boolean raisesOwnBid = previous.map(AuctionBidStanding::bidderId)
            .filter(command.bidderId()::equals).isPresent();
        long expectedDelta;
        try {
            expectedDelta = raisesOwnBid
                ? Math.subtractExact(command.amountMinor(), previous.orElseThrow().amountMinor())
                : command.amountMinor();
        } catch (ArithmeticException exception) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.BID, AuctionOperationStatus.HOLD_MISMATCH, listing));
        }
        if (expectedDelta <= 0L || command.heldDeltaMinor() != expectedDelta) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.BID, AuctionOperationStatus.HOLD_MISMATCH, listing));
        }
        if (!identities.isUnused(command.bidId())
            || !identities.isUnused(command.holdTransactionId())
            || !allDistinct(command.bidId(), command.holdAccountId(), command.holdTransactionId())
            || raisesOwnBid && (!command.holdAccountId().equals(
                previous.orElseThrow().holdAccountId())
                || !identities.ownsHoldAccount(
                    command.holdAccountId(), listing.listingId(), command.bidderId()))
            || !raisesOwnBid && !identities.isUnused(command.holdAccountId())) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.BID, AuctionOperationStatus.IDENTITY_CONFLICT, listing));
        }
        effectiveNow = listing.clampTime(effectiveNow);
        long added;
        long deadline;
        long sequence;
        try {
            added = antiSnipeExtension(listing, effectiveNow);
            deadline = Math.addExact(listing.deadlineMillis(), added);
            sequence = Math.incrementExact(nextAcceptedSequence);
        } catch (ArithmeticException exception) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.BID, AuctionOperationStatus.INVALID_TRANSITION, listing));
        }
        AuctionBidStanding standing = new AuctionBidStanding(
            command.bidId(), command.bidderId(), command.holdAccountId(), command.holdTransactionId(),
            command.amountMinor(), sequence, effectiveNow);
        AuctionListing updated;
        try {
            int extensionCount = Math.addExact(
                listing.antiSnipeExtensionCount(), added > 0L ? 1 : 0);
            updated = copy(
                listing,
                AuctionListingState.ACTIVE,
                Math.incrementExact(listing.revision()),
                deadline,
                effectiveNow,
                0L,
                extensionCount,
                Math.addExact(listing.antiSnipeCumulativeMillis(), added),
                Math.incrementExact(listing.acceptedBidCount()),
                Optional.of(standing),
                Optional.empty(),
                Optional.empty()
            );
        } catch (ArithmeticException exception) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.BID, AuctionOperationStatus.INVALID_TRANSITION, listing));
        }
        identities.claimListing(updated);
        nextAcceptedSequence = sequence;
        listings.put(updated.listingId(), updated);
        long refund = raisesOwnBid ? 0L : previous.map(AuctionBidStanding::amountMinor).orElse(0L);
        Optional<UUID> refundPlayer = refund == 0L
            ? Optional.empty()
            : Optional.of(previous.orElseThrow().bidderId());
        return remember(fingerprint, applied(
            command.requestId(), updated, AuctionOperationType.BID,
            expectedDelta, refund, added, refundPlayer, previous));
    }

    public synchronized AuctionOperationResult buyNow(AuctionBuyNowCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = AuctionRequestFingerprints.buyNow(command);
        AuctionOperationResult replay = replay(
            command.requestId(), command.listingId(), AuctionOperationType.BUY_NOW, fingerprint);
        if (replay != null) {
            return replay;
        }
        AuctionListing listing = listings.get(command.listingId());
        long effectiveNow = listing == null
            ? command.receivedAtMillis()
            : observeTime(listing.rules().timeBasis(), command.receivedAtMillis());
        AuctionOperationResult rejection = commonActiveRejection(
            command.requestId(), command.listingId(), AuctionOperationType.BUY_NOW,
            command.expectedRevision(), effectiveNow, listing, false);
        if (rejection != null) {
            return remember(fingerprint, rejection);
        }
        if (!listing.type().hasBuyout()) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.BUY_NOW, AuctionOperationStatus.BUYOUT_UNAVAILABLE, listing));
        }
        if (listing.sellerId().equals(command.buyerId())) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.BUY_NOW, AuctionOperationStatus.SELLER_SELF_ACTION, listing));
        }
        Optional<AuctionBidStanding> previous = listing.highestBid();
        boolean buyerOwnsTopBid = previous.map(AuctionBidStanding::bidderId)
            .filter(command.buyerId()::equals).isPresent();
        long expectedDelta;
        try {
            expectedDelta = buyerOwnsTopBid
                ? Math.subtractExact(listing.buyoutMinor(), previous.orElseThrow().amountMinor())
                : listing.buyoutMinor();
        } catch (ArithmeticException exception) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.BUY_NOW, AuctionOperationStatus.HOLD_MISMATCH, listing));
        }
        if (expectedDelta <= 0L || expectedDelta != command.heldDeltaMinor()) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.BUY_NOW, AuctionOperationStatus.HOLD_MISMATCH, listing));
        }
        if (!identities.isUnused(command.holdTransactionId())
            || !identities.isUnused(command.settlementTransactionId())
            || !allDistinct(command.holdAccountId(), command.holdTransactionId(),
                command.settlementTransactionId())
            || buyerOwnsTopBid && (!command.holdAccountId().equals(
                previous.orElseThrow().holdAccountId())
                || !identities.ownsHoldAccount(
                    command.holdAccountId(), listing.listingId(), command.buyerId()))
            || !buyerOwnsTopBid && !identities.isUnused(command.holdAccountId())) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.BUY_NOW, AuctionOperationStatus.IDENTITY_CONFLICT, listing));
        }
        effectiveNow = listing.clampTime(effectiveNow);
        AuctionSale sale = new AuctionSale(
            command.buyerId(), command.holdAccountId(), command.holdTransactionId(),
            command.settlementTransactionId(), listing.buyoutMinor(), effectiveNow, true);
        AuctionListing updated;
        try {
            updated = copy(
                listing,
                AuctionListingState.SOLD_PENDING,
                Math.incrementExact(listing.revision()),
                listing.deadlineMillis(),
                effectiveNow,
                0L,
                listing.antiSnipeExtensionCount(),
                listing.antiSnipeCumulativeMillis(),
                listing.acceptedBidCount(),
                previous,
                Optional.of(sale),
                Optional.of(command.settlementTransactionId())
            );
        } catch (ArithmeticException exception) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.BUY_NOW, AuctionOperationStatus.INVALID_TRANSITION, listing));
        }
        identities.claimListing(updated);
        listings.put(updated.listingId(), updated);
        long refund = buyerOwnsTopBid ? 0L : previous.map(AuctionBidStanding::amountMinor).orElse(0L);
        Optional<UUID> refundPlayer = refund == 0L
            ? Optional.empty()
            : Optional.of(previous.orElseThrow().bidderId());
        return remember(fingerprint, applied(
            command.requestId(), updated, AuctionOperationType.BUY_NOW,
            expectedDelta, refund, 0L, refundPlayer, previous));
    }

    public synchronized AuctionOperationResult cancel(CancelAuctionCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = AuctionRequestFingerprints.cancel(command);
        AuctionOperationResult replay = replay(
            command.requestId(), command.listingId(), AuctionOperationType.CANCEL, fingerprint);
        if (replay != null) {
            return replay;
        }
        AuctionListing listing = listings.get(command.listingId());
        long effectiveNow = listing == null
            ? command.receivedAtMillis()
            : observeTime(listing.rules().timeBasis(), command.receivedAtMillis());
        if (listing == null) {
            return remember(fingerprint, missing(command.requestId(), command.listingId(), AuctionOperationType.CANCEL));
        }
        if (command.expectedRevision() != listing.revision()) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.CANCEL, AuctionOperationStatus.STALE_REVISION, listing));
        }
        if (listing.state() != AuctionListingState.ACTIVE && listing.state() != AuctionListingState.FROZEN) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.CANCEL, AuctionOperationStatus.NOT_ACTIVE, listing));
        }
        if (!listing.sellerId().equals(command.actorId())) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.CANCEL, AuctionOperationStatus.NOT_SELLER, listing));
        }
        if (!listing.rules().allowSellerCancelBeforeBid() || listing.highestBid().isPresent()) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.CANCEL, AuctionOperationStatus.CANCELLATION_DENIED, listing));
        }
        if (!identities.isUnused(command.terminalTransactionId())) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.CANCEL, AuctionOperationStatus.IDENTITY_CONFLICT, listing));
        }
        effectiveNow = listing.clampTime(effectiveNow);
        AuctionListing updated;
        try {
            updated = copy(
                listing,
                AuctionListingState.CANCELLED,
                Math.incrementExact(listing.revision()),
                listing.deadlineMillis(),
                effectiveNow,
                0L,
                listing.antiSnipeExtensionCount(),
                listing.antiSnipeCumulativeMillis(),
                listing.acceptedBidCount(),
                listing.highestBid(),
                Optional.empty(),
                Optional.of(command.terminalTransactionId())
            );
        } catch (ArithmeticException exception) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.CANCEL, AuctionOperationStatus.INVALID_TRANSITION, listing));
        }
        identities.claimListing(updated);
        listings.put(updated.listingId(), updated);
        return remember(fingerprint, applied(
            command.requestId(), updated, AuctionOperationType.CANCEL,
            0L, 0L, 0L, Optional.empty(), Optional.empty()));
    }

    public synchronized AuctionOperationResult expire(ExpireAuctionCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = AuctionRequestFingerprints.expire(command);
        AuctionOperationResult replay = replay(
            command.requestId(), command.listingId(), AuctionOperationType.EXPIRE, fingerprint);
        if (replay != null) {
            return replay;
        }
        AuctionListing listing = listings.get(command.listingId());
        long effectiveNow = listing == null
            ? command.receivedAtMillis()
            : observeTime(listing.rules().timeBasis(), command.receivedAtMillis());
        if (listing == null) {
            return remember(fingerprint, missing(command.requestId(), command.listingId(), AuctionOperationType.EXPIRE));
        }
        if (command.expectedRevision() != listing.revision()) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.EXPIRE, AuctionOperationStatus.STALE_REVISION, listing));
        }
        if (listing.state() != AuctionListingState.ACTIVE || !listing.type().acceptsBids()) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.EXPIRE, AuctionOperationStatus.NOT_ACTIVE, listing));
        }
        effectiveNow = listing.clampTime(effectiveNow);
        if (effectiveNow < listing.deadlineMillis()) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.EXPIRE, AuctionOperationStatus.DEADLINE_NOT_REACHED, listing));
        }
        if (!identities.isUnused(command.terminalTransactionId())) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.EXPIRE, AuctionOperationStatus.IDENTITY_CONFLICT, listing));
        }
        long soldAt = effectiveNow;
        Optional<AuctionSale> sale = listing.highestBid().map(bid -> new AuctionSale(
            bid.bidderId(), bid.holdAccountId(), bid.holdTransactionId(),
            command.terminalTransactionId(), bid.amountMinor(), soldAt, false));
        AuctionListingState state = sale.isPresent()
            ? AuctionListingState.SOLD_PENDING
            : AuctionListingState.ENDED_UNSOLD;
        AuctionListing updated;
        try {
            updated = copy(
                listing,
                state,
                Math.incrementExact(listing.revision()),
                listing.deadlineMillis(),
                effectiveNow,
                0L,
                listing.antiSnipeExtensionCount(),
                listing.antiSnipeCumulativeMillis(),
                listing.acceptedBidCount(),
                listing.highestBid(),
                sale,
                Optional.of(command.terminalTransactionId())
            );
        } catch (ArithmeticException exception) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.EXPIRE, AuctionOperationStatus.INVALID_TRANSITION, listing));
        }
        identities.claimListing(updated);
        listings.put(updated.listingId(), updated);
        return remember(fingerprint, applied(
            command.requestId(), updated, AuctionOperationType.EXPIRE,
            0L, 0L, 0L, Optional.empty(), Optional.empty()));
    }

    public synchronized AuctionOperationResult freeze(FreezeAuctionCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = AuctionRequestFingerprints.freeze(command);
        AuctionOperationResult replay = replay(
            command.requestId(), command.listingId(), AuctionOperationType.FREEZE, fingerprint);
        if (replay != null) {
            return replay;
        }
        AuctionListing listing = listings.get(command.listingId());
        long effectiveNow = listing == null
            ? command.receivedAtMillis()
            : observeTime(listing.rules().timeBasis(), command.receivedAtMillis());
        if (listing == null) {
            return remember(fingerprint, missing(command.requestId(), command.listingId(), AuctionOperationType.FREEZE));
        }
        if (command.expectedRevision() != listing.revision()) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.FREEZE, AuctionOperationStatus.STALE_REVISION, listing));
        }
        if (listing.state() != AuctionListingState.ACTIVE) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.FREEZE, AuctionOperationStatus.NOT_ACTIVE, listing));
        }
        effectiveNow = listing.clampTime(effectiveNow);
        if (listing.type().acceptsBids() && effectiveNow >= listing.deadlineMillis()) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.FREEZE, AuctionOperationStatus.DEADLINE_PASSED, listing));
        }
        AuctionListing updated;
        try {
            long remaining = listing.type().acceptsBids()
                ? Math.subtractExact(listing.deadlineMillis(), effectiveNow)
                : 0L;
            updated = copy(
                listing,
                AuctionListingState.FROZEN,
                Math.incrementExact(listing.revision()),
                listing.deadlineMillis(),
                effectiveNow,
                remaining,
                listing.antiSnipeExtensionCount(),
                listing.antiSnipeCumulativeMillis(),
                listing.acceptedBidCount(),
                listing.highestBid(),
                Optional.empty(),
                Optional.empty()
            );
        } catch (ArithmeticException exception) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.FREEZE, AuctionOperationStatus.INVALID_TRANSITION, listing));
        }
        listings.put(updated.listingId(), updated);
        return remember(fingerprint, applied(
            command.requestId(), updated, AuctionOperationType.FREEZE,
            0L, 0L, 0L, Optional.empty(), Optional.empty()));
    }

    public synchronized AuctionOperationResult resume(ResumeAuctionCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = AuctionRequestFingerprints.resume(command);
        AuctionOperationResult replay = replay(
            command.requestId(), command.listingId(), AuctionOperationType.RESUME, fingerprint);
        if (replay != null) {
            return replay;
        }
        AuctionListing listing = listings.get(command.listingId());
        long effectiveNow = listing == null
            ? command.receivedAtMillis()
            : observeTime(listing.rules().timeBasis(), command.receivedAtMillis());
        if (listing == null) {
            return remember(fingerprint, missing(command.requestId(), command.listingId(), AuctionOperationType.RESUME));
        }
        if (command.expectedRevision() != listing.revision()) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.RESUME, AuctionOperationStatus.STALE_REVISION, listing));
        }
        if (listing.state() != AuctionListingState.FROZEN) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.RESUME, AuctionOperationStatus.NOT_FROZEN, listing));
        }
        effectiveNow = listing.clampTime(effectiveNow);
        long deadline = listing.deadlineMillis();
        if (listing.type().acceptsBids() && listing.rules().pauseWhileFrozen()) {
            try {
                deadline = Math.addExact(effectiveNow, listing.frozenRemainingMillis());
            } catch (ArithmeticException exception) {
                return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                    AuctionOperationType.RESUME, AuctionOperationStatus.INVALID_TRANSITION, listing));
            }
        }
        AuctionListing updated;
        try {
            updated = copy(
                listing,
                AuctionListingState.ACTIVE,
                Math.incrementExact(listing.revision()),
                deadline,
                effectiveNow,
                0L,
                listing.antiSnipeExtensionCount(),
                listing.antiSnipeCumulativeMillis(),
                listing.acceptedBidCount(),
                listing.highestBid(),
                Optional.empty(),
                Optional.empty()
            );
        } catch (ArithmeticException exception) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.RESUME, AuctionOperationStatus.INVALID_TRANSITION, listing));
        }
        listings.put(updated.listingId(), updated);
        return remember(fingerprint, applied(
            command.requestId(), updated, AuctionOperationType.RESUME,
            0L, 0L, 0L, Optional.empty(), Optional.empty()));
    }

    public synchronized AuctionOperationResult settle(SettleAuctionCommand command) {
        Objects.requireNonNull(command, "command");
        String fingerprint = AuctionRequestFingerprints.settle(command);
        AuctionOperationResult replay = replay(
            command.requestId(), command.listingId(), AuctionOperationType.SETTLE, fingerprint);
        if (replay != null) {
            return replay;
        }
        AuctionListing listing = listings.get(command.listingId());
        long effectiveNow = listing == null
            ? command.receivedAtMillis()
            : observeTime(listing.rules().timeBasis(), command.receivedAtMillis());
        if (listing == null) {
            return remember(fingerprint, missing(command.requestId(), command.listingId(), AuctionOperationType.SETTLE));
        }
        if (command.expectedRevision() != listing.revision()) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.SETTLE, AuctionOperationStatus.STALE_REVISION, listing));
        }
        if (listing.state() != AuctionListingState.SOLD_PENDING) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.SETTLE, AuctionOperationStatus.INVALID_TRANSITION, listing));
        }
        effectiveNow = listing.clampTime(effectiveNow);
        AuctionListing updated;
        try {
            updated = copy(
                listing,
                AuctionListingState.SETTLED,
                Math.incrementExact(listing.revision()),
                listing.deadlineMillis(),
                effectiveNow,
                0L,
                listing.antiSnipeExtensionCount(),
                listing.antiSnipeCumulativeMillis(),
                listing.acceptedBidCount(),
                listing.highestBid(),
                listing.sale(),
                listing.terminalTransactionId()
            );
        } catch (ArithmeticException exception) {
            return remember(fingerprint, rejected(command.requestId(), command.listingId(),
                AuctionOperationType.SETTLE, AuctionOperationStatus.INVALID_TRANSITION, listing));
        }
        listings.put(updated.listingId(), updated);
        return remember(fingerprint, applied(
            command.requestId(), updated, AuctionOperationType.SETTLE,
            0L, 0L, 0L, Optional.empty(), Optional.empty()));
    }

    public synchronized Optional<AuctionListing> find(UUID listingId) {
        return Optional.ofNullable(listings.get(Objects.requireNonNull(listingId, "listingId")));
    }

    public synchronized List<AuctionListing> listings() {
        List<AuctionListing> result = new ArrayList<>(listings.values());
        result.sort(Comparator.comparing(AuctionListing::listingId));
        return List.copyOf(result);
    }

    public synchronized long nextAcceptedSequence() {
        return nextAcceptedSequence;
    }

    public synchronized long lastObservedTimeMillis(AuctionTimeBasis basis) {
        return lastObservedTimeMillisByBasis.get(Objects.requireNonNull(basis, "basis"));
    }

    public synchronized Map<AuctionTimeBasis, Long> lastObservedTimeMillisByBasis() {
        return Map.copyOf(lastObservedTimeMillisByBasis);
    }

    public synchronized int requestReceiptCount() {
        return requests.size();
    }

    public synchronized AuctionHouseSnapshot snapshot() {
        return new AuctionHouseSnapshot(
            nextAcceptedSequence, lastObservedTimeMillisByBasis, listings, requests);
    }

    private AuctionOperationResult commonActiveRejection(
        UUID requestId,
        UUID listingId,
        AuctionOperationType operation,
        long expectedRevision,
        long receivedAtMillis,
        AuctionListing listing,
        boolean requireBids
    ) {
        if (listing == null) {
            return missing(requestId, listingId, operation);
        }
        if (expectedRevision != listing.revision()) {
            return rejected(requestId, listingId, operation, AuctionOperationStatus.STALE_REVISION, listing);
        }
        if (listing.state() != AuctionListingState.ACTIVE) {
            return rejected(requestId, listingId, operation, AuctionOperationStatus.NOT_ACTIVE, listing);
        }
        if (requireBids && !listing.type().acceptsBids()) {
            return rejected(requestId, listingId, operation, AuctionOperationStatus.BUYOUT_UNAVAILABLE, listing);
        }
        if (listing.type().acceptsBids() && listing.deadlineReached(receivedAtMillis)) {
            return rejected(requestId, listingId, operation, AuctionOperationStatus.DEADLINE_PASSED, listing);
        }
        return null;
    }

    private long antiSnipeExtension(AuctionListing listing, long effectiveNow) {
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

    private long observeTime(AuctionTimeBasis basis, long timeMillis) {
        Objects.requireNonNull(basis, "basis");
        if (timeMillis < 0L) {
            throw new IllegalArgumentException("Auction time must not be negative.");
        }
        long observed = Math.max(lastObservedTimeMillisByBasis.get(basis), timeMillis);
        lastObservedTimeMillisByBasis.put(basis, observed);
        return observed;
    }

    private AuctionOperationResult replay(
        UUID requestId,
        UUID listingId,
        AuctionOperationType operation,
        String fingerprint
    ) {
        AuctionRequestReceipt receipt = requests.get(requestId);
        if (receipt == null) {
            return requests.size() >= AuctionHouseSnapshot.MAX_REQUEST_RECEIPTS
                ? rejected(requestId, listingId, operation,
                    AuctionOperationStatus.CAPACITY_EXCEEDED, listings.get(listingId))
                : null;
        }
        if (receipt.fingerprint().equals(fingerprint)) {
            return receipt.result().asReplay();
        }
        AuctionListing current = listings.get(listingId);
        return rejected(requestId, listingId, operation, AuctionOperationStatus.REQUEST_CONFLICT, current);
    }

    private AuctionOperationResult remember(String fingerprint, AuctionOperationResult result) {
        if (!requests.containsKey(result.requestId())
            && requests.size() >= AuctionHouseSnapshot.MAX_REQUEST_RECEIPTS) {
            throw new IllegalStateException("Auction request receipt capacity is exhausted.");
        }
        requests.put(result.requestId(), new AuctionRequestReceipt(fingerprint, result));
        return result;
    }

    private static AuctionOperationResult missing(
        UUID requestId,
        UUID listingId,
        AuctionOperationType operation
    ) {
        return new AuctionOperationResult(
            requestId, listingId, operation, AuctionOperationStatus.NOT_FOUND, false,
            -1L, 0L, 0L, 0L, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static AuctionOperationResult rejected(
        UUID requestId,
        UUID listingId,
        AuctionOperationType operation,
        AuctionOperationStatus status,
        AuctionListing listing
    ) {
        return new AuctionOperationResult(
            requestId,
            listingId,
            operation,
            status,
            false,
            listing == null ? -1L : listing.revision(),
            0L,
            0L,
            0L,
            Optional.empty(),
            Optional.empty(),
            Optional.ofNullable(listing)
        );
    }

    private static AuctionOperationResult applied(
        UUID requestId,
        AuctionListing listing,
        AuctionOperationType operation,
        long requiredHoldDelta,
        long refund,
        long antiSnipeAdded,
        Optional<UUID> refundPlayer,
        Optional<AuctionBidStanding> displacedBid
    ) {
        return new AuctionOperationResult(
            requestId,
            listing.listingId(),
            operation,
            AuctionOperationStatus.APPLIED,
            false,
            listing.revision(),
            requiredHoldDelta,
            refund,
            antiSnipeAdded,
            refundPlayer,
            displacedBid,
            Optional.of(listing)
        );
    }

    private static AuctionListing copy(
        AuctionListing listing,
        AuctionListingState state,
        long revision,
        long deadlineMillis,
        long lastObservedTimeMillis,
        long frozenRemainingMillis,
        int antiSnipeExtensionCount,
        long antiSnipeCumulativeMillis,
        long acceptedBidCount,
        Optional<AuctionBidStanding> highestBid,
        Optional<AuctionSale> sale,
        Optional<UUID> terminalTransactionId
    ) {
        return new AuctionListing(
            listing.listingId(),
            listing.sellerId(),
            listing.activationTransactionId(),
            listing.itemLot(),
            listing.type(),
            state,
            revision,
            listing.startingBidMinor(),
            listing.buyoutMinor(),
            listing.rules(),
            listing.createdAtMillis(),
            deadlineMillis,
            listing.originalDurationMillis(),
            lastObservedTimeMillis,
            frozenRemainingMillis,
            antiSnipeExtensionCount,
            antiSnipeCumulativeMillis,
            acceptedBidCount,
            highestBid,
            sale,
            terminalTransactionId
        );
    }

    private static boolean allDistinct(UUID... identities) {
        Set<UUID> unique = new HashSet<>();
        for (UUID identity : identities) {
            if (!unique.add(Objects.requireNonNull(identity, "identity"))) {
                return false;
            }
        }
        return true;
    }
}
