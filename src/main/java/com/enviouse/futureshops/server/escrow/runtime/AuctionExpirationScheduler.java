package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.market.auction.AuctionHouseBook;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionListing;
import com.enviouse.futureshops.server.market.auction.AuctionListingState;
import com.enviouse.futureshops.server.market.auction.AuctionOperationResult;
import com.enviouse.futureshops.server.market.auction.ExpireAuctionCommand;
import com.enviouse.futureshops.server.market.auction.SettleAuctionCommand;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowCommit;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowItemCustody;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecyclePlanner;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Time-driven Auction House work (plan §8 time / §17 bounded work per tick): expires listings
 * whose deadline passed — unsold listings return the item to the seller as claims, sold listings
 * settle to the winner — in bounded batches through the same durable escrow path player actions
 * use. Deterministic request ids ({@code listingId × revision}) make a retried expiration a
 * replay, never a second economic result. Modeled on {@code StockRefreshScheduler}.
 */
public final class AuctionExpirationScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AuctionExpirationScheduler.class);
    /** Sweep cadence: every 5 seconds (100 ticks). Deadlines are minute-scale. */
    private static final int CHECK_INTERVAL_TICKS = 100;
    /** Maximum listings resolved per sweep — expiry spikes drain over several sweeps. */
    private static final int MAX_PER_SWEEP = 32;
    /** Maximum crashed create intents recovered per sweep. */
    private static final int MAX_RECOVERY_PER_SWEEP = 8;

    private static final AtomicInteger TICKS = new AtomicInteger();

    private AuctionExpirationScheduler() {
    }

    public static void onServerTick(MinecraftServer server) {
        if (TICKS.incrementAndGet() < CHECK_INTERVAL_TICKS) {
            return;
        }
        TICKS.set(0);
        sweep(server);
    }

    public static void reset() {
        TICKS.set(0);
    }

    /** Runs one sweep immediately (admin `/marketadmin sweep` / tests). Server thread only. */
    public static void trigger(MinecraftServer server) {
        java.util.Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "Auction expiration requires the server thread");
        }
        TICKS.set(0);
        sweep(server);
    }

    static void sweep(MinecraftServer server) {
        EscrowRuntimeService runtime = AuctionActionService.readyRuntime();
        if (runtime == null || server == null) {
            return;
        }
        try {
            // Recovery FIRST: unfinished create intents (crash between Prepare and Commit) are
            // resolved before any new expiry work — committed custody completes the listing,
            // absent/aborted custody returns cleanly, undecided/quarantined stays for the item
            // runtime or manual review (plan §4.2: recovery restores or completes, never both).
            recoverPreparedCreates(runtime);
            // Module FREEZE pauses auction time (plan §11): no expiry/settlement while frozen —
            // settling a sold listing during a freeze would move value the operator paused.
            if (auctionTimersPaused(server)) {
                return;
            }
            long nowMillis = System.currentTimeMillis();
            AuctionHouseSnapshot snapshot = runtime.auctionHouseSnapshot();
            int resolved = 0;
            for (AuctionListing listing : snapshot.listings().values()) {
                if (resolved >= MAX_PER_SWEEP) {
                    break;
                }
                if (resolveDue(runtime, listing, nowMillis)) {
                    resolved++;
                    // Each commit changes the snapshot; re-read so revisions stay current.
                    snapshot = runtime.auctionHouseSnapshot();
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Auction expiration sweep failed", exception);
        }
    }

    /** Bounded recovery pass over PREPARED create intents (max {@value #MAX_RECOVERY_PER_SWEEP}). */
    private static void recoverPreparedCreates(EscrowRuntimeService runtime) {
        for (var intent : runtime.pendingAuctionCreateRecovery(MAX_RECOVERY_PER_SWEEP)) {
            try {
                UUID custodyRequestId = com.enviouse.futureshops.server.market.auction.escrow
                        .AuctionEscrowIds.custodyRequestId(intent.command().requestId());
                var entry = runtime.itemInventoryMutationGateway().find(custodyRequestId);
                var custodyState = entry.map(value -> switch (value.status()) {
                    case PREPARED -> com.enviouse.futureshops.server.market.auction.escrow
                            .AuctionCreateRecoveryDecision.CustodyState.PREPARED;
                    case COMMITTED -> com.enviouse.futureshops.server.market.auction.escrow
                            .AuctionCreateRecoveryDecision.CustodyState.COMMITTED;
                    case ABORTED -> com.enviouse.futureshops.server.market.auction.escrow
                            .AuctionCreateRecoveryDecision.CustodyState.ABORTED;
                    case QUARANTINED -> com.enviouse.futureshops.server.market.auction.escrow
                            .AuctionCreateRecoveryDecision.CustodyState.QUARANTINED;
                }).orElse(com.enviouse.futureshops.server.market.auction.escrow
                        .AuctionCreateRecoveryDecision.CustodyState.NONE);
                var decision = com.enviouse.futureshops.server.market.auction.escrow
                        .AuctionCreateRecoveryDecision.decide(intent, custodyState,
                        entry.flatMap(value -> value.committedReceipt()));
                switch (decision.action()) {
                    case COMMIT -> {
                        AuctionEscrowCommit commit = AuctionEscrowLifecyclePlanner.commitCreate(
                                runtime.auctionHouseSnapshot(), intent,
                                decision.receipt().orElseThrow());
                        runtime.commitAuctionEscrowLifecycle(
                                new AuctionEscrowLifecycleEvent.Commit(
                                        Optional.of(intent.complete()), commit));
                    }
                    case ABORT -> runtime.commitAuctionEscrowLifecycle(
                            new AuctionEscrowLifecycleEvent.Abort(intent, intent.abort(
                                    com.enviouse.futureshops.server.market.auction.escrow
                                            .AuctionCreateEscrowIntent.Status
                                            .ABORTED_MISSING_ITEMS)));
                    case MANUAL_REVIEW -> LOGGER.warn(
                            "Auction create {} requires manual review (custody {})",
                            intent.command().requestId(), custodyState);
                    case RETRY_CUSTODY, NONE -> {
                        // Item-runtime recovery owns undecided custody; re-inspect next sweep.
                    }
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Auction create recovery for {} failed; retrying next sweep",
                        intent.command().requestId(), exception);
            }
        }
    }

    private static boolean auctionTimersPaused(MinecraftServer server) {
        try {
            return com.enviouse.futureshops.server.market.control.MarketControlSavedData
                    .get(server).snapshot()
                    .module(com.enviouse.futureshops.server.market.control
                            .MarketControlModule.AUCTION_HOUSE)
                    .status().timersPaused();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** Resolves one due listing; returns whether a commit happened. Never throws. */
    private static boolean resolveDue(EscrowRuntimeService runtime, AuctionListing listing,
                                      long nowMillis) {
        try {
            boolean activeDue = listing.state() == AuctionListingState.ACTIVE
                    && listing.deadlineMillis() > 0L
                    && nowMillis >= listing.deadlineMillis();
            boolean soldPending = listing.state() == AuctionListingState.SOLD_PENDING;
            if (!activeDue && !soldPending) {
                return false;
            }
            UUID requestId = AuctionActionService.derived(
                    "auction.expire." + listing.revision() + ".", listing.listingId());
            if (runtime.auctionEscrowCommit(requestId).isPresent()) {
                return false; // already resolved at this revision
            }
            Optional<AuctionEscrowItemCustody> custody =
                    AuctionActionService.custodyFor(runtime, listing.listingId());
            if (custody.isEmpty()) {
                LOGGER.warn("Auction {} is due but its create custody is missing; leaving for "
                        + "manual review", listing.listingId());
                return false;
            }
            AuctionHouseSnapshot snapshot = runtime.auctionHouseSnapshot();
            Instant now = Instant.ofEpochMilli(nowMillis);
            AuctionEscrowCommit commit;
            if (soldPending) {
                SettleAuctionCommand command = new SettleAuctionCommand(requestId,
                        listing.listingId(), listing.revision(), nowMillis);
                AuctionOperationResult preview = new AuctionHouseBook(snapshot).settle(command);
                if (!preview.applied()) {
                    return false;
                }
                commit = AuctionEscrowLifecyclePlanner.settle(snapshot, command,
                        custody.orElseThrow(),
                        AuctionActionService.walletSnapshot(runtime, listing.sellerId()),
                        AuctionActionService.currencyId(), now);
            } else {
                ExpireAuctionCommand command = new ExpireAuctionCommand(requestId,
                        listing.listingId(), listing.revision(),
                        AuctionActionService.derived("auction.expiretxn.", requestId),
                        nowMillis);
                AuctionOperationResult preview = new AuctionHouseBook(snapshot).expire(command);
                if (!preview.applied()) {
                    return false;
                }
                boolean sold = listing.highestBid().isPresent();
                commit = AuctionEscrowLifecyclePlanner.expire(snapshot, command,
                        custody.orElseThrow(),
                        sold ? Optional.of(AuctionActionService.walletSnapshot(runtime,
                                listing.sellerId())) : Optional.empty(),
                        AuctionActionService.currencyId(), now);
            }
            runtime.commitAuctionEscrowLifecycle(new AuctionEscrowLifecycleEvent.Commit(
                    Optional.empty(), commit));
            AuctionActionService.postListingEvent(soldPending ? "SETTLE" : "EXPIRE", commit);
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("Auction {} expiration failed; will retry next sweep",
                    listing.listingId(), exception);
            return false;
        }
    }
}
