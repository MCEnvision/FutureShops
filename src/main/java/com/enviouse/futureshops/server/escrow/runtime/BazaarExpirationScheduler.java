package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.market.bazaar.BazaarIds;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationResult;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationStatus;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationType;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrder;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBook;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBookSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderState;
import com.enviouse.futureshops.server.market.bazaar.BazaarSavedData;
import com.enviouse.futureshops.server.market.bazaar.ExpireBazaarOrderCommand;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowCommit;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecyclePlanner;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleState;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Time-driven Bazaar order expiry (plan §9 order types / §17 bounded work per tick): terminates
 * GOOD_UNTIL_TIME orders whose deadline passed, returning the unspent money reserve or the
 * unfilled item custody to the owner as claims through the same durable escrow path player
 * actions use. Deterministic request ids ({@code orderId × "expire" × revision}) make a retried
 * expiration a replay, never a second refund. Runs while the module is disabled — expiry is a
 * refund-shaped operation and refunds never freeze (plan §11). Modeled on
 * {@code StockRefreshScheduler} / {@code AuctionExpirationScheduler}.
 */
public final class BazaarExpirationScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(BazaarExpirationScheduler.class);
    /** Sweep cadence: every 5 seconds (100 ticks). Order deadlines are hour-scale. */
    private static final int CHECK_INTERVAL_TICKS = 100;
    /** Maximum orders expired per sweep — expiry spikes drain over several sweeps (plan §15). */
    private static final int MAX_PER_SWEEP = 64;
    /** Maximum crashed create intents recovered per sweep. */
    private static final int MAX_RECOVERY_PER_SWEEP = 8;

    private static final AtomicInteger TICKS = new AtomicInteger();

    private BazaarExpirationScheduler() {
    }

    public static void onServerTick(MinecraftServer server) {
        if (TICKS.incrementAndGet() < CHECK_INTERVAL_TICKS) {
            return;
        }
        TICKS.set(0);
        sweep(server);
    }

    /** Runs one sweep immediately (tests / admin tooling). Server thread only. */
    public static void trigger(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "Bazaar expiration requires the server thread");
        }
        TICKS.set(0);
        sweep(server);
    }

    public static void reset() {
        TICKS.set(0);
    }

    static void sweep(MinecraftServer server) {
        EscrowRuntimeService runtime = BazaarActionService.readyRuntime();
        if (runtime == null || server == null) {
            return;
        }
        try {
            // Recovery FIRST: unfinished create intents (crash between Prepare and Commit)
            // resolve before new expiry work — committed sell custody completes the order,
            // uncommitted custody aborts safely, BUY resumes its atomic wallet decision
            // (plan §4.2). Without this pass, a sell-order crash permanently strands the
            // extracted items with a PREPARED intent nothing ever drains.
            recoverPreparedCreates(server, runtime);
            long nowMillis = System.currentTimeMillis();
            BazaarOrderBookSnapshot snapshot = runtime.bazaarSnapshot();
            int resolved = 0;
            for (BazaarOrder order : snapshot.orders()) {
                if (resolved >= MAX_PER_SWEEP) {
                    break;
                }
                if (expireDue(server, runtime, order, nowMillis)) {
                    resolved++;
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Bazaar expiration sweep failed", exception);
        }
    }

    /** Bounded recovery pass over PREPARED create intents (max {@value #MAX_RECOVERY_PER_SWEEP}). */
    private static void recoverPreparedCreates(MinecraftServer server,
                                               EscrowRuntimeService runtime) {
        for (var intent : runtime.pendingBazaarCreateRecovery(MAX_RECOVERY_PER_SWEEP)) {
            try {
                var committedReceipt = intent.sellCustody()
                        .flatMap(custody -> runtime.itemInventoryMutationGateway()
                                .find(custody.receipt().token().requestId()))
                        .flatMap(entry -> entry.committedReceipt());
                var decision = com.enviouse.futureshops.server.market.bazaar.escrow
                        .BazaarCreateRecoveryDecision.inspect(intent, committedReceipt);
                switch (decision.action()) {
                    case RESUME_COMMIT -> {
                        BazaarEscrowLifecycleState lifecycle =
                                BazaarSavedData.get(server).escrowLifecycleSnapshot();
                        var planned = BazaarEscrowLifecyclePlanner.commitCreate(
                                runtime.bazaarSnapshot(), lifecycle, intent,
                                intent.command().side() == com.enviouse.futureshops.server
                                        .market.bazaar.BazaarOrderSide.SELL
                                        ? committedReceipt : Optional.empty(),
                                Instant.ofEpochMilli(System.currentTimeMillis()));
                        runtime.commitBazaarEscrowLifecycle(
                                new BazaarEscrowLifecycleEvent.Commit(
                                        Optional.of(planned.terminalIntent()),
                                        planned.commit()));
                    }
                    case ABORT_SAFE -> runtime.commitBazaarEscrowLifecycle(
                            new BazaarEscrowLifecycleEvent.Resolve(intent,
                                    decision.terminalIntent().orElseThrow()));
                    case MANUAL_REVIEW -> LOGGER.warn(
                            "Bazaar create {} requires manual review: {}",
                            intent.command().requestId(), decision.detail());
                    case ALREADY_TERMINAL -> {
                        // Terminal intents fall out of the pending list on their own.
                    }
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Bazaar create recovery for {} failed; retrying next sweep",
                        intent.command().requestId(), exception);
            }
        }
    }

    /** Expires one due order; returns whether a commit happened. Never throws. */
    private static boolean expireDue(MinecraftServer server, EscrowRuntimeService runtime,
                                     BazaarOrder order, long nowMillis) {
        try {
            if (!expirable(order, nowMillis)) {
                return false;
            }
            ExpireBazaarOrderCommand command = expireCommand(order, nowMillis);
            if (runtime.bazaarReceipt(command.requestId()).isPresent()) {
                return false; // already resolved at this revision
            }
            // Re-read: earlier commits in this sweep may have advanced the book.
            BazaarOrderBookSnapshot snapshot = runtime.bazaarSnapshot();
            BazaarOperationResult preview = BazaarOrderBook.restore(snapshot).expire(command);
            if (preview.status() != BazaarOperationStatus.APPLIED) {
                return false; // revision moved or the order resolved another way; next sweep
            }
            BazaarEscrowLifecycleState lifecycle =
                    BazaarSavedData.get(server).escrowLifecycleSnapshot();
            BazaarEscrowCommit commit = BazaarEscrowLifecyclePlanner.expire(snapshot,
                    lifecycle, command, BazaarActionService.currencyId(),
                    Instant.ofEpochMilli(nowMillis));
            runtime.commitBazaarEscrowLifecycle(new BazaarEscrowLifecycleEvent.Commit(
                    Optional.empty(), commit));
            return true;
        } catch (RuntimeException exception) {
            LOGGER.error("Bazaar order {} expiration failed; will retry next sweep",
                    order.orderId(), exception);
            return false;
        }
    }

    /**
     * An order is due when it carries a deadline that passed and is still terminable — open,
     * partially filled, or frozen (a frozen order's remaining reserve still refunds; plan §11
     * never blocks refunds).
     */
    static boolean expirable(BazaarOrder order, long nowMillis) {
        Objects.requireNonNull(order, "order");
        return !order.state().terminal()
                && (order.matchable() || order.state() == BazaarOrderState.FROZEN)
                && order.expiredAt(nowMillis);
    }

    /**
     * The command for one expiration attempt. The request id is derived from
     * {@code orderId × "expire" × revision} so a crash retry replays the stored receipt instead
     * of producing a second refund, while a revision change (a fill or an unfreeze landed first)
     * derives a fresh request that revalidates from scratch.
     */
    static ExpireBazaarOrderCommand expireCommand(BazaarOrder order, long nowMillis) {
        Objects.requireNonNull(order, "order");
        UUID requestId = BazaarActionService.derived(
                "bazaar.expire." + order.revision() + ".", order.orderId());
        return new ExpireBazaarOrderCommand(requestId, order.orderId(),
                BazaarIds.terminal(requestId, order.orderId(), BazaarOperationType.EXPIRE),
                order.revision(), nowMillis);
    }
}
