package com.enviouse.futureshops.server.market.bazaar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarOrderBookTest {
    private BazaarOrderBook book;
    private long nextId;

    @BeforeEach
    void setUp() {
        book = new BazaarOrderBook();
        book.registerProduct(product(1L, BazaarProductStatus.ACTIVE));
        book.setEffectiveRules(standardRules());
        nextId = 100L;
    }

    @Test
    void matchesPriceThenTimeAndKeepsTheRemainderOpen() {
        BazaarOperationResult older = create(BazaarOrderSide.SELL, id(1), 100_000L,
                10, BazaarTimeInForce.GOOD_UNTIL_CANCELLED, standardRules(), 0L, 0L);
        BazaarOperationResult better = create(BazaarOrderSide.SELL, id(2), 90_000L,
                5, BazaarTimeInForce.GOOD_UNTIL_CANCELLED, standardRules(), 1L, 0L);
        BazaarOperationResult buy = create(BazaarOrderSide.BUY, id(3), 100_000L,
                12, BazaarTimeInForce.GOOD_UNTIL_CANCELLED, standardRules(), 2L, 0L);

        assertTrue(older.newlyCommitted());
        assertTrue(better.newlyCommitted());
        assertEquals(2, buy.fills().size());
        assertEquals(better.orderId(), buy.fills().get(0).sellOrderId());
        assertEquals(90_000L, buy.fills().get(0).priceMinor());
        assertEquals(5, buy.fills().get(0).quantity());
        assertEquals(older.orderId(), buy.fills().get(1).sellOrderId());
        assertEquals(100_000L, buy.fills().get(1).priceMinor());
        assertEquals(7, buy.fills().get(1).quantity());
        assertEquals(BazaarOrderState.FILLED, buy.order().orElseThrow().state());
        assertEquals(3, book.order(older.orderId()).orElseThrow().remainingQuantity());
        assertEquals(BazaarOrderState.PARTIALLY_FILLED,
                book.order(older.orderId()).orElseThrow().state());
        assertTrue(buy.settlements().stream()
                .anyMatch(value -> value.kind() == BazaarSettlementKind.BUYER_ITEM_CLAIM));
        assertTrue(buy.settlements().stream()
                .anyMatch(value -> value.kind() == BazaarSettlementKind.SELLER_MONEY_CLAIM));
        assertTrue(buy.settlements().stream()
                .anyMatch(value -> value.kind() == BazaarSettlementKind.BUYER_CHANGE_CLAIM));
    }

    @Test
    void equalPricesUseAcceptedSequence() {
        BazaarOperationResult first = create(BazaarOrderSide.SELL, id(1), 100_000L,
                2, BazaarTimeInForce.GOOD_UNTIL_CANCELLED, standardRules(), 0L, 0L);
        BazaarOperationResult second = create(BazaarOrderSide.SELL, id(2), 100_000L,
                2, BazaarTimeInForce.GOOD_UNTIL_CANCELLED, standardRules(), 1L, 0L);
        BazaarOperationResult buy = create(BazaarOrderSide.BUY, id(3), 100_000L,
                3, BazaarTimeInForce.GOOD_UNTIL_CANCELLED, standardRules(), 2L, 0L);

        assertEquals(first.orderId(), buy.fills().get(0).sellOrderId());
        assertEquals(second.orderId(), buy.fills().get(1).sellOrderId());
    }

    @Test
    void immediateOrCancelRefundsOnlyTheUnfilledReserve() {
        create(BazaarOrderSide.SELL, id(1), 80_000L, 3,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, standardRules(), 0L, 0L);
        BazaarOperationResult buy = create(BazaarOrderSide.BUY, id(2), 100_000L,
                10, BazaarTimeInForce.IMMEDIATE_OR_CANCEL, standardRules(), 1L, 0L);

        BazaarOrder order = buy.order().orElseThrow();
        assertEquals(BazaarOrderState.CANCELLED, order.state());
        assertEquals(7, order.remainingQuantity());
        assertEquals(0L, order.reservedMoneyMinor());
        assertTrue(buy.settlements().stream().anyMatch(settlement ->
                settlement.kind() == BazaarSettlementKind.BUYER_REFUND_CLAIM));
    }

    @Test
    void fillOrKillFailureLeavesNoOrderOrIdentityReservation() {
        UUID orderId = id(40);
        UUID requestId = id(41);
        CreateBazaarOrderCommand command = command(requestId, orderId,
                BazaarOrderSide.BUY, id(3), 100_000L, 10,
                BazaarTimeInForce.FILL_OR_KILL, standardRules(), 0L, 0L);

        BazaarOperationResult first = book.create(command);
        BazaarOperationResult replay = book.create(command);

        assertEquals(BazaarOperationStatus.FILL_OR_KILL_UNAVAILABLE, first.status());
        assertTrue(replay.replayed());
        assertTrue(book.order(orderId).isEmpty());
        CreateBazaarOrderCommand retry = command(id(42), orderId,
                BazaarOrderSide.BUY, id(3), 100_000L, 10,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, standardRules(), 1L, 0L);
        assertTrue(book.create(retry).newlyCommitted());
    }

    @Test
    void requestReplayAndConflictAreDeterministic() {
        CreateBazaarOrderCommand command = command(id(50), id(51),
                BazaarOrderSide.SELL, id(1), 100_000L, 2,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, standardRules(), 0L, 0L);
        BazaarOperationResult first = book.create(command);
        BazaarOperationResult replay = book.create(command);
        CreateBazaarOrderCommand changed = new CreateBazaarOrderCommand(
                command.requestId(), command.orderId(), command.ownerId(),
                command.activationTransactionId(), command.moneyHoldAccountId(),
                command.custodyLotId(), command.productId(), command.productVersion(),
                command.side(), command.type(), command.timeInForce(),
                command.limitPriceMinor() + 1L, command.quantity(),
                command.createdAtMillis(), command.expiresAtMillis(), command.rules());
        BazaarOperationResult conflict = book.create(changed);

        assertTrue(first.newlyCommitted());
        assertTrue(replay.replayed());
        assertEquals(first.order(), replay.order());
        assertEquals(BazaarOperationStatus.REQUEST_CONFLICT, conflict.status());
        assertFalse(conflict.replayed());
    }

    @Test
    void cancelTakerPreventsSelfTradeAndRefundsTheIncomingOrder() {
        BazaarRuleSnapshot rules = rules(BazaarSelfTradePolicy.CANCEL_TAKER,
                BazaarExecutionPricePolicy.MAKER, false, 5000, 0L);
        create(BazaarOrderSide.SELL, id(1), 100_000L, 5,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules, 0L, 0L);
        BazaarOperationResult buy = create(BazaarOrderSide.BUY, id(1), 100_000L, 5,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules, 1L, 0L);

        assertTrue(buy.fills().isEmpty());
        assertEquals(BazaarOrderState.CANCELLED, buy.order().orElseThrow().state());
        assertTrue(buy.settlements().stream().anyMatch(settlement ->
                settlement.kind() == BazaarSettlementKind.BUYER_REFUND_CLAIM));
    }

    @Test
    void cancelMakerRemovesSelfOrderThenMatchesAnotherOwner() {
        BazaarRuleSnapshot rules = rules(BazaarSelfTradePolicy.CANCEL_MAKER,
                BazaarExecutionPricePolicy.MAKER, false, 5000, 0L);
        BazaarOperationResult self = create(BazaarOrderSide.SELL, id(1), 90_000L, 2,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules, 0L, 0L);
        BazaarOperationResult other = create(BazaarOrderSide.SELL, id(2), 100_000L, 2,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules, 1L, 0L);
        BazaarOperationResult buy = create(BazaarOrderSide.BUY, id(1), 100_000L, 2,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules, 2L, 0L);

        assertEquals(List.of(self.orderId()), buy.cancelledMakerOrderIds());
        assertEquals(BazaarOrderState.CANCELLED,
                book.order(self.orderId()).orElseThrow().state());
        assertEquals(other.orderId(), buy.fills().get(0).sellOrderId());
    }

    @Test
    void skipSelfSearchesPastTheSelfOrder() {
        BazaarRuleSnapshot rules = rules(BazaarSelfTradePolicy.SKIP_SELF,
                BazaarExecutionPricePolicy.MAKER, false, 5000, 0L);
        BazaarOperationResult self = create(BazaarOrderSide.SELL, id(1), 90_000L, 2,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules, 0L, 0L);
        BazaarOperationResult other = create(BazaarOrderSide.SELL, id(2), 100_000L, 2,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules, 1L, 0L);
        BazaarOperationResult buy = create(BazaarOrderSide.BUY, id(1), 100_000L, 2,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules, 2L, 0L);

        assertEquals(other.orderId(), buy.fills().get(0).sellOrderId());
        assertEquals(BazaarOrderState.OPEN, book.order(self.orderId()).orElseThrow().state());
    }

    @Test
    void circuitBreakerHaltsBeforeTheOutOfBandFill() {
        BazaarRuleSnapshot rules = rules(BazaarSelfTradePolicy.CANCEL_TAKER,
                BazaarExecutionPricePolicy.MAKER, true, 1000, 0L);
        book.setReferencePrice("iron", 100_000L);
        BazaarOperationResult sell = create(BazaarOrderSide.SELL, id(1), 200_000L, 2,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules, 0L, 0L);
        BazaarOperationResult buy = create(BazaarOrderSide.BUY, id(2), 200_000L, 2,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules, 1L, 0L);

        assertTrue(buy.productHalted());
        assertTrue(buy.fills().isEmpty());
        assertEquals(BazaarProductStatus.HALTED,
                book.product("iron").orElseThrow().status());
        assertEquals(BazaarOrderState.FROZEN,
                book.order(sell.orderId()).orElseThrow().state());
        assertEquals(BazaarOrderState.FROZEN, buy.order().orElseThrow().state());
    }

    @Test
    void fillOrKillRollsBackEveryFillWhenALaterPriceHalts() {
        BazaarRuleSnapshot rules = rules(BazaarSelfTradePolicy.CANCEL_TAKER,
                BazaarExecutionPricePolicy.MAKER, true, 1000, 0L);
        book.setReferencePrice("iron", 100_000L);
        BazaarOperationResult first = create(BazaarOrderSide.SELL, id(1),
                100_000L, 1, BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                rules, 0L, 0L);
        BazaarOperationResult second = create(BazaarOrderSide.SELL, id(2),
                200_000L, 1, BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                rules, 1L, 0L);
        BazaarOperationResult buy = create(BazaarOrderSide.BUY, id(3),
                200_000L, 2, BazaarTimeInForce.FILL_OR_KILL,
                rules, 2L, 0L);

        assertEquals(BazaarOperationStatus.PRICE_OUTSIDE_BAND,
                buy.status());
        assertTrue(book.order(buy.orderId()).isEmpty());
        assertTrue(book.fills().isEmpty());
        assertEquals(1, book.order(first.orderId()).orElseThrow()
                .remainingQuantity());
        assertEquals(BazaarOrderState.FROZEN,
                book.order(first.orderId()).orElseThrow().state());
        assertEquals(BazaarOrderState.FROZEN,
                book.order(second.orderId()).orElseThrow().state());
        assertEquals(BazaarProductStatus.HALTED,
                book.product("iron").orElseThrow().status());
    }

    @Test
    void immediateOrCancelCommitsSafeFillsAndRefundsHaltedRemainder() {
        BazaarRuleSnapshot rules = rules(BazaarSelfTradePolicy.CANCEL_TAKER,
                BazaarExecutionPricePolicy.MAKER, true, 1000, 0L);
        book.setReferencePrice("iron", 100_000L);
        BazaarOperationResult first = create(BazaarOrderSide.SELL, id(1),
                100_000L, 1, BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                rules, 0L, 0L);
        BazaarOperationResult second = create(BazaarOrderSide.SELL, id(2),
                200_000L, 1, BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                rules, 1L, 0L);
        BazaarOperationResult buy = create(BazaarOrderSide.BUY, id(3),
                200_000L, 2, BazaarTimeInForce.IMMEDIATE_OR_CANCEL,
                rules, 2L, 0L);

        assertEquals(BazaarOperationStatus.APPLIED, buy.status());
        assertEquals(1, buy.fills().size());
        assertEquals(first.orderId(), buy.fills().get(0).sellOrderId());
        assertEquals(BazaarOrderState.CANCELLED,
                buy.order().orElseThrow().state());
        assertEquals(1, buy.order().orElseThrow().remainingQuantity());
        assertTrue(buy.settlements().stream().anyMatch(settlement ->
                settlement.kind()
                        == BazaarSettlementKind.BUYER_REFUND_CLAIM));
        assertEquals(BazaarOrderState.FROZEN,
                book.order(second.orderId()).orElseThrow().state());
    }

    @Test
    void ownerCancellationHonorsRevisionAndMinimumLifetime() {
        BazaarRuleSnapshot rules = rules(BazaarSelfTradePolicy.CANCEL_TAKER,
                BazaarExecutionPricePolicy.MAKER, false, 5000, 1_000L);
        BazaarOperationResult created = create(BazaarOrderSide.SELL, id(1), 100_000L, 2,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules, 100L, 0L);
        UUID orderId = created.orderId();

        BazaarOperationResult early = book.cancel(new CancelBazaarOrderCommand(
                id(70), orderId, id(1), BazaarIds.terminal(id(70), orderId,
                BazaarOperationType.CANCEL), 0L, 1_099L));
        BazaarOperationResult wrongRevision = book.cancel(new CancelBazaarOrderCommand(
                id(72), orderId, id(1), BazaarIds.terminal(id(72), orderId,
                BazaarOperationType.CANCEL), 9L, 1_100L));
        BazaarOperationResult cancelled = book.cancel(new CancelBazaarOrderCommand(
                id(74), orderId, id(1), BazaarIds.terminal(id(74), orderId,
                BazaarOperationType.CANCEL), 0L, 1_100L));

        assertEquals(BazaarOperationStatus.INVALID_ORDER, early.status());
        assertEquals(BazaarOperationStatus.REVISION_CHANGED, wrongRevision.status());
        assertEquals(BazaarOrderState.CANCELLED, cancelled.order().orElseThrow().state());
        assertTrue(cancelled.settlements().stream().anyMatch(settlement ->
                settlement.kind() == BazaarSettlementKind.SELLER_ITEM_REFUND_CLAIM));
    }

    @Test
    void timedOrderExpiresOnlyAtItsDeadline() {
        BazaarOperationResult created = create(BazaarOrderSide.BUY, id(1), 100_000L, 2,
                BazaarTimeInForce.GOOD_UNTIL_TIME, standardRules(), 100L, 1_000L);
        BazaarOperationResult early = book.expire(new ExpireBazaarOrderCommand(
                id(80), created.orderId(), BazaarIds.terminal(id(80),
                created.orderId(), BazaarOperationType.EXPIRE), 0L, 999L));
        BazaarOperationResult expired = book.expire(new ExpireBazaarOrderCommand(
                id(82), created.orderId(), BazaarIds.terminal(id(82),
                created.orderId(), BazaarOperationType.EXPIRE), 0L, 1_000L));

        assertEquals(BazaarOperationStatus.INVALID_ORDER, early.status());
        assertEquals(BazaarOrderState.EXPIRED, expired.order().orElseThrow().state());
    }

    @Test
    void productVersionAndStatusAreServerAuthoritative() {
        CreateBazaarOrderCommand stale = command(id(90), id(91), BazaarOrderSide.BUY,
                id(1), 100_000L, 1, BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                standardRules(), 0L, 0L);
        stale = new CreateBazaarOrderCommand(stale.requestId(), stale.orderId(), stale.ownerId(),
                stale.activationTransactionId(), stale.moneyHoldAccountId(), stale.custodyLotId(),
                stale.productId(), 2L, stale.side(), stale.type(), stale.timeInForce(),
                stale.limitPriceMinor(), stale.quantity(), stale.createdAtMillis(),
                stale.expiresAtMillis(), stale.rules());
        assertEquals(BazaarOperationStatus.PRODUCT_VERSION_CHANGED, book.create(stale).status());

        book.setProductStatus("iron", BazaarProductStatus.RETIRED);
        assertEquals(BazaarOperationStatus.PRODUCT_UNAVAILABLE,
                create(BazaarOrderSide.BUY, id(1), 100_000L, 1,
                        BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                        standardRules(), 1L, 0L).status());
    }

    @Test
    void midpointExecutionRoundsToTheProductTickInsideTheSpread() {
        book = new BazaarOrderBook();
        book.registerProduct(new BazaarProduct("iron", 1L, "minecraft:iron_ingot", "",
                "ores", 1, 10L, 10L, 1_000_000_000L, 1_000_000,
                BazaarProductStatus.ACTIVE));
        BazaarRuleSnapshot rules = rules(BazaarSelfTradePolicy.CANCEL_TAKER,
                BazaarExecutionPricePolicy.MIDPOINT, false, 5000, 0L);
        create(BazaarOrderSide.SELL, id(1), 110L, 1,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules, 0L, 0L);
        BazaarOperationResult buy = create(BazaarOrderSide.BUY, id(2), 150L, 1,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules, 1L, 0L);

        assertEquals(130L, buy.fills().get(0).priceMinor());
    }

    @Test
    void settlementKindsRejectCrossAssetAndMissingReferenceShapes() {
        assertThrows(IllegalArgumentException.class, () ->
                new BazaarEscrowSettlement(id(1), id(2), Optional.of(id(3)),
                        BazaarSettlementKind.BUYER_ITEM_CLAIM, 1L, 1,
                        Optional.of(id(4)), Optional.of(id(5))));
        assertThrows(IllegalArgumentException.class, () ->
                new BazaarEscrowSettlement(id(1), id(2), Optional.of(id(3)),
                        BazaarSettlementKind.SELLER_MONEY_CLAIM, 1L, 0,
                        Optional.empty(), Optional.of(id(5))));
        assertThrows(IllegalArgumentException.class, () ->
                new BazaarEscrowSettlement(id(1), id(2), Optional.of(id(3)),
                        BazaarSettlementKind.FEE_DESTINATION, 1L, 0,
                        Optional.empty(), Optional.of(id(5))));
        assertThrows(IllegalArgumentException.class, () ->
                new BazaarEscrowSettlement(id(1), id(2), Optional.empty(),
                        BazaarSettlementKind.BUYER_REFUND_CLAIM, 1L, 0,
                        Optional.empty(), Optional.empty()));
    }

    @Test
    void escrowIdentitiesCannotCrossSemanticRoles() {
        CreateBazaarOrderCommand first = command(id(300), id(301),
                BazaarOrderSide.SELL, id(1), 100_000L, 1,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                standardRules(), 0L, 0L);
        assertTrue(book.create(first).newlyCommitted());

        CreateBazaarOrderCommand colliding = command(id(302), id(303),
                BazaarOrderSide.BUY, id(2), 100_000L, 1,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                standardRules(), 1L, 0L);
        colliding = new CreateBazaarOrderCommand(
                colliding.requestId(), colliding.orderId(),
                colliding.ownerId(), first.custodyLotId().orElseThrow(),
                colliding.moneyHoldAccountId(), colliding.custodyLotId(),
                colliding.productId(), colliding.productVersion(),
                colliding.side(), colliding.type(),
                colliding.timeInForce(), colliding.limitPriceMinor(),
                colliding.quantity(), colliding.createdAtMillis(),
                colliding.expiresAtMillis(), colliding.rules());
        BazaarOperationResult rejected = book.create(colliding);
        assertEquals(BazaarOperationStatus.INVALID_ORDER,
                rejected.status());

        CreateBazaarOrderCommand requestCollision = command(
                first.orderId(), id(304), BazaarOrderSide.BUY, id(2),
                100_000L, 1, BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                standardRules(), 2L, 0L);
        assertEquals(BazaarOperationStatus.REQUEST_CONFLICT,
                book.create(requestCollision).status());
    }

    private BazaarOperationResult create(BazaarOrderSide side, UUID owner, long price,
                                          int quantity, BazaarTimeInForce timeInForce,
                                          BazaarRuleSnapshot rules, long createdAt,
                                          long expiresAt) {
        book.setEffectiveRules(rules);
        UUID request = next();
        UUID order = next();
        return book.create(command(request, order, side, owner, price, quantity,
                timeInForce, rules, createdAt, expiresAt));
    }

    private CreateBazaarOrderCommand command(UUID request, UUID order,
                                             BazaarOrderSide side, UUID owner,
                                             long price, int quantity,
                                             BazaarTimeInForce timeInForce,
                                             BazaarRuleSnapshot rules,
                                             long createdAt, long expiresAt) {
        UUID activation = next();
        Optional<UUID> money = side == BazaarOrderSide.BUY
                ? Optional.of(next()) : Optional.empty();
        Optional<UUID> custody = side == BazaarOrderSide.SELL
                ? Optional.of(next()) : Optional.empty();
        BazaarOrderType type = timeInForce.rests()
                ? BazaarOrderType.LIMIT : BazaarOrderType.INSTANT;
        return new CreateBazaarOrderCommand(request, order, owner, activation,
                money, custody, "iron", 1L, side, type, timeInForce, price,
                quantity, createdAt, expiresAt, rules);
    }

    private static BazaarProduct product(long version, BazaarProductStatus status) {
        return new BazaarProduct("iron", version, "minecraft:iron_ingot", "",
                "ores", 1, 1L, 1L, 1_000_000_000L, 1_000_000, status);
    }

    private static BazaarRuleSnapshot standardRules() {
        return rules(BazaarSelfTradePolicy.CANCEL_TAKER,
                BazaarExecutionPricePolicy.MAKER, false, 5000, 0L);
    }

    private static BazaarRuleSnapshot rules(BazaarSelfTradePolicy selfTrade,
                                             BazaarExecutionPricePolicy price,
                                             boolean circuitBreaker,
                                             int priceBand,
                                             long minimumLifetime) {
        return new BazaarRuleSnapshot(10, 25, 1_000_000,
                1_000_000_000_000_000L, 32, 8,
                5_000_000_000_000_000L, selfTrade, price,
                circuitBreaker, priceBand, minimumLifetime, 1L);
    }

    private UUID next() {
        return id(nextId++);
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }
}
