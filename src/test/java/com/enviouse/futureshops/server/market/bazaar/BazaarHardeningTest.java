package com.enviouse.futureshops.server.market.bazaar;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarHardeningTest {
    @Test
    void callerCannotChooseFeesOrAnOldRuleRevision() {
        BazaarOrderBook book = book(rules(1L, 10, 25));
        BazaarRuleSnapshot forged = rules(1L, 0, 0);
        BazaarOperationResult rejected = book.create(command(id(1), id(2),
                id(3), BazaarOrderSide.BUY, Optional.of(id(4)),
                Optional.empty(), 1L, 100L, 2, forged));

        assertEquals(BazaarOperationStatus.RULE_REVISION_CHANGED,
                rejected.status());
        assertTrue(book.order(id(2)).isEmpty());

        BazaarRuleSnapshot revisionTwo = rules(2L, 20, 40);
        book.setEffectiveRules(revisionTwo);
        BazaarOperationResult applied = book.create(command(id(5), id(6),
                id(7), BazaarOrderSide.BUY, Optional.of(id(8)),
                Optional.empty(), 1L, 100L, 2, revisionTwo));

        assertTrue(applied.newlyCommitted());
        assertEquals(revisionTwo, applied.order().orElseThrow().rules());
        assertEquals(2L, book.snapshot().effectiveRuleRevision());
    }

    @Test
    void productVersionsRemainExactAndOldOrdersStayFrozen() {
        BazaarRuleSnapshot rules = rules(1L, 10, 25);
        BazaarOrderBook book = book(rules);
        BazaarOperationResult old = book.create(command(id(10), id(11),
                id(12), BazaarOrderSide.SELL, Optional.empty(),
                Optional.of(id(13)), 1L, 100L, 2, rules));
        book.registerProduct(new BazaarProduct("iron", 2L,
                "minecraft:raw_iron", "component_v2", "ores", 2,
                5L, 5L, 1_000_000L, 10_000,
                BazaarProductStatus.ACTIVE));

        assertEquals(BazaarOrderState.FROZEN,
                book.order(old.orderId()).orElseThrow().state());
        assertEquals(2, book.snapshot().products().size());
        BazaarOrderBook restored = BazaarOrderBook.restore(book.snapshot());
        assertEquals(1L, restored.order(old.orderId()).orElseThrow()
                .productVersion());
        assertEquals("minecraft:iron_ingot", restored.productVersion("iron", 1L)
                .orElseThrow().registryId());
        assertEquals(2L, restored.product("iron").orElseThrow().version());
    }

    @Test
    void snapshotRejectsAReceiptWithForgedSettlementAmount() {
        BazaarOrderBook book = matchedBook();
        BazaarOrderBookSnapshot snapshot = book.snapshot();
        Map<UUID, BazaarRequestReceipt> receipts = new LinkedHashMap<>(
                snapshot.receipts());
        BazaarRequestReceipt original = receipts.values().stream()
                .filter(receipt -> receipt.result().fills().size() == 1)
                .findFirst().orElseThrow();
        BazaarOperationResult result = original.result();
        List<BazaarEscrowSettlement> settlements = new ArrayList<>(
                result.settlements());
        BazaarEscrowSettlement first = settlements.get(0);
        settlements.set(0, new BazaarEscrowSettlement(first.transactionId(),
                first.ownerId(), first.orderId(), first.kind(), first.moneyMinor(),
                Math.addExact(first.itemQuantity(), 1), first.counterpartyId(),
                first.fillId()));
        BazaarOperationResult forged = new BazaarOperationResult(
                result.requestId(), result.orderId(), result.operation(),
                result.status(), false, result.observedRevision(), result.order(),
                result.fills(), settlements, result.cancelledMakerOrderIds(),
                result.productHalted());
        BazaarRequestReceipt forgedReceipt = BazaarRequestReceipt.create(
                original.fingerprint(), original.createCommand().orElseThrow(),
                forged);
        receipts.put(result.requestId(), forgedReceipt);

        assertThrows(IllegalArgumentException.class, () ->
                BazaarSnapshotValidator.validate(copy(snapshot,
                        snapshot.products(), snapshot.fills(), receipts,
                        snapshot.ruleSnapshots())));
    }

    @Test
    void snapshotRejectsWrongMakerPriceAndFillTopology() {
        BazaarOrderBook book = matchedBook();
        BazaarOrderBookSnapshot snapshot = book.snapshot();
        BazaarFill fill = snapshot.fills().get(0);
        BazaarFill wrongPrice = new BazaarFill(fill.fillId(), fill.buyOrderId(),
                fill.sellOrderId(), fill.makerOrderId(), fill.takerOrderId(),
                fill.productId(), fill.productVersion(), fill.quantity(),
                Math.addExact(fill.priceMinor(), 1L), Math.multiplyExact(
                Math.addExact(fill.priceMinor(), 1L), fill.quantity()),
                fill.buyerFeeMinor(), fill.sellerFeeMinor(),
                fill.buyerPriceImprovementMinor(), fill.sequence(),
                fill.filledAtMillis(), fill.settlementTransactionId());
        BazaarFill swappedRoles = new BazaarFill(fill.fillId(),
                fill.buyOrderId(), fill.sellOrderId(), fill.takerOrderId(),
                fill.makerOrderId(), fill.productId(), fill.productVersion(),
                fill.quantity(), fill.priceMinor(), fill.grossMinor(),
                fill.buyerFeeMinor(), fill.sellerFeeMinor(),
                fill.buyerPriceImprovementMinor(), fill.sequence(),
                fill.filledAtMillis(), fill.settlementTransactionId());

        assertThrows(IllegalArgumentException.class, () ->
                BazaarSnapshotValidator.validate(copy(snapshot,
                        snapshot.products(), List.of(wrongPrice),
                        snapshot.receipts(), snapshot.ruleSnapshots())));
        assertThrows(IllegalArgumentException.class, () ->
                BazaarSnapshotValidator.validate(copy(snapshot,
                        snapshot.products(), List.of(swappedRoles),
                        snapshot.receipts(), snapshot.ruleSnapshots())));
    }

    @Test
    void snapshotRequiresHistoricalProductsAndTrustedRules() {
        BazaarRuleSnapshot rules = rules(1L, 10, 25);
        BazaarOrderBook book = book(rules);
        book.create(command(id(30), id(31), id(32), BazaarOrderSide.SELL,
                Optional.empty(), Optional.of(id(33)), 1L, 100L, 2, rules));
        book.registerProduct(new BazaarProduct("iron", 2L,
                "minecraft:raw_iron", "", "ores", 1, 1L, 1L,
                1_000_000L, 10_000, BazaarProductStatus.ACTIVE));
        BazaarOrderBookSnapshot snapshot = book.snapshot();
        List<BazaarProduct> latestOnly = snapshot.products().stream()
                .filter(product -> product.version() == 2L).toList();

        assertThrows(IllegalArgumentException.class, () ->
                BazaarSnapshotValidator.validate(copy(snapshot, latestOnly,
                        snapshot.fills(), snapshot.receipts(),
                        snapshot.ruleSnapshots())));
        assertThrows(IllegalArgumentException.class, () ->
                BazaarSnapshotValidator.validate(new BazaarOrderBookSnapshot(
                        snapshot.schemaVersion(),
                        snapshot.nextSequence(), snapshot.products(),
                        snapshot.orders(), snapshot.fills(), snapshot.receipts(),
                        snapshot.referencePrices(), snapshot.terminalTransactions(),
                        List.of(), snapshot.effectiveRuleRevision(),
                        snapshot.retentionPolicy(),
                        snapshot.lifecycleReceipts())));
    }

    @Test
    void receiptCapacityFailsClosedAndSurvivesRestart() {
        BazaarRuleSnapshot rules = rules(1L, 10, 25);
        BazaarOrderBook book = new BazaarOrderBook(
                new BazaarRetentionPolicy(1, 4));
        book.registerProduct(product());
        book.setEffectiveRules(rules);
        BazaarOperationResult first = book.create(command(id(40), id(41),
                id(42), BazaarOrderSide.SELL, Optional.empty(),
                Optional.of(id(43)), 1L, 100L, 2, rules));
        CreateBazaarOrderCommand secondCommand = command(id(44), id(45),
                id(46), BazaarOrderSide.SELL, Optional.empty(),
                Optional.of(id(47)), 1L, 100L, 2, rules);
        BazaarOperationResult second = book.create(secondCommand);

        assertTrue(first.newlyCommitted());
        assertEquals(BazaarOperationStatus.RETENTION_LIMIT_REACHED,
                second.status());
        assertFalse(book.order(second.orderId()).isPresent());

        BazaarOrderBook restored = BazaarOrderBook.restore(book.snapshot());
        assertEquals(BazaarOperationStatus.RETENTION_LIMIT_REACHED,
                restored.create(secondCommand).status());
        assertEquals(book.snapshot(), restored.snapshot());
    }

    @Test
    void canonicalCancellationAndSelfTradeChainsRestore() {
        BazaarRuleSnapshot cancelMakerRules = rules(1L, 10, 25,
                BazaarSelfTradePolicy.CANCEL_MAKER);
        BazaarOrderBook selfTradeBook = book(cancelMakerRules);
        BazaarOperationResult self = selfTradeBook.create(command(id(50),
                id(51), id(52), BazaarOrderSide.SELL, Optional.empty(),
                Optional.of(id(53)), 1L, 90L, 2, cancelMakerRules));
        selfTradeBook.create(command(id(54), id(55), id(56),
                BazaarOrderSide.SELL, Optional.empty(), Optional.of(id(57)),
                1L, 100L, 2, cancelMakerRules));
        BazaarOperationResult taker = selfTradeBook.create(command(id(58),
                id(59), id(52), BazaarOrderSide.BUY, Optional.of(id(60)),
                Optional.empty(), 1L, 100L, 2, cancelMakerRules));

        assertEquals(List.of(self.orderId()),
                taker.cancelledMakerOrderIds());
        BazaarOrderBook.restore(selfTradeBook.snapshot());

        BazaarRuleSnapshot rules = rules(1L, 10, 25);
        BazaarOrderBook cancellationBook = book(rules);
        BazaarOperationResult created = cancellationBook.create(command(id(61),
                id(62), id(63), BazaarOrderSide.SELL, Optional.empty(),
                Optional.of(id(64)), 1L, 100L, 2, rules));
        UUID transaction = BazaarIds.terminal(id(65), created.orderId(),
                BazaarOperationType.CANCEL);
        BazaarOperationResult cancelled = cancellationBook.cancel(
                new CancelBazaarOrderCommand(id(65), created.orderId(), id(63),
                        transaction, created.observedRevision(), 1_000L));

        assertTrue(cancelled.newlyCommitted());
        BazaarOrderBook.restore(cancellationBook.snapshot());
    }

    @Test
    void terminalCapacityRejectsBeforeChangingTheOrder() {
        BazaarRuleSnapshot rules = rules(1L, 10, 25);
        BazaarOrderBook book = new BazaarOrderBook(
                new BazaarRetentionPolicy(10, 1));
        book.registerProduct(product());
        book.setEffectiveRules(rules);
        BazaarOperationResult first = book.create(command(id(70), id(71),
                id(72), BazaarOrderSide.SELL, Optional.empty(),
                Optional.of(id(73)), 1L, 100L, 2, rules));
        BazaarOperationResult second = book.create(command(id(74), id(75),
                id(76), BazaarOrderSide.SELL, Optional.empty(),
                Optional.of(id(77)), 1L, 100L, 2, rules));
        UUID firstTransaction = BazaarIds.terminal(id(78), first.orderId(),
                BazaarOperationType.CANCEL);
        UUID secondTransaction = BazaarIds.terminal(id(79), second.orderId(),
                BazaarOperationType.CANCEL);

        assertTrue(book.cancel(new CancelBazaarOrderCommand(id(78),
                first.orderId(), id(72), firstTransaction,
                first.observedRevision(), 1_000L)).newlyCommitted());
        BazaarOperationResult rejected = book.cancel(
                new CancelBazaarOrderCommand(id(79), second.orderId(), id(76),
                        secondTransaction, second.observedRevision(), 1_000L));

        assertEquals(BazaarOperationStatus.RETENTION_LIMIT_REACHED,
                rejected.status());
        assertEquals(BazaarOrderState.OPEN,
                book.order(second.orderId()).orElseThrow().state());
        BazaarOrderBook.restore(book.snapshot());
    }

    private static BazaarOrderBook matchedBook() {
        BazaarRuleSnapshot rules = rules(1L, 10, 25);
        BazaarOrderBook book = book(rules);
        book.create(command(id(100), id(101), id(102),
                BazaarOrderSide.SELL, Optional.empty(), Optional.of(id(103)),
                1L, 100L, 3, rules));
        book.create(command(id(104), id(105), id(106),
                BazaarOrderSide.BUY, Optional.of(id(107)), Optional.empty(),
                1L, 100L, 2, rules));
        return book;
    }

    private static BazaarOrderBook book(BazaarRuleSnapshot rules) {
        BazaarOrderBook book = new BazaarOrderBook();
        book.registerProduct(product());
        book.setEffectiveRules(rules);
        return book;
    }

    private static BazaarOrderBookSnapshot copy(
            BazaarOrderBookSnapshot snapshot,
            List<BazaarProduct> products,
            List<BazaarFill> fills,
            Map<UUID, BazaarRequestReceipt> receipts,
            List<BazaarRuleSnapshot> rules) {
        return new BazaarOrderBookSnapshot(snapshot.schemaVersion(),
                snapshot.nextSequence(), products, snapshot.orders(), fills,
                receipts, snapshot.referencePrices(),
                snapshot.terminalTransactions(), rules,
                snapshot.effectiveRuleRevision(), snapshot.retentionPolicy(),
                snapshot.lifecycleReceipts());
    }

    private static CreateBazaarOrderCommand command(
            UUID requestId,
            UUID orderId,
            UUID ownerId,
            BazaarOrderSide side,
            Optional<UUID> money,
            Optional<UUID> custody,
            long productVersion,
            long price,
            int quantity,
            BazaarRuleSnapshot rules) {
        return new CreateBazaarOrderCommand(requestId, orderId, ownerId,
                id(orderId.getLeastSignificantBits() + 1_000L), money, custody,
                "iron", productVersion, side, BazaarOrderType.LIMIT,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, price, quantity,
                orderId.getLeastSignificantBits(), 0L, rules);
    }

    private static BazaarProduct product() {
        return new BazaarProduct("iron", 1L, "minecraft:iron_ingot", "",
                "ores", 1, 1L, 1L, 1_000_000L, 10_000,
                BazaarProductStatus.ACTIVE);
    }

    private static BazaarRuleSnapshot rules(long revision, int makerFee,
                                             int takerFee) {
        return rules(revision, makerFee, takerFee,
                BazaarSelfTradePolicy.CANCEL_TAKER);
    }

    private static BazaarRuleSnapshot rules(long revision, int makerFee,
                                             int takerFee,
                                             BazaarSelfTradePolicy selfTrade) {
        return new BazaarRuleSnapshot(makerFee, takerFee, 10_000,
                10_000_000_000L, 100, 50, 100_000_000_000L,
                selfTrade,
                BazaarExecutionPricePolicy.MAKER, false, 5_000, 0L,
                revision);
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }
}
