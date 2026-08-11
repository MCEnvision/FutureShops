package com.enviouse.futureshops.server.market.bazaar.escrow;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.market.bazaar.BazaarOperationStatus;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBookSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarSettlementKind;
import com.enviouse.futureshops.server.market.bazaar.BazaarTimeInForce;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBookSnapshotCodec;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarEscrowLifecycleTest {
    @Test
    void sellThenBuyFillConservesEveryClaimAndBacking() {
        var rules = BazaarEscrowTestFixtures.rules();
        BazaarOrderBookSnapshot book =
                BazaarEscrowTestFixtures.baseSnapshot(rules);
        BazaarEscrowLifecycleState state =
                BazaarEscrowLifecycleState.empty();

        var sell = BazaarEscrowTestFixtures.sell(100L,
                BazaarEscrowTestFixtures.id(10L), 2, 90_000L, rules);
        var preparedSell = BazaarEscrowLifecycleRepository.apply(book,
                state, new BazaarEscrowLifecycleEvent.Prepare(
                        sell.intent()));
        var plannedSell = BazaarEscrowLifecyclePlanner.commitCreate(
                preparedSell.orderBook(), preparedSell.lifecycle(),
                sell.intent(), Optional.of(sell.receipt()),
                BazaarEscrowTestFixtures.NOW);
        var committedSell = BazaarEscrowLifecycleRepository.apply(
                preparedSell.orderBook(), preparedSell.lifecycle(),
                new BazaarEscrowLifecycleEvent.Commit(
                        Optional.of(plannedSell.terminalIntent()),
                        plannedSell.commit()));
        assertEquals(2, committedSell.lifecycle().activeBackings()
                .get(sell.intent().orderId()).sellCustody().orElseThrow()
                .remainingQuantity());

        var buy = BazaarEscrowTestFixtures.buy(200L,
                BazaarEscrowTestFixtures.id(20L), 1, 100_000L,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules);
        var preparedBuy = BazaarEscrowLifecycleRepository.apply(
                committedSell.orderBook(), committedSell.lifecycle(),
                new BazaarEscrowLifecycleEvent.Prepare(buy));
        var plannedBuy = BazaarEscrowLifecyclePlanner.commitCreate(
                preparedBuy.orderBook(), preparedBuy.lifecycle(), buy,
                Optional.empty(), BazaarEscrowTestFixtures.NOW.plusSeconds(1));
        assertEquals(BazaarOperationStatus.APPLIED,
                plannedBuy.commit().bookMutation().requestReceipt()
                        .orElseThrow().result().status());
        assertTrue(plannedBuy.commit().bookMutation().requestReceipt()
                .orElseThrow().result().settlements().stream().anyMatch(
                        value -> value.kind()
                                == BazaarSettlementKind
                                .BUYER_CHANGE_CLAIM));
        assertTrue(plannedBuy.commit().claims().stream().anyMatch(
                value -> value.kind() == ClaimKind.ITEM));
        assertEquals(2, plannedBuy.commit().ledgerTransactions().size());

        var committedBuy = BazaarEscrowLifecycleRepository.apply(
                preparedBuy.orderBook(), preparedBuy.lifecycle(),
                new BazaarEscrowLifecycleEvent.Commit(
                        Optional.of(plannedBuy.terminalIntent()),
                        plannedBuy.commit()));
        assertFalse(committedBuy.lifecycle().activeBackings().containsKey(
                buy.orderId()));
        assertEquals(1, committedBuy.lifecycle().activeBackings()
                .get(sell.intent().orderId()).sellCustody().orElseThrow()
                .remainingQuantity());
    }

    @Test
    void fillOrKillRejectionNeverDebitsTheWallet() {
        var rules = BazaarEscrowTestFixtures.rules();
        BazaarOrderBookSnapshot book =
                BazaarEscrowTestFixtures.baseSnapshot(rules);
        var buy = BazaarEscrowTestFixtures.buy(300L,
                BazaarEscrowTestFixtures.id(30L), 4, 100_000L,
                BazaarTimeInForce.FILL_OR_KILL, rules);
        var prepared = BazaarEscrowLifecycleRepository.apply(book,
                BazaarEscrowLifecycleState.empty(),
                new BazaarEscrowLifecycleEvent.Prepare(buy));
        var planned = BazaarEscrowLifecyclePlanner.commitCreate(
                prepared.orderBook(), prepared.lifecycle(), buy,
                Optional.empty(), BazaarEscrowTestFixtures.NOW);

        assertEquals(BazaarOperationStatus.FILL_OR_KILL_UNAVAILABLE,
                planned.commit().bookMutation().requestReceipt()
                        .orElseThrow().result().status());
        assertTrue(planned.commit().ledgerTransactions().isEmpty());
        assertTrue(planned.commit().claims().isEmpty());
        assertTrue(planned.commit().orderTransitions().isEmpty());
        assertEquals(BazaarCreateEscrowIntent.Status.REJECTED,
                planned.terminalIntent().status());
    }

    @Test
    void codecsAndColdStateReplayAreCanonical() {
        var rules = BazaarEscrowTestFixtures.rules();
        BazaarOrderBookSnapshot book =
                BazaarEscrowTestFixtures.baseSnapshot(rules);
        var buy = BazaarEscrowTestFixtures.buy(400L,
                BazaarEscrowTestFixtures.id(40L), 2, 100_000L,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules);
        var prepared = BazaarEscrowLifecycleRepository.apply(book,
                BazaarEscrowLifecycleState.empty(),
                new BazaarEscrowLifecycleEvent.Prepare(buy));
        var planned = BazaarEscrowLifecyclePlanner.commitCreate(
                prepared.orderBook(), prepared.lifecycle(), buy,
                Optional.empty(), BazaarEscrowTestFixtures.NOW);
        var event = new BazaarEscrowLifecycleEvent.Commit(
                Optional.of(planned.terminalIntent()), planned.commit());

        assertEquals(planned.commit(), BazaarEscrowCommitCodec.decode(
                BazaarEscrowCommitCodec.encode(planned.commit())));
        assertEquals(event, BazaarEscrowLifecycleEventCodec.decode(
                BazaarEscrowLifecycleEventCodec.encode(event)));
        var committed = BazaarEscrowLifecycleRepository.apply(
                prepared.orderBook(), prepared.lifecycle(), event);
        byte[] stateBytes = BazaarEscrowLifecycleStateCodec.encode(
                committed.lifecycle());
        assertArrayEquals(stateBytes,
                BazaarEscrowLifecycleStateCodec.encode(
                        BazaarEscrowLifecycleStateCodec.decode(stateBytes)));
        byte[] bookBytes = BazaarOrderBookSnapshotCodec.encode(
                committed.orderBook());
        BazaarEscrowLifecycleRepository.validateAgainst(
                BazaarOrderBookSnapshotCodec.decode(bookBytes),
                BazaarEscrowLifecycleStateCodec.decode(stateBytes));
        assertTrue(BazaarEscrowLifecycleRepository.apply(
                committed.orderBook(), committed.lifecycle(), event)
                .replayed());
    }

    @Test
    void prepareIdentityConflictsFailClosed() {
        var rules = BazaarEscrowTestFixtures.rules();
        BazaarOrderBookSnapshot book =
                BazaarEscrowTestFixtures.baseSnapshot(rules);
        var first = BazaarEscrowTestFixtures.buy(500L,
                BazaarEscrowTestFixtures.id(50L), 1, 100_000L,
                BazaarTimeInForce.GOOD_UNTIL_CANCELLED, rules);
        var prepared = BazaarEscrowLifecycleRepository.apply(book,
                BazaarEscrowLifecycleState.empty(),
                new BazaarEscrowLifecycleEvent.Prepare(first));
        var changed = new BazaarBuyFundingEvidence(first.requestId(),
                first.orderId(), first.command().ownerId(),
                first.command().moneyHoldAccountId().orElseThrow(),
                BazaarEscrowPaymentSource.WALLET,
                new BazaarEscrowWalletSnapshot(first.command().ownerId(),
                        20_000_000L, 7L), Optional.empty(),
                BazaarEscrowTestFixtures.CURRENCY);
        var conflict = BazaarCreateEscrowIntent.preparedBuy(
                first.command(), changed, first.preparedAt());

        assertThrows(BazaarEscrowLifecycleConflictException.class,
                () -> BazaarEscrowLifecycleRepository.apply(
                        prepared.orderBook(), prepared.lifecycle(),
                        new BazaarEscrowLifecycleEvent.Prepare(conflict)));
    }
}
