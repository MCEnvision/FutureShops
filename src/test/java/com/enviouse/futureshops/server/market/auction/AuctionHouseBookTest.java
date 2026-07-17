package com.enviouse.futureshops.server.market.auction;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionHouseBookTest {
    @Test
    void creationReplaysExactlyWithoutRepeatingCommitSideEffects() {
        AuctionHouseBook book = new AuctionHouseBook();
        CreateAuctionCommand command = timedCommand(id(1), id(2), id(3), 1000L, 2000L, rules(true));

        AuctionOperationResult first = book.create(command);
        AuctionOperationResult replay = book.create(command);
        CreateAuctionCommand conflict = timedCommand(
            command.requestId(), id(4), id(3), 1000L, 2000L, rules(true));
        CreateAuctionCommand laterRetry = timedCommand(
            command.requestId(), command.listingId(), command.sellerId(), 5000L, 6000L, rules(true));

        assertTrue(first.applied());
        assertTrue(first.newlyCommitted());
        assertTrue(first.durablyApplied());
        assertFalse(first.replayed());
        assertFalse(replay.applied());
        assertFalse(replay.newlyCommitted());
        assertTrue(replay.durablyApplied());
        assertTrue(replay.replayed());
        assertEquals(first.listing(), replay.listing());
        assertEquals(first.listing(), book.create(laterRetry).listing());
        assertTrue(book.create(laterRetry).replayed());
        assertEquals(AuctionOperationStatus.REQUEST_CONFLICT, book.create(conflict).status());
        assertEquals(1, book.requestReceiptCount());
    }

    @Test
    void bidsHoldExactDeltaRefundPreviousLeaderAndExtendDeadline() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionListing created = createTimed(book);
        PlaceAuctionBidCommand firstCommand = bid(
            id(10), created, 0L, id(11), id(12), id(14), id(13), 100L, 100L, 1950L);

        AuctionOperationResult first = book.bid(firstCommand);
        AuctionListing afterFirst = first.listing().orElseThrow();
        AuctionOperationResult second = book.bid(bid(
            id(20), afterFirst, 1L, id(21), id(22), id(24), id(23), 200L, 200L, 2010L));
        AuctionListing afterSecond = second.listing().orElseThrow();

        assertEquals(60L, first.antiSnipeAddedMillis());
        assertEquals(2060L, afterFirst.deadlineMillis());
        assertEquals(100L, second.refundMinor());
        assertEquals(id(12), second.refundPlayerId().orElseThrow());
        assertEquals(60L, second.antiSnipeAddedMillis());
        assertEquals(2120L, afterSecond.deadlineMillis());
        assertEquals(2, afterSecond.antiSnipeExtensionCount());
        assertEquals(2L, book.nextAcceptedSequence());
    }

    @Test
    void leaderRaisesThroughStableConstantSizeHoldAccount() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionListing created = createTimed(book);
        UUID bidder = id(12);
        UUID account = id(14);
        AuctionListing first = book.bid(bid(
            id(10), created, 0L, id(11), bidder, account, id(13), 100L, 100L, 1200L))
            .listing().orElseThrow();

        AuctionOperationResult equal = book.bid(bid(
            id(20), first, 1L, id(21), id(22), id(24), id(23), 100L, 100L, 1300L));
        AuctionOperationResult wrongAccount = book.bid(bid(
            id(25), first, 1L, id(26), bidder, id(27), id(28), 150L, 50L, 1400L));
        AuctionOperationResult raised = book.bid(bid(
            id(30), first, 1L, id(31), bidder, account, id(33), 150L, 50L, 1400L));

        assertEquals(AuctionOperationStatus.BID_TOO_LOW, equal.status());
        assertEquals(AuctionOperationStatus.IDENTITY_CONFLICT, wrongAccount.status());
        assertTrue(raised.applied());
        assertEquals(50L, raised.requiredHoldDeltaMinor());
        assertEquals(0L, raised.refundMinor());
        AuctionBidStanding standing = raised.listing().orElseThrow().highestBid().orElseThrow();
        assertEquals(bidder, standing.bidderId());
        assertEquals(account, standing.holdAccountId());
        assertEquals(id(33), standing.holdTransactionId());
    }

    @Test
    void leaderCannotSwitchBackToAPreviouslyRetiredHoldAccount() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionListing listing = createTimed(book);
        UUID firstBidder = id(12);
        AuctionListing first = book.bid(bid(
            id(10), listing, 0L, id(11), firstBidder, id(14), id(13), 100L, 100L, 1200L))
            .listing().orElseThrow();
        AuctionListing second = book.bid(bid(
            id(20), first, 1L, id(21), id(22), id(24), id(23), 200L, 200L, 1300L))
            .listing().orElseThrow();
        AuctionListing third = book.bid(bid(
            id(30), second, 2L, id(31), firstBidder, id(34), id(33), 300L, 300L, 1400L))
            .listing().orElseThrow();

        AuctionOperationResult switched = book.bid(bid(
            id(40), third, 3L, id(41), firstBidder, id(14), id(43), 400L, 100L, 1500L));

        assertEquals(AuctionOperationStatus.IDENTITY_CONFLICT, switched.status());
        assertEquals(id(34), book.find(listing.listingId()).orElseThrow()
            .highestBid().orElseThrow().holdAccountId());
    }

    @Test
    void staleRevisionWrongHoldSellerBidAndDeadlineFailClosed() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionListing listing = createTimed(book);

        assertEquals(AuctionOperationStatus.STALE_REVISION, book.bid(bid(
            id(10), listing, 9L, id(11), id(12), id(14), id(13), 100L, 100L, 1200L)).status());
        assertEquals(AuctionOperationStatus.HOLD_MISMATCH, book.bid(bid(
            id(20), listing, 0L, id(21), id(22), id(24), id(23), 100L, 99L, 1200L)).status());
        assertEquals(AuctionOperationStatus.SELLER_SELF_ACTION, book.bid(bid(
            id(30), listing, 0L, id(31), listing.sellerId(), id(34), id(33), 100L, 100L, 1200L)).status());
        assertEquals(AuctionOperationStatus.DEADLINE_PASSED, book.bid(bid(
            id(40), listing, 0L, id(41), id(42), id(44), id(43), 100L, 100L, 2000L)).status());
        assertTrue(book.find(listing.listingId()).orElseThrow().highestBid().isEmpty());
    }

    @Test
    void buyoutUsesExistingLeadingAccountAndRefundsOnlyOtherBidder() {
        AuctionHouseBook ownBidBook = new AuctionHouseBook();
        AuctionListing ownListing = createTimed(ownBidBook);
        UUID leader = id(12);
        UUID leaderAccount = id(14);
        AuctionListing ownBid = ownBidBook.bid(bid(
            id(10), ownListing, 0L, id(11), leader, leaderAccount, id(13), 100L, 100L, 1200L))
            .listing().orElseThrow();
        AuctionOperationResult ownBuyout = ownBidBook.buyNow(new AuctionBuyNowCommand(
            id(20), ownListing.listingId(), ownBid.revision(), leader,
            leaderAccount, id(22), id(21), 900L, 1300L));

        AuctionHouseBook otherBook = new AuctionHouseBook();
        AuctionListing otherListing = createTimed(otherBook);
        AuctionListing otherBid = otherBook.bid(bid(
            id(30), otherListing, 0L, id(31), leader, id(34), id(33), 100L, 100L, 1200L))
            .listing().orElseThrow();
        AuctionOperationResult otherBuyout = otherBook.buyNow(new AuctionBuyNowCommand(
            id(40), otherListing.listingId(), otherBid.revision(), id(42),
            id(44), id(45), id(43), 1000L, 1300L));

        assertTrue(ownBuyout.applied());
        assertEquals(900L, ownBuyout.requiredHoldDeltaMinor());
        assertEquals(0L, ownBuyout.refundMinor());
        assertEquals(leaderAccount,
            ownBuyout.listing().orElseThrow().sale().orElseThrow().holdAccountId());
        assertEquals(AuctionListingState.SOLD_PENDING, ownBuyout.listing().orElseThrow().state());
        assertEquals(100L, otherBuyout.refundMinor());
        assertEquals(leader, otherBuyout.refundPlayerId().orElseThrow());
    }

    @Test
    void sellerCanCancelOnlyBeforeTheFirstBidWhenEnabled() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionListing listing = createTimed(book);
        AuctionOperationResult cancelled = book.cancel(new CancelAuctionCommand(
            id(10), listing.listingId(), 0L, listing.sellerId(), id(11), 1200L));

        AuctionHouseBook withBid = new AuctionHouseBook();
        AuctionListing bidListing = createTimed(withBid);
        AuctionListing afterBid = withBid.bid(bid(
            id(20), bidListing, 0L, id(21), id(22), id(24), id(23), 100L, 100L, 1200L))
            .listing().orElseThrow();
        AuctionOperationResult denied = withBid.cancel(new CancelAuctionCommand(
            id(30), bidListing.listingId(), afterBid.revision(), bidListing.sellerId(), id(31), 1300L));

        AuctionHouseBook disabled = new AuctionHouseBook();
        AuctionListing disabledListing = disabled.create(timedCommand(
            id(40), id(41), id(42), 1000L, 2000L, rules(false))).listing().orElseThrow();
        AuctionOperationResult disabledResult = disabled.cancel(new CancelAuctionCommand(
            id(43), disabledListing.listingId(), 0L, disabledListing.sellerId(), id(44), 1200L));

        assertEquals(AuctionListingState.CANCELLED, cancelled.listing().orElseThrow().state());
        assertEquals(AuctionOperationStatus.CANCELLATION_DENIED, denied.status());
        assertEquals(AuctionOperationStatus.CANCELLATION_DENIED, disabledResult.status());
    }

    @Test
    void expirationAllocatesWinningSaleAndSettlementIsIdempotent() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionListing listing = createTimed(book);
        AuctionListing afterBid = book.bid(bid(
            id(10), listing, 0L, id(11), id(12), id(14), id(13), 100L, 100L, 1200L))
            .listing().orElseThrow();
        ExpireAuctionCommand expire = new ExpireAuctionCommand(
            id(20), listing.listingId(), afterBid.revision(), id(21), afterBid.deadlineMillis());

        AuctionListing sold = book.expire(expire).listing().orElseThrow();
        AuctionSale sale = sold.sale().orElseThrow();
        SettleAuctionCommand settle = new SettleAuctionCommand(
            id(30), sold.listingId(), sold.revision(), sold.lastObservedTimeMillis());
        AuctionOperationResult settled = book.settle(settle);

        assertEquals(AuctionListingState.SOLD_PENDING, sold.state());
        assertEquals(id(12), sale.buyerId());
        assertEquals(id(14), sale.holdAccountId());
        assertEquals(id(13), sale.holdTransactionId());
        assertEquals(AuctionListingState.SETTLED, settled.listing().orElseThrow().state());
        assertFalse(book.settle(settle).applied());
        assertTrue(book.settle(settle).durablyApplied());
    }

    @Test
    void freezePreservesRemainingTimeAndResumeClampsBackwardClock() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionListing listing = createTimed(book);
        AuctionListing frozen = book.freeze(new FreezeAuctionCommand(
            id(10), listing.listingId(), 0L, 1500L)).listing().orElseThrow();
        AuctionListing resumed = book.resume(new ResumeAuctionCommand(
            id(20), listing.listingId(), 1L, 5000L)).listing().orElseThrow();

        assertEquals(500L, frozen.frozenRemainingMillis());
        assertEquals(5500L, resumed.deadlineMillis());
        assertEquals(5000L, resumed.lastObservedTimeMillis());
        assertEquals(AuctionOperationStatus.DEADLINE_PASSED, book.freeze(new FreezeAuctionCommand(
            id(30), listing.listingId(), 2L, 5500L)).status());
    }

    @Test
    void rejectedFutureObservationPreventsBackwardReopeningWithinItsTimeBasis() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionListing listing = createTimed(book);

        AuctionOperationResult future = book.bid(bid(
            id(10), listing, 0L, id(11), id(12), id(14), id(13), 100L, 100L, 2500L));
        AuctionOperationResult backward = book.bid(bid(
            id(20), listing, 0L, id(21), id(22), id(24), id(23), 100L, 100L, 1500L));

        assertEquals(AuctionOperationStatus.DEADLINE_PASSED, future.status());
        assertEquals(AuctionOperationStatus.DEADLINE_PASSED, backward.status());
        assertEquals(2500L, book.lastObservedTimeMillis(AuctionTimeBasis.REAL_TIME));
        assertEquals(0L, book.lastObservedTimeMillis(AuctionTimeBasis.ONLINE_TIME));
    }

    @Test
    void realAndOnlineTimeClocksAreIndependentAndPersisted() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionListing real = book.create(timedCommand(
            id(1), id(2), id(3), 1000L, 2000L, rules(true, AuctionTimeBasis.REAL_TIME)))
            .listing().orElseThrow();
        AuctionListing online = book.create(timedCommand(
            id(4), id(5), id(6), 100L, 200L, rules(true, AuctionTimeBasis.ONLINE_TIME)))
            .listing().orElseThrow();

        AuctionHouseBook restored = new AuctionHouseBook(book.snapshot());

        assertEquals(1000L, real.createdAtMillis());
        assertEquals(100L, online.createdAtMillis());
        assertEquals(1000L, restored.lastObservedTimeMillis(AuctionTimeBasis.REAL_TIME));
        assertEquals(100L, restored.lastObservedTimeMillis(AuctionTimeBasis.ONLINE_TIME));
    }

    @Test
    void retryObservationTimeIsOutsideRequestIdentity() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionListing listing = createTimed(book);
        PlaceAuctionBidCommand first = bid(
            id(10), listing, 0L, id(11), id(12), id(14), id(13), 100L, 100L, 1200L);
        PlaceAuctionBidCommand laterRetry = new PlaceAuctionBidCommand(
            first.requestId(), first.listingId(), first.expectedRevision(), first.bidId(),
            first.bidderId(), first.holdAccountId(), first.holdTransactionId(),
            first.amountMinor(), first.heldDeltaMinor(), 1900L);

        AuctionOperationResult accepted = book.bid(first);
        AuctionOperationResult replay = book.bid(laterRetry);

        assertTrue(accepted.applied());
        assertTrue(replay.replayed());
        assertFalse(replay.applied());
        assertEquals(accepted.listing(), replay.listing());
    }

    @Test
    void snapshotRestorePreservesCanonicalStateClocksIdentitiesAndReceipts() {
        AuctionHouseBook book = new AuctionHouseBook();
        CreateAuctionCommand create = timedCommand(
            id(1), id(2), id(3), 1000L, 2000L, rules(true));
        AuctionListing listing = book.create(create).listing().orElseThrow();
        PlaceAuctionBidCommand bid = bid(
            id(10), listing, 0L, id(11), id(12), id(14), id(13), 100L, 100L, 1950L);
        AuctionOperationResult accepted = book.bid(bid);

        AuctionHouseBook restored = new AuctionHouseBook(book.snapshot());
        assertEquals(book.listings(), restored.listings());
        assertEquals(book.nextAcceptedSequence(), restored.nextAcceptedSequence());
        assertEquals(book.lastObservedTimeMillisByBasis(), restored.lastObservedTimeMillisByBasis());
        assertEquals(accepted.listing(), restored.bid(bid).listing());
        assertTrue(restored.bid(bid).replayed());
        AuctionOperationResult reusedIdentity = restored.bid(bid(
            id(20), accepted.listing().orElseThrow(), 1L, id(11), id(22),
            id(24), id(23), 200L, 200L, 1960L));

        assertEquals(AuctionOperationStatus.IDENTITY_CONFLICT, reusedIdentity.status());
    }

    @Test
    void snapshotRejectsCountersBehindCanonicalEvidence() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionListing listing = createTimed(book);
        book.bid(bid(
            id(10), listing, 0L, id(11), id(12), id(14), id(13), 100L, 100L, 1200L));
        AuctionHouseSnapshot snapshot = book.snapshot();

        assertThrows(IllegalArgumentException.class, () -> new AuctionHouseSnapshot(
            0L,
            snapshot.lastObservedTimeMillisByBasis(),
            snapshot.listings(),
            snapshot.requestReceipts()));
        Map<AuctionTimeBasis, Long> behind = new HashMap<>(snapshot.lastObservedTimeMillisByBasis());
        behind.put(AuctionTimeBasis.REAL_TIME, 0L);
        assertThrows(IllegalArgumentException.class, () -> new AuctionHouseSnapshot(
            snapshot.nextAcceptedSequence(),
            behind,
            snapshot.listings(),
            snapshot.requestReceipts()));
    }

    @Test
    void snapshotRejectsMissingOrImpossibleLaterAppliedRevisions() {
        AuctionHouseBook book = new AuctionHouseBook();
        CreateAuctionCommand create = timedCommand(
            id(1), id(2), id(3), 1000L, 2000L, rules(true));
        AuctionListing active = book.create(create).listing().orElseThrow();
        book.cancel(new CancelAuctionCommand(
            id(10), active.listingId(), 0L, active.sellerId(), id(11), 1200L));
        AuctionHouseSnapshot cancelled = book.snapshot();

        assertThrows(IllegalArgumentException.class, () -> new AuctionHouseSnapshot(
            cancelled.nextAcceptedSequence(),
            cancelled.lastObservedTimeMillisByBasis(),
            Map.of(active.listingId(), active),
            cancelled.requestReceipts()));

        AuctionHouseBook bidBook = new AuctionHouseBook();
        AuctionListing listing = createTimed(bidBook);
        PlaceAuctionBidCommand bid = bid(
            id(20), listing, 0L, id(21), id(22), id(24), id(23), 100L, 100L, 1200L);
        bidBook.bid(bid);
        AuctionHouseSnapshot withBid = bidBook.snapshot();
        Map<UUID, AuctionRequestReceipt> missingBid = new HashMap<>(withBid.requestReceipts());
        missingBid.remove(bid.requestId());
        assertThrows(IllegalArgumentException.class, () -> new AuctionHouseSnapshot(
            withBid.nextAcceptedSequence(),
            withBid.lastObservedTimeMillisByBasis(),
            withBid.listings(),
            missingBid));
    }

    @Test
    void snapshotRejectsPreextendedCreationEvidence() {
        AuctionHouseBook book = new AuctionHouseBook();
        CreateAuctionCommand command = timedCommand(
            id(1), id(2), id(3), 1000L, 2000L, rules(true));
        AuctionListing listing = book.create(command).listing().orElseThrow();
        AuctionHouseSnapshot valid = book.snapshot();
        AuctionListing drifted = new AuctionListing(
            listing.listingId(), listing.sellerId(), listing.activationTransactionId(),
            listing.itemLot(), listing.type(), listing.state(), listing.revision(),
            listing.startingBidMinor(), listing.buyoutMinor(), listing.rules(),
            listing.createdAtMillis(), 2060L, listing.originalDurationMillis(),
            listing.lastObservedTimeMillis(), 0L, 1, 60L, 0L,
            Optional.empty(), Optional.empty(), Optional.empty());
        AuctionOperationResult result = new AuctionOperationResult(
            command.requestId(), command.listingId(), AuctionOperationType.CREATE,
            AuctionOperationStatus.APPLIED, false, 0L, 0L, 0L, 0L,
            Optional.empty(), Optional.empty(), Optional.of(drifted));
        AuctionRequestReceipt original = valid.requestReceipts().get(command.requestId());

        assertThrows(IllegalArgumentException.class, () -> new AuctionHouseSnapshot(
            0L,
            valid.lastObservedTimeMillisByBasis(),
            Map.of(drifted.listingId(), drifted),
            Map.of(command.requestId(), new AuctionRequestReceipt(
                original.fingerprint(), result))));
    }

    @Test
    void runtimeRejectsCrossListingIdentityReuse() {
        AuctionHouseBook book = new AuctionHouseBook();
        AuctionListing first = createTimed(book);
        CreateAuctionCommand activationConflict = new CreateAuctionCommand(
            id(10), id(11), id(12), first.activationTransactionId(),
            lot(id(2011)), AuctionListingType.AUCTION_WITH_BUYOUT,
            100L, 1000L, rules(true), 1000L, 2000L);
        CreateAuctionCommand custodyConflict = new CreateAuctionCommand(
            id(20), id(21), id(22), id(1021),
            lot(first.itemLot().custodyLotId()), AuctionListingType.AUCTION_WITH_BUYOUT,
            100L, 1000L, rules(true), 1000L, 2000L);

        assertEquals(AuctionOperationStatus.IDENTITY_CONFLICT, book.create(activationConflict).status());
        assertEquals(AuctionOperationStatus.IDENTITY_CONFLICT, book.create(custodyConflict).status());

        AuctionListing second = book.create(timedCommand(
            id(30), id(31), id(32), 1000L, 2000L, rules(true))).listing().orElseThrow();
        AuctionListing firstBid = book.bid(bid(
            id(40), first, 0L, id(41), id(42), id(44), id(43), 100L, 100L, 1200L))
            .listing().orElseThrow();

        assertEquals(AuctionOperationStatus.IDENTITY_CONFLICT, book.bid(bid(
            id(50), second, 0L, id(41), id(52), id(54), id(53), 100L, 100L, 1200L)).status());
        assertEquals(AuctionOperationStatus.IDENTITY_CONFLICT, book.bid(bid(
            id(60), second, 0L, id(61), id(62), id(64), id(43), 100L, 100L, 1200L)).status());
        assertEquals(AuctionOperationStatus.IDENTITY_CONFLICT, book.bid(bid(
            id(70), second, 0L, id(71), id(72), id(44), id(73), 100L, 100L, 1200L)).status());

        AuctionHouseBook terminalBook = new AuctionHouseBook();
        AuctionListing terminalFirst = terminalBook.create(timedCommand(
            id(80), id(81), id(82), 1000L, 2000L, rules(true))).listing().orElseThrow();
        AuctionListing terminalSecond = terminalBook.create(timedCommand(
            id(83), id(84), id(85), 1000L, 2000L, rules(true))).listing().orElseThrow();
        terminalBook.cancel(new CancelAuctionCommand(
            id(86), terminalFirst.listingId(), 0L, terminalFirst.sellerId(), id(87), 1200L));
        assertEquals(AuctionOperationStatus.IDENTITY_CONFLICT, terminalBook.cancel(new CancelAuctionCommand(
            id(88), terminalSecond.listingId(), 0L, terminalSecond.sellerId(), id(87), 1200L)).status());
        assertEquals(1L, firstBid.revision());
    }

    @Test
    void snapshotRejectsCrossListingIdentityOwnershipConflicts() {
        AuctionHouseBook left = new AuctionHouseBook();
        AuctionListing first = left.create(timedCommand(
            id(1), id(2), id(3), 1000L, 2000L, rules(true))).listing().orElseThrow();

        AuctionHouseBook right = new AuctionHouseBook();
        CreateAuctionCommand colliding = new CreateAuctionCommand(
            id(4), id(5), id(6), first.activationTransactionId(), lot(id(2005)),
            AuctionListingType.AUCTION_WITH_BUYOUT, 100L, 1000L, rules(true), 1000L, 2000L);
        right.create(colliding);

        Map<UUID, AuctionListing> listings = new HashMap<>(left.snapshot().listings());
        listings.putAll(right.snapshot().listings());
        Map<UUID, AuctionRequestReceipt> receipts = new HashMap<>(left.snapshot().requestReceipts());
        receipts.putAll(right.snapshot().requestReceipts());

        assertThrows(IllegalArgumentException.class, () -> new AuctionHouseSnapshot(
            0L,
            Map.of(AuctionTimeBasis.REAL_TIME, 1000L, AuctionTimeBasis.ONLINE_TIME, 0L),
            listings,
            receipts));
    }

    @Test
    void createAndFreezeArithmeticOverflowFailClosed() throws ReflectiveOperationException {
        AuctionHouseBook createBook = new AuctionHouseBook();
        CreateAuctionCommand permanent = new CreateAuctionCommand(
            id(1), id(2), id(3), id(1002), lot(id(2002)), AuctionListingType.BUY_NOW,
            0L, 1000L, rules(true), Long.MAX_VALUE, 0L);
        assertTrue(createBook.create(permanent).applied());
        AuctionOperationResult overflow = createBook.create(timedCommand(
            id(4), id(5), id(6), 0L, 1L, rules(true)));
        assertEquals(AuctionOperationStatus.INVALID_TRANSITION, overflow.status());
        assertTrue(createBook.find(id(5)).isEmpty());

        AuctionHouseBook freezeBook = new AuctionHouseBook();
        AuctionListing listing = createTimed(freezeBook);
        AuctionListing maximumRevision = withRevision(listing, Long.MAX_VALUE);
        Field field = AuctionHouseBook.class.getDeclaredField("listings");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, AuctionListing> mutableListings =
            (Map<UUID, AuctionListing>) field.get(freezeBook);
        mutableListings.put(listing.listingId(), maximumRevision);

        AuctionOperationResult freeze = freezeBook.freeze(new FreezeAuctionCommand(
            id(10), listing.listingId(), Long.MAX_VALUE, 1500L));
        assertEquals(AuctionOperationStatus.INVALID_TRANSITION, freeze.status());
        assertEquals(Long.MAX_VALUE,
            freezeBook.find(listing.listingId()).orElseThrow().revision());
    }

    @Test
    void resultContractsRejectContradictoryEvidence() {
        assertThrows(IllegalArgumentException.class, () -> new AuctionOperationResult(
            id(1), id(2), AuctionOperationType.BID, AuctionOperationStatus.BID_TOO_LOW,
            false, 0L, 1L, 0L, 0L,
            Optional.empty(), Optional.empty(), Optional.empty()));
    }

    private static AuctionListing createTimed(AuctionHouseBook book) {
        return book.create(timedCommand(id(1), id(2), id(3), 1000L, 2000L, rules(true)))
            .listing().orElseThrow();
    }

    private static PlaceAuctionBidCommand bid(
        UUID requestId,
        AuctionListing listing,
        long revision,
        UUID bidId,
        UUID bidderId,
        UUID accountId,
        UUID transactionId,
        long amount,
        long delta,
        long receivedAt
    ) {
        return new PlaceAuctionBidCommand(
            requestId, listing.listingId(), revision, bidId, bidderId,
            accountId, transactionId, amount, delta, receivedAt);
    }

    private static CreateAuctionCommand timedCommand(
        UUID requestId,
        UUID listingId,
        UUID sellerId,
        long created,
        long deadline,
        AuctionRuleSnapshot rules
    ) {
        long suffix = listingId.getLeastSignificantBits();
        return new CreateAuctionCommand(
            requestId,
            listingId,
            sellerId,
            id(Math.addExact(1000L, suffix)),
            lot(id(Math.addExact(2000L, suffix))),
            AuctionListingType.AUCTION_WITH_BUYOUT,
            100L,
            1000L,
            rules,
            created,
            deadline
        );
    }

    private static AuctionItemLot lot(UUID custodyId) {
        return new AuctionItemLot(
            custodyId, "minecraft:diamond", "a".repeat(64),
            1, 32, "materials", "diamond minecraft");
    }

    private static AuctionRuleSnapshot rules(boolean cancelBeforeBid) {
        return rules(cancelBeforeBid, AuctionTimeBasis.REAL_TIME);
    }

    private static AuctionRuleSnapshot rules(
        boolean cancelBeforeBid,
        AuctionTimeBasis basis
    ) {
        return new AuctionRuleSnapshot(
            10L,
            250,
            10L,
            0,
            true,
            60L,
            60L,
            120L,
            2,
            cancelBeforeBid,
            basis,
            true,
            7L
        );
    }

    private static AuctionListing withRevision(AuctionListing listing, long revision) {
        return new AuctionListing(
            listing.listingId(), listing.sellerId(), listing.activationTransactionId(),
            listing.itemLot(), listing.type(), listing.state(), revision,
            listing.startingBidMinor(), listing.buyoutMinor(), listing.rules(),
            listing.createdAtMillis(), listing.deadlineMillis(), listing.originalDurationMillis(),
            listing.lastObservedTimeMillis(), listing.frozenRemainingMillis(),
            listing.antiSnipeExtensionCount(), listing.antiSnipeCumulativeMillis(),
            listing.acceptedBidCount(), listing.highestBid(), listing.sale(),
            listing.terminalTransactionId());
    }

    private static UUID id(long value) {
        return new UUID(1L, value);
    }
}
