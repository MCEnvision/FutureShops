package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.config.BazaarConfig;
import com.enviouse.futureshops.server.market.bazaar.BazaarIds;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationResult;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationStatus;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationType;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrder;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBook;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderState;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderType;
import com.enviouse.futureshops.server.market.bazaar.BazaarProduct;
import com.enviouse.futureshops.server.market.bazaar.BazaarProductStatus;
import com.enviouse.futureshops.server.market.bazaar.BazaarRuleSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarRuleSnapshotFactory;
import com.enviouse.futureshops.server.market.bazaar.BazaarSettlementKind;
import com.enviouse.futureshops.server.market.bazaar.BazaarTimeInForce;
import com.enviouse.futureshops.server.market.bazaar.CancelBazaarOrderCommand;
import com.enviouse.futureshops.server.market.bazaar.CreateBazaarOrderCommand;
import com.enviouse.futureshops.server.market.bazaar.ExpireBazaarOrderCommand;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure decision logic of {@link BazaarExpirationScheduler}: which orders are due, and that its
 * deterministic commands are exactly what {@link BazaarOrderBook#expire} accepts — an expiration
 * retry replays the stored receipt instead of producing a second refund. No Forge bootstrap.
 */
class BazaarExpirationSchedulerTest {

    private static final long CREATED_AT = 1_000L;
    private static final long EXPIRES_AT = 3_600_000L;

    private static BazaarRuleSnapshot rules() {
        return BazaarRuleSnapshotFactory.from(BazaarConfig.Settings.defaults(), 0L);
    }

    private static BazaarOrderBook book(BazaarRuleSnapshot rules) {
        BazaarOrderBook book = new BazaarOrderBook();
        book.registerProduct(new BazaarProduct("emerald", 1L, "minecraft:emerald", "",
                "materials", 1, 1L, 1L, 1_000_000_000L, 1_000_000,
                BazaarProductStatus.ACTIVE));
        book.setEffectiveRules(rules);
        return book;
    }

    private static BazaarOrder timedBuyOrder(BazaarOrderBook book, BazaarRuleSnapshot rules,
                                             long seed) {
        CreateBazaarOrderCommand create = new CreateBazaarOrderCommand(id(seed), id(seed + 1L),
                id(seed + 2L), id(seed + 3L), Optional.of(id(seed + 4L)), Optional.empty(),
                "emerald", 1L, BazaarOrderSide.BUY, BazaarOrderType.LIMIT,
                BazaarTimeInForce.GOOD_UNTIL_TIME, 100L, 10, CREATED_AT, EXPIRES_AT, rules);
        BazaarOperationResult created = book.create(create);
        assertEquals(BazaarOperationStatus.APPLIED, created.status());
        return created.order().orElseThrow();
    }

    private static UUID id(long value) {
        return new UUID(83L, value);
    }

    @Test
    void onlyDueOpenOrFrozenOrdersAreExpirable() {
        BazaarRuleSnapshot rules = rules();
        BazaarOrderBook book = book(rules);
        BazaarOrder order = timedBuyOrder(book, rules, 100L);

        assertFalse(BazaarExpirationScheduler.expirable(order, EXPIRES_AT - 1L));
        assertTrue(BazaarExpirationScheduler.expirable(order, EXPIRES_AT));
        assertTrue(BazaarExpirationScheduler.expirable(order, EXPIRES_AT + 1L));

        // A frozen order still refunds its remaining reserve (plan §11 never blocks refunds).
        book.setProductStatus("emerald", BazaarProductStatus.HALTED);
        BazaarOrder frozen = book.order(order.orderId()).orElseThrow();
        assertEquals(BazaarOrderState.FROZEN, frozen.state());
        assertTrue(BazaarExpirationScheduler.expirable(frozen, EXPIRES_AT));
        book.setProductStatus("emerald", BazaarProductStatus.ACTIVE);

        // Terminal orders are never revisited.
        BazaarOrder open = book.order(order.orderId()).orElseThrow();
        UUID cancelRequest = id(120L);
        BazaarOperationResult cancelled = book.cancel(new CancelBazaarOrderCommand(
                cancelRequest, open.orderId(), open.ownerId(),
                BazaarIds.terminal(cancelRequest, open.orderId(), BazaarOperationType.CANCEL),
                open.revision(), EXPIRES_AT - 10L));
        assertEquals(BazaarOperationStatus.APPLIED, cancelled.status());
        assertFalse(BazaarExpirationScheduler.expirable(
                cancelled.order().orElseThrow(), EXPIRES_AT + 1L));
    }

    @Test
    void goodUntilCancelledOrdersNeverExpire() {
        BazaarRuleSnapshot rules = rules();
        BazaarOrderBook book = book(rules);
        CreateBazaarOrderCommand create = new CreateBazaarOrderCommand(id(200L), id(201L),
                id(202L), id(203L), Optional.of(id(204L)), Optional.empty(), "emerald", 1L,
                BazaarOrderSide.BUY, BazaarOrderType.LIMIT,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, 100L, 10, CREATED_AT, 0L, rules);
        BazaarOperationResult created = book.create(create);
        assertEquals(BazaarOperationStatus.APPLIED, created.status());
        assertFalse(BazaarExpirationScheduler.expirable(
                created.order().orElseThrow(), Long.MAX_VALUE - 1L));
    }

    @Test
    void expireCommandIsDeterministicPerRevisionAndCanonicalForTheBook() {
        BazaarRuleSnapshot rules = rules();
        BazaarOrderBook book = book(rules);
        BazaarOrder order = timedBuyOrder(book, rules, 300L);

        ExpireBazaarOrderCommand command =
                BazaarExpirationScheduler.expireCommand(order, EXPIRES_AT);
        assertEquals(command, BazaarExpirationScheduler.expireCommand(order, EXPIRES_AT));
        assertEquals(order.revision(), command.expectedRevision());
        assertEquals(BazaarIds.terminal(command.requestId(), order.orderId(),
                BazaarOperationType.EXPIRE), command.terminalTransactionId());

        BazaarOperationResult expired = book.expire(command);
        assertEquals(BazaarOperationStatus.APPLIED, expired.status());
        assertEquals(BazaarOrderState.EXPIRED, expired.order().orElseThrow().state());
        // The full unspent reserve returns to the owner as a money claim.
        assertEquals(order.reservedMoneyMinor(), expired.settlements().stream()
                .filter(settlement -> settlement.kind()
                        == BazaarSettlementKind.BUYER_REFUND_CLAIM)
                .mapToLong(settlement -> settlement.moneyMinor()).sum());

        // A retried sweep replays the stored receipt — never a second refund (plan §15).
        BazaarOperationResult replayed = book.expire(command);
        assertTrue(replayed.replayed());
        assertEquals(BazaarOperationStatus.APPLIED, replayed.status());
        assertEquals(1L, book.snapshot().receipts().values().stream()
                .filter(receipt -> receipt.result().operation()
                        == BazaarOperationType.EXPIRE).count());
    }

    @Test
    void aRevisionChangeDerivesAFreshRequestThatRevalidates() {
        BazaarRuleSnapshot rules = rules();
        BazaarOrderBook book = book(rules);
        BazaarOrder order = timedBuyOrder(book, rules, 400L);

        ExpireBazaarOrderCommand stale =
                BazaarExpirationScheduler.expireCommand(order, EXPIRES_AT);
        book.setProductStatus("emerald", BazaarProductStatus.HALTED);
        BazaarOrder frozen = book.order(order.orderId()).orElseThrow();
        assertNotEquals(order.revision(), frozen.revision());

        ExpireBazaarOrderCommand fresh =
                BazaarExpirationScheduler.expireCommand(frozen, EXPIRES_AT);
        assertNotEquals(stale.requestId(), fresh.requestId());

        // The stale command fails closed on its revision; the fresh one succeeds.
        assertEquals(BazaarOperationStatus.REVISION_CHANGED, book.expire(stale).status());
        assertEquals(BazaarOperationStatus.APPLIED, book.expire(fresh).status());
    }
}
