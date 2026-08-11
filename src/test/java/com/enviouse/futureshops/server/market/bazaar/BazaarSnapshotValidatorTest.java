package com.enviouse.futureshops.server.market.bazaar;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarSnapshotValidatorTest {
    @Test
    void snapshotRestorePreservesBooksFillsReceiptsAndReplay() {
        BazaarOrderBook book = new BazaarOrderBook();
        book.registerProduct(product());
        book.setEffectiveRules(rules());
        CreateBazaarOrderCommand sell = command(id(1), id(2), id(3),
                BazaarOrderSide.SELL, 100_000L, 10, Optional.empty(), Optional.of(id(4)));
        CreateBazaarOrderCommand buy = command(id(5), id(6), id(7),
                BazaarOrderSide.BUY, 100_000L, 4, Optional.of(id(8)), Optional.empty());
        book.create(sell);
        BazaarOperationResult bought = book.create(buy);
        BazaarOrderBookSnapshot snapshot = book.snapshot();

        BazaarOrderBook restored = BazaarOrderBook.restore(snapshot);

        assertEquals(snapshot, restored.snapshot());
        assertEquals(bought.fills(), restored.fills());
        assertTrue(restored.create(buy).replayed());
        assertEquals(6, restored.order(sell.orderId()).orElseThrow().remainingQuantity());
    }

    @Test
    void snapshotRejectsFillFeeTampering() {
        BazaarOrderBook book = matchedBook();
        BazaarOrderBookSnapshot snapshot = book.snapshot();
        List<BazaarFill> fills = new ArrayList<>(snapshot.fills());
        BazaarFill fill = fills.get(0);
        fills.set(0, new BazaarFill(fill.fillId(), fill.buyOrderId(), fill.sellOrderId(),
                fill.makerOrderId(), fill.takerOrderId(), fill.productId(),
                fill.productVersion(), fill.quantity(), fill.priceMinor(), fill.grossMinor(),
                Math.addExact(fill.buyerFeeMinor(), 1L), fill.sellerFeeMinor(),
                fill.buyerPriceImprovementMinor(), fill.sequence(), fill.filledAtMillis(),
                fill.settlementTransactionId()));
        BazaarOrderBookSnapshot tampered = new BazaarOrderBookSnapshot(
                snapshot.schemaVersion(), snapshot.nextSequence(), snapshot.products(),
                snapshot.orders(), fills, snapshot.receipts(), snapshot.referencePrices(),
                snapshot.terminalTransactions(), snapshot.ruleSnapshots(),
                snapshot.effectiveRuleRevision(), snapshot.retentionPolicy(),
                snapshot.lifecycleReceipts());

        assertThrows(IllegalArgumentException.class,
                () -> BazaarSnapshotValidator.validate(tampered));
    }

    @Test
    void snapshotRejectsSequenceRollbackAndMissingProduct() {
        BazaarOrderBook book = matchedBook();
        BazaarOrderBookSnapshot snapshot = book.snapshot();
        BazaarOrderBookSnapshot badSequence = new BazaarOrderBookSnapshot(
                snapshot.schemaVersion(), 1L, snapshot.products(), snapshot.orders(),
                snapshot.fills(), snapshot.receipts(), snapshot.referencePrices(),
                snapshot.terminalTransactions(), snapshot.ruleSnapshots(),
                snapshot.effectiveRuleRevision(), snapshot.retentionPolicy(),
                snapshot.lifecycleReceipts());
        BazaarOrderBookSnapshot missingProduct = new BazaarOrderBookSnapshot(
                snapshot.schemaVersion(), snapshot.nextSequence(), List.of(), snapshot.orders(),
                snapshot.fills(), snapshot.receipts(), snapshot.referencePrices(),
                snapshot.terminalTransactions(), snapshot.ruleSnapshots(),
                snapshot.effectiveRuleRevision(), snapshot.retentionPolicy(),
                snapshot.lifecycleReceipts());

        assertThrows(IllegalArgumentException.class,
                () -> BazaarSnapshotValidator.validate(badSequence));
        assertThrows(IllegalArgumentException.class,
                () -> BazaarSnapshotValidator.validate(missingProduct));
    }

    private static BazaarOrderBook matchedBook() {
        BazaarOrderBook book = new BazaarOrderBook();
        book.registerProduct(product());
        book.setEffectiveRules(rules());
        book.create(command(id(10), id(11), id(12), BazaarOrderSide.SELL,
                100_000L, 3, Optional.empty(), Optional.of(id(13))));
        book.create(command(id(14), id(15), id(16), BazaarOrderSide.BUY,
                100_000L, 2, Optional.of(id(17)), Optional.empty()));
        return book;
    }

    private static CreateBazaarOrderCommand command(UUID request, UUID order, UUID owner,
                                                     BazaarOrderSide side, long price,
                                                     int quantity, Optional<UUID> money,
                                                     Optional<UUID> custody) {
        return new CreateBazaarOrderCommand(request, order, owner, id(order.getLeastSignificantBits() + 1000L),
                money, custody, "iron", 1L, side, BazaarOrderType.LIMIT,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, price, quantity, 0L, 0L, rules());
    }

    private static BazaarProduct product() {
        return new BazaarProduct("iron", 1L, "minecraft:iron_ingot", "", "ores",
                1, 1L, 1L, 1_000_000_000L, 1_000_000, BazaarProductStatus.ACTIVE);
    }

    private static BazaarRuleSnapshot rules() {
        return new BazaarRuleSnapshot(10, 25, 1_000_000,
                1_000_000_000_000_000L, 32, 8, 5_000_000_000_000_000L,
                BazaarSelfTradePolicy.CANCEL_TAKER, BazaarExecutionPricePolicy.MAKER,
                false, 5000, 0L, 1L);
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }
}
