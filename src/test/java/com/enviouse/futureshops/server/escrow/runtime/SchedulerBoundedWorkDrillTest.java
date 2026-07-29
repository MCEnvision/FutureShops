package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.config.BazaarConfig;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationResult;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationStatus;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationType;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrder;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBook;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBookSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderState;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderType;
import com.enviouse.futureshops.server.market.bazaar.BazaarProduct;
import com.enviouse.futureshops.server.market.bazaar.BazaarProductStatus;
import com.enviouse.futureshops.server.market.bazaar.BazaarRuleSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarRuleSnapshotFactory;
import com.enviouse.futureshops.server.market.bazaar.BazaarTimeInForce;
import com.enviouse.futureshops.server.market.bazaar.CreateBazaarOrderCommand;
import com.enviouse.futureshops.server.market.bazaar.ExpireBazaarOrderCommand;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6 scheduler drills (plan §17 bounded work per tick, §15 expiration
 * spikes): expiration request ids are a pure function of order identity and
 * revision — the same across sweeps, restarts, and snapshot restores, so a
 * crash retry replays instead of double-refunding — while an expiry spike
 * larger than the per-sweep cap drains deterministically across sweeps with
 * every order refunded exactly once. Pure seams only; no runtime, no Forge.
 */
class SchedulerBoundedWorkDrillTest {

    private static final long CREATED_AT = 1_000L;
    private static final long EXPIRES_AT = 3_600_000L;
    private static final int SPIKE = 100;

    @Test
    void expirationRequestIdsAreStableAcrossSweepsAndRestores() {
        BazaarRuleSnapshot rules = rules();
        BazaarOrderBook book = book(rules);
        BazaarOrder order = timedBuyOrder(book, rules, 100L, id(150L));

        // The sweep time never influences the id — only order and revision.
        ExpireBazaarOrderCommand early =
                BazaarExpirationScheduler.expireCommand(order, EXPIRES_AT);
        ExpireBazaarOrderCommand late =
                BazaarExpirationScheduler.expireCommand(order,
                        EXPIRES_AT + 60_000L);
        assertEquals(early.requestId(), late.requestId());

        // A restart rebuilds the book from its snapshot; the recomputed
        // command still carries the same replay key.
        BazaarOrderBookSnapshot snapshot = book.snapshot();
        BazaarOrder restored = BazaarOrderBook.restore(snapshot)
                .order(order.orderId()).orElseThrow();
        assertEquals(early.requestId(), BazaarExpirationScheduler
                .expireCommand(restored, EXPIRES_AT + 120_000L)
                .requestId());

        // Retrying the same command replays the stored receipt: exactly one
        // refund no matter how many sweeps observe the order.
        assertEquals(BazaarOperationStatus.APPLIED,
                book.expire(early).status());
        BazaarOperationResult retried = book.expire(early);
        assertTrue(retried.replayed());
        assertEquals(1L, expireReceiptCount(book));
    }

    @Test
    void revisionChangeDerivesAFreshRequestThatRevalidates() {
        BazaarRuleSnapshot rules = rules();
        BazaarOrderBook book = book(rules);
        BazaarOrder order = timedBuyOrder(book, rules, 200L, id(250L));
        ExpireBazaarOrderCommand stale =
                BazaarExpirationScheduler.expireCommand(order, EXPIRES_AT);

        // A concurrent freeze advances the revision before the sweep lands.
        book.setProductStatus("emerald", BazaarProductStatus.HALTED);
        BazaarOrder frozen = book.order(order.orderId()).orElseThrow();
        assertNotEquals(order.revision(), frozen.revision());
        ExpireBazaarOrderCommand fresh =
                BazaarExpirationScheduler.expireCommand(frozen, EXPIRES_AT);

        assertNotEquals(stale.requestId(), fresh.requestId());
        assertEquals(BazaarOperationStatus.REVISION_CHANGED,
                book.expire(stale).status());
        assertEquals(BazaarOperationStatus.APPLIED,
                book.expire(fresh).status());
        assertEquals(1L, expireReceiptCount(book));
    }

    @Test
    void expirySpikeDrainsAcrossBoundedSweeps() throws Exception {
        int cap = maxPerSweep();
        assertTrue(cap > 0 && cap < SPIKE,
                "The drill spike must exceed the per-sweep cap");

        BazaarRuleSnapshot rules = rules();
        BazaarOrderBook book = book(rules);
        for (int index = 0; index < SPIKE; index++) {
            // Unique owner per order so no per-player cap interferes.
            timedBuyOrder(book, rules, 1_000L + index * 10L,
                    id(50_000L + index));
        }

        long now = EXPIRES_AT + 1L;
        assertEquals(SPIKE, dueOrders(book, now).size());

        // First bounded sweep: exactly `cap` orders resolve.
        assertEquals(cap, sweepOnce(book, now, cap));
        assertEquals(SPIKE - cap, dueOrders(book, now).size());
        assertEquals(cap, expireReceiptCount(book));

        // The remainder drains on the next sweep — nothing is starved.
        assertEquals(SPIKE - cap, sweepOnce(book, now, cap));
        assertEquals(0, dueOrders(book, now).size());

        // Every order refunded exactly once, all terminal.
        assertEquals(SPIKE, expireReceiptCount(book));
        assertEquals(SPIKE, book.snapshot().orders().stream()
                .filter(order -> order.state()
                        == BazaarOrderState.EXPIRED).count());
    }

    /** One bounded sweep over the book, mirroring the scheduler loop:
     * re-reads due work, resolves at most {@code cap}, returns the count. */
    private static int sweepOnce(BazaarOrderBook book, long now, int cap) {
        int resolved = 0;
        for (BazaarOrder order : dueOrders(book, now)) {
            if (resolved >= cap) {
                break;
            }
            BazaarOperationResult result = book.expire(
                    BazaarExpirationScheduler.expireCommand(order, now));
            assertEquals(BazaarOperationStatus.APPLIED, result.status());
            resolved++;
        }
        return resolved;
    }

    private static List<BazaarOrder> dueOrders(
            BazaarOrderBook book,
            long now
    ) {
        List<BazaarOrder> due = new ArrayList<>();
        for (BazaarOrder order : book.snapshot().orders()) {
            if (BazaarExpirationScheduler.expirable(order, now)) {
                due.add(order);
            }
        }
        return due;
    }

    /** Counts APPLIED expirations only: a failed attempt (for example a
     * stale revision) records a receipt too, but never a refund. */
    private static long expireReceiptCount(BazaarOrderBook book) {
        return book.snapshot().receipts().values().stream()
                .filter(receipt -> receipt.result().operation()
                        == BazaarOperationType.EXPIRE
                        && receipt.result().status()
                        == BazaarOperationStatus.APPLIED).count();
    }

    /** Reads the private bounded-work constant so the drill tracks the
     * production cap instead of hard-coding a copy that could drift. */
    private static int maxPerSweep() throws Exception {
        Field field = BazaarExpirationScheduler.class
                .getDeclaredField("MAX_PER_SWEEP");
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static BazaarRuleSnapshot rules() {
        return BazaarRuleSnapshotFactory.from(
                BazaarConfig.Settings.defaults(), 0L);
    }

    private static BazaarOrderBook book(BazaarRuleSnapshot rules) {
        BazaarOrderBook book = new BazaarOrderBook();
        book.registerProduct(new BazaarProduct("emerald", 1L,
                "minecraft:emerald", "", "materials", 1, 1L, 1L,
                1_000_000_000L, 1_000_000, BazaarProductStatus.ACTIVE));
        book.setEffectiveRules(rules);
        return book;
    }

    private static BazaarOrder timedBuyOrder(
            BazaarOrderBook book,
            BazaarRuleSnapshot rules,
            long seed,
            UUID owner
    ) {
        CreateBazaarOrderCommand create = new CreateBazaarOrderCommand(
                id(seed), id(seed + 1L), owner, id(seed + 3L),
                Optional.of(id(seed + 4L)), Optional.empty(), "emerald",
                1L, BazaarOrderSide.BUY, BazaarOrderType.LIMIT,
                BazaarTimeInForce.GOOD_UNTIL_TIME, 100L, 10, CREATED_AT,
                EXPIRES_AT, rules);
        BazaarOperationResult created = book.create(create);
        assertEquals(BazaarOperationStatus.APPLIED, created.status());
        return created.order().orElseThrow();
    }

    private static UUID id(long value) {
        return new UUID(87L, value);
    }
}
