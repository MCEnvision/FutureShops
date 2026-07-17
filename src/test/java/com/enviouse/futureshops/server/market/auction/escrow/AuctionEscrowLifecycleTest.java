package com.enviouse.futureshops.server.market.auction.escrow;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.market.auction.AuctionBuyNowCommand;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionListing;
import com.enviouse.futureshops.server.market.auction.AuctionListingState;
import com.enviouse.futureshops.server.market.auction.CancelAuctionCommand;
import com.enviouse.futureshops.server.market.auction.ExpireAuctionCommand;
import com.enviouse.futureshops.server.market.auction.FreezeAuctionCommand;
import com.enviouse.futureshops.server.market.auction.PlaceAuctionBidCommand;
import com.enviouse.futureshops.server.market.auction.ResumeAuctionCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionEscrowLifecycleTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void createRequiresPreparedIntentAndRoundTripsCanonicalEvidence() {
        AuctionCreateEscrowIntent intent =
                AuctionEscrowTestFixtures.preparedCreate();
        AuctionEscrowCommit commit =
                AuctionEscrowTestFixtures.createCommit();
        AuctionEscrowLifecycleEvent.Prepare prepare =
                new AuctionEscrowLifecycleEvent.Prepare(intent);
        AuctionEscrowLifecycleEvent.Commit complete =
                new AuctionEscrowLifecycleEvent.Commit(
                        Optional.of(intent.complete()), commit);

        assertEquals(intent, AuctionCreateEscrowIntentCodec.decode(
                AuctionCreateEscrowIntentCodec.encode(intent)));
        assertEquals(commit, AuctionEscrowCommitCodec.decode(
                AuctionEscrowCommitCodec.encode(commit)));
        assertEquals(prepare, AuctionEscrowLifecycleEventCodec.decode(
                AuctionEscrowLifecycleEventCodec.encode(prepare)));
        assertEquals(complete, AuctionEscrowLifecycleEventCodec.decode(
                AuctionEscrowLifecycleEventCodec.encode(complete)));

        assertThrows(AuctionEscrowLifecycleConflictException.class,
                () -> AuctionEscrowLifecycleRepository.apply(
                        AuctionHouseSnapshot.empty(),
                        AuctionEscrowLifecycleState.empty(), complete));
        var prepared = AuctionEscrowLifecycleRepository.apply(
                AuctionHouseSnapshot.empty(),
                AuctionEscrowLifecycleState.empty(), prepare);
        var applied = AuctionEscrowLifecycleRepository.apply(
                prepared.auctionHouse(), prepared.lifecycle(), complete);
        assertEquals(AuctionListingState.ACTIVE,
                applied.auctionHouse().listings().get(
                        intent.listingId()).state());
        assertFalse(applied.replayed());
        assertTrue(AuctionEscrowLifecycleRepository.apply(
                applied.auctionHouse(), applied.lifecycle(), complete)
                .replayed());
        assertEquals(applied.lifecycle(),
                AuctionEscrowLifecycleStateCodec.decode(
                        AuctionEscrowLifecycleStateCodec.encode(
                                applied.lifecycle())));
    }

    @Test
    void bidDisplacesPriorHoldIntoDurableRefundClaim() {
        Applied created = created();
        AuctionListing listing = created.snapshot().listings().get(
                AuctionEscrowTestFixtures.id(2));
        UUID firstBidder = AuctionEscrowTestFixtures.id(20);
        PlaceAuctionBidCommand firstCommand = new PlaceAuctionBidCommand(
                AuctionEscrowTestFixtures.id(21), listing.listingId(),
                listing.revision(), AuctionEscrowTestFixtures.id(22),
                firstBidder, AuctionEscrowTestFixtures.id(23),
                AuctionEscrowTestFixtures.id(24), 100L, 100L, 1200L);
        AuctionEscrowCommit first = AuctionEscrowLifecyclePlanner.bid(
                created.snapshot(), firstCommand,
                AuctionEscrowTestFixtures.wallet(firstBidder, 1000L,
                        100_000L), AuctionEscrowTestFixtures.CURRENCY_ID,
                AuctionEscrowTestFixtures.NOW);
        var afterFirst = AuctionEscrowLifecycleRepository.apply(
                created.snapshot(), created.lifecycle(),
                new AuctionEscrowLifecycleEvent.Commit(Optional.empty(),
                        first));
        AuctionListing leading = afterFirst.auctionHouse().listings().get(
                listing.listingId());
        UUID secondBidder = AuctionEscrowTestFixtures.id(30);
        AuctionEscrowCommit second = AuctionEscrowLifecyclePlanner.bid(
                afterFirst.auctionHouse(), new PlaceAuctionBidCommand(
                        AuctionEscrowTestFixtures.id(31),
                        listing.listingId(), leading.revision(),
                        AuctionEscrowTestFixtures.id(32), secondBidder,
                        AuctionEscrowTestFixtures.id(33),
                        AuctionEscrowTestFixtures.id(34),
                        200L, 200L, 1300L),
                AuctionEscrowTestFixtures.wallet(secondBidder, 1000L,
                        100_000L), AuctionEscrowTestFixtures.CURRENCY_ID,
                AuctionEscrowTestFixtures.NOW.plusSeconds(1));

        assertEquals(1, second.claims().size());
        assertEquals(ClaimKind.REFUND, second.claims().get(0).kind());
        assertEquals(firstBidder, second.claims().get(0).ownerId());
        assertTrue(second.ledgerTransaction().orElseThrow().legs().stream()
                .anyMatch(leg -> leg.account().type()
                == LedgerAccountType.PLAYER_CLAIM
                && leg.deltaMinor() == 100L));
    }

    @Test
    void buyNowAlwaysCreatesFullSellerProceedsClaim() {
        Applied created = created();
        AuctionListing listing = created.snapshot().listings().get(
                AuctionEscrowTestFixtures.id(2));
        UUID bidder = AuctionEscrowTestFixtures.id(40);
        AuctionEscrowCommit bid = AuctionEscrowLifecyclePlanner.bid(
                created.snapshot(), new PlaceAuctionBidCommand(
                        AuctionEscrowTestFixtures.id(41),
                        listing.listingId(), listing.revision(),
                        AuctionEscrowTestFixtures.id(42), bidder,
                        AuctionEscrowTestFixtures.id(43),
                        AuctionEscrowTestFixtures.id(44),
                        100L, 100L, 1200L),
                AuctionEscrowTestFixtures.wallet(bidder, 1000L,
                        100_000L), AuctionEscrowTestFixtures.CURRENCY_ID,
                AuctionEscrowTestFixtures.NOW);
        var afterBid = AuctionEscrowLifecycleRepository.apply(
                created.snapshot(), created.lifecycle(),
                new AuctionEscrowLifecycleEvent.Commit(Optional.empty(),
                        bid));
        AuctionListing leading = afterBid.auctionHouse().listings().get(
                listing.listingId());
        UUID buyer = AuctionEscrowTestFixtures.id(50);
        AuctionBuyNowCommand command = new AuctionBuyNowCommand(
                AuctionEscrowTestFixtures.id(51),
                listing.listingId(), leading.revision(), buyer,
                AuctionEscrowTestFixtures.id(52),
                AuctionEscrowTestFixtures.id(53),
                AuctionEscrowTestFixtures.id(54), 1000L, 1300L);
        AuctionEscrowWalletSnapshot buyerWallet =
                AuctionEscrowTestFixtures.wallet(buyer, 2000L,
                        100_000L);
        AuctionEscrowCommit buy = AuctionEscrowLifecyclePlanner.buyNow(
                afterBid.auctionHouse(), command, buyerWallet,
                AuctionEscrowTestFixtures.wallet(listing.sellerId(), 0L,
                        50L),
                AuctionEscrowTestFixtures.preparedCreate()
                        .plannedCustody(),
                AuctionEscrowTestFixtures.CURRENCY_ID,
                AuctionEscrowTestFixtures.NOW.plusSeconds(2));
        AuctionEscrowCommit differentSellerCapacity =
                AuctionEscrowLifecyclePlanner.buyNow(
                        afterBid.auctionHouse(), command, buyerWallet,
                        new AuctionEscrowWalletSnapshot(
                                listing.sellerId(), 0L, -9000L,
                                0L, 0L, 99L),
                        AuctionEscrowTestFixtures.preparedCreate()
                                .plannedCustody(),
                        AuctionEscrowTestFixtures.CURRENCY_ID,
                        AuctionEscrowTestFixtures.NOW.plusSeconds(2));

        assertEquals(2, buy.bookMutations().size());
        assertEquals(AuctionListingState.SETTLED,
                buy.finalListing().state());
        assertEquals(buy, differentSellerCapacity);
        assertEquals(buy.fingerprint(),
                differentSellerCapacity.fingerprint());
        assertEquals(List.of(buyerWallet), buy.walletSnapshots());
        assertEquals(3, buy.claims().size());
        assertTrue(buy.claims().stream().anyMatch(claim ->
                claim.kind() == ClaimKind.ITEM
                        && claim.ownerId().equals(buyer)));
        assertTrue(buy.claims().stream().anyMatch(claim ->
                claim.kind() == ClaimKind.REFUND
                        && claim.ownerId().equals(bidder)));
        var sellerClaim = buy.claims().stream().filter(claim ->
                claim.kind() == ClaimKind.MONEY
                        && claim.ownerId().equals(listing.sellerId()))
                .findFirst().orElseThrow();
        assertEquals(975L, sellerClaim.originalUnits());
        assertEquals(975L, sellerClaim.remainingUnits());
        assertEquals(ClaimStatus.PENDING, sellerClaim.status());
        assertEquals(AuctionEscrowIds.sellerProceedsClaimId(
                command.requestId(), listing.sellerId()),
                sellerClaim.claimId());
        assertEquals("auction." + listing.listingId()
                + ".seller.proceeds", sellerClaim.sourceKey());
        assertFalse(buy.ledgerTransaction().orElseThrow().legs().stream()
                .anyMatch(leg -> leg.account().ownerKey().equals(
                        listing.sellerId().toString())
                        && (leg.account().type()
                        == LedgerAccountType.PLAYER_WALLET
                        || leg.account().type()
                        == LedgerAccountType.PLAYER_DEBT)));
    }

    @Test
    void timedSettlementAlwaysCreatesFullSellerProceedsClaim() {
        Applied created = created();
        AuctionListing listing = created.snapshot().listings().get(
                AuctionEscrowTestFixtures.id(2));
        UUID bidder = AuctionEscrowTestFixtures.id(70);
        AuctionEscrowCommit bid = AuctionEscrowLifecyclePlanner.bid(
                created.snapshot(), new PlaceAuctionBidCommand(
                        AuctionEscrowTestFixtures.id(71),
                        listing.listingId(), listing.revision(),
                        AuctionEscrowTestFixtures.id(72), bidder,
                        AuctionEscrowTestFixtures.id(73),
                        AuctionEscrowTestFixtures.id(74),
                        100L, 100L, 1200L),
                AuctionEscrowTestFixtures.wallet(bidder, 1000L,
                        100_000L), AuctionEscrowTestFixtures.CURRENCY_ID,
                AuctionEscrowTestFixtures.NOW);
        var afterBid = AuctionEscrowLifecycleRepository.apply(
                created.snapshot(), created.lifecycle(),
                new AuctionEscrowLifecycleEvent.Commit(Optional.empty(),
                        bid));
        AuctionListing leading = afterBid.auctionHouse().listings().get(
                listing.listingId());
        ExpireAuctionCommand command = new ExpireAuctionCommand(
                AuctionEscrowTestFixtures.id(75), leading.listingId(),
                leading.revision(), AuctionEscrowTestFixtures.id(76),
                leading.deadlineMillis());

        AuctionEscrowCommit expired = AuctionEscrowLifecyclePlanner.expire(
                afterBid.auctionHouse(), command,
                AuctionEscrowTestFixtures.preparedCreate()
                        .plannedCustody(),
                Optional.of(new AuctionEscrowWalletSnapshot(
                        listing.sellerId(), 0L, -500L,
                        0L, 0L, 101L)),
                AuctionEscrowTestFixtures.CURRENCY_ID,
                AuctionEscrowTestFixtures.NOW.plusSeconds(1));

        assertEquals(2, expired.bookMutations().size());
        assertEquals(AuctionListingState.SETTLED,
                expired.finalListing().state());
        assertTrue(expired.walletSnapshots().isEmpty());
        var sellerClaim = expired.claims().stream()
                .filter(claim -> claim.kind() == ClaimKind.MONEY)
                .findFirst().orElseThrow();
        assertEquals(listing.sellerId(), sellerClaim.ownerId());
        assertEquals(98L, sellerClaim.originalUnits());
        assertEquals(98L, sellerClaim.remainingUnits());
        assertEquals(ClaimStatus.PENDING, sellerClaim.status());
        assertEquals(AuctionEscrowIds.sellerProceedsClaimId(
                command.requestId(), listing.sellerId()),
                sellerClaim.claimId());
        assertFalse(expired.ledgerTransaction().orElseThrow().legs()
                .stream().anyMatch(leg -> leg.account().type()
                == LedgerAccountType.PLAYER_WALLET
                || leg.account().type()
                == LedgerAccountType.PLAYER_DEBT));
        AuctionEscrowLifecycleEvent.Commit event =
                new AuctionEscrowLifecycleEvent.Commit(Optional.empty(),
                        expired);
        var applied = AuctionEscrowLifecycleRepository.apply(
                afterBid.auctionHouse(), afterBid.lifecycle(), event);
        assertFalse(applied.replayed());
        assertTrue(AuctionEscrowLifecycleRepository.apply(
                applied.auctionHouse(), applied.lifecycle(), event)
                .replayed());
        assertEquals(expired, AuctionEscrowCommitCodec.decode(
                AuctionEscrowCommitCodec.encode(expired)));
    }

    @Test
    void largeSellerProceedsRemainExactAndOverflowFailsClosed() {
        long safePrice = Long.MAX_VALUE / 4L;
        AuctionCreateEscrowIntent intent =
                AuctionEscrowTestFixtures.preparedCreate(
                        100L, safePrice,
                        AuctionEscrowTestFixtures.rules());
        Applied created = created(intent);
        AuctionListing listing = created.snapshot().listings().get(
                intent.listingId());
        UUID buyer = AuctionEscrowTestFixtures.id(80);
        AuctionEscrowCommit commit = AuctionEscrowLifecyclePlanner.buyNow(
                created.snapshot(), new AuctionBuyNowCommand(
                        AuctionEscrowTestFixtures.id(81),
                        listing.listingId(), listing.revision(), buyer,
                        AuctionEscrowTestFixtures.id(82),
                        AuctionEscrowTestFixtures.id(83),
                        AuctionEscrowTestFixtures.id(84), safePrice,
                        1300L),
                AuctionEscrowTestFixtures.wallet(buyer, safePrice,
                        Long.MAX_VALUE),
                AuctionEscrowTestFixtures.wallet(listing.sellerId(), 0L,
                        0L), intent.plannedCustody(),
                AuctionEscrowTestFixtures.CURRENCY_ID,
                AuctionEscrowTestFixtures.NOW.plusSeconds(2));
        long tax = listing.rules().saleTax(safePrice);
        long expectedNet = Math.subtractExact(safePrice, tax);
        var sellerClaim = commit.claims().stream()
                .filter(claim -> claim.kind() == ClaimKind.MONEY)
                .findFirst().orElseThrow();

        assertEquals(expectedNet, sellerClaim.originalUnits());
        assertEquals(expectedNet, sellerClaim.remainingUnits());
        assertEquals(commit, AuctionEscrowCommitCodec.decode(
                AuctionEscrowCommitCodec.encode(commit)));

        AuctionCreateEscrowIntent overflowing =
                AuctionEscrowTestFixtures.preparedCreate(
                        100L, Long.MAX_VALUE,
                        AuctionEscrowTestFixtures.rules());
        Applied overflowingCreated = created(overflowing);
        AuctionListing overflowingListing = overflowingCreated.snapshot()
                .listings().get(overflowing.listingId());
        assertThrows(ArithmeticException.class,
                () -> AuctionEscrowLifecyclePlanner.buyNow(
                        overflowingCreated.snapshot(),
                        new AuctionBuyNowCommand(
                                AuctionEscrowTestFixtures.id(85),
                                overflowingListing.listingId(),
                                overflowingListing.revision(), buyer,
                                AuctionEscrowTestFixtures.id(86),
                                AuctionEscrowTestFixtures.id(87),
                                AuctionEscrowTestFixtures.id(88),
                                Long.MAX_VALUE, 1300L),
                        AuctionEscrowTestFixtures.wallet(buyer,
                                Long.MAX_VALUE, Long.MAX_VALUE),
                        AuctionEscrowTestFixtures.wallet(
                                overflowingListing.sellerId(), 0L, 0L),
                        overflowing.plannedCustody(),
                        AuctionEscrowTestFixtures.CURRENCY_ID,
                        AuctionEscrowTestFixtures.NOW.plusSeconds(2)));
    }

    @Test
    void cancelExpiryFreezeAndResumePreserveExactTerminalRules() {
        Applied created = created();
        AuctionListing listing = created.snapshot().listings().get(
                AuctionEscrowTestFixtures.id(2));
        var custody = AuctionEscrowTestFixtures.preparedCreate()
                .plannedCustody();
        AuctionEscrowCommit cancel = AuctionEscrowLifecyclePlanner.cancel(
                created.snapshot(), new CancelAuctionCommand(
                        AuctionEscrowTestFixtures.id(60),
                        listing.listingId(), listing.revision(),
                        listing.sellerId(),
                        AuctionEscrowTestFixtures.id(61), 1200L), custody,
                AuctionEscrowTestFixtures.CURRENCY_ID,
                AuctionEscrowTestFixtures.NOW);
        assertEquals(AuctionListingState.CANCELLED,
                cancel.finalListing().state());
        assertEquals(listing.sellerId(), cancel.claims().get(0).ownerId());

        Applied second = created();
        AuctionListing secondListing = second.snapshot().listings().get(
                listing.listingId());
        AuctionEscrowCommit freeze = AuctionEscrowLifecyclePlanner.freeze(
                second.snapshot(), new FreezeAuctionCommand(
                        AuctionEscrowTestFixtures.id(62),
                        secondListing.listingId(),
                        secondListing.revision(), 1500L),
                AuctionEscrowTestFixtures.CURRENCY_ID,
                AuctionEscrowTestFixtures.NOW);
        var frozen = AuctionEscrowLifecycleRepository.apply(
                second.snapshot(), second.lifecycle(),
                new AuctionEscrowLifecycleEvent.Commit(Optional.empty(),
                        freeze));
        AuctionListing frozenListing = frozen.auctionHouse().listings()
                .get(secondListing.listingId());
        AuctionEscrowCommit resume = AuctionEscrowLifecyclePlanner.resume(
                frozen.auctionHouse(), new ResumeAuctionCommand(
                        AuctionEscrowTestFixtures.id(63),
                        frozenListing.listingId(),
                        frozenListing.revision(), 5000L),
                AuctionEscrowTestFixtures.CURRENCY_ID,
                AuctionEscrowTestFixtures.NOW.plusSeconds(1));
        assertEquals(AuctionListingState.ACTIVE,
                resume.finalListing().state());

        Applied third = created();
        AuctionListing expiring = third.snapshot().listings().get(
                listing.listingId());
        AuctionEscrowCommit expired = AuctionEscrowLifecyclePlanner.expire(
                third.snapshot(), new ExpireAuctionCommand(
                        AuctionEscrowTestFixtures.id(64),
                        expiring.listingId(), expiring.revision(),
                        AuctionEscrowTestFixtures.id(65),
                        expiring.deadlineMillis()), custody,
                Optional.empty(), AuctionEscrowTestFixtures.CURRENCY_ID,
                AuctionEscrowTestFixtures.NOW.plusSeconds(2));
        assertEquals(AuctionListingState.ENDED_UNSOLD,
                expired.finalListing().state());
        assertEquals(expiring.sellerId(),
                expired.claims().get(0).ownerId());
    }

    @Test
    void preparedCustodyRecoveryFailsClosedOnMismatchedEvidence() {
        AuctionCreateEscrowIntent intent =
                AuctionEscrowTestFixtures.preparedCreate();
        assertEquals(AuctionCreateRecoveryDecision.Action.COMMIT,
                AuctionCreateRecoveryDecision.decide(intent,
                        AuctionCreateRecoveryDecision.CustodyState.COMMITTED,
                        Optional.of(intent.plannedCustody().receipt()))
                        .action());
        assertEquals(AuctionCreateRecoveryDecision.Action.RETRY_CUSTODY,
                AuctionCreateRecoveryDecision.decide(intent,
                        AuctionCreateRecoveryDecision.CustodyState.PREPARED,
                        Optional.empty()).action());
        assertEquals(AuctionCreateRecoveryDecision.Action.MANUAL_REVIEW,
                AuctionCreateRecoveryDecision.decide(intent,
                        AuctionCreateRecoveryDecision.CustodyState.COMMITTED,
                        Optional.empty()).action());
    }

    private static Applied created() {
        AuctionCreateEscrowIntent intent =
                AuctionEscrowTestFixtures.preparedCreate();
        return created(intent);
    }

    private static Applied created(AuctionCreateEscrowIntent intent) {
        AuctionEscrowCommit commit = AuctionEscrowLifecyclePlanner
                .commitCreate(AuctionHouseSnapshot.empty(), intent,
                        intent.plannedCustody().receipt());
        var prepared = AuctionEscrowLifecycleRepository.apply(
                AuctionHouseSnapshot.empty(),
                AuctionEscrowLifecycleState.empty(),
                new AuctionEscrowLifecycleEvent.Prepare(intent));
        var completed = AuctionEscrowLifecycleRepository.apply(
                prepared.auctionHouse(), prepared.lifecycle(),
                new AuctionEscrowLifecycleEvent.Commit(
                        Optional.of(intent.complete()), commit));
        return new Applied(completed.auctionHouse(),
                completed.lifecycle());
    }

    private record Applied(
            AuctionHouseSnapshot snapshot,
            AuctionEscrowLifecycleState lifecycle
    ) {
    }
}
