package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.stock.StockSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSavedData;
import com.enviouse.futureshops.server.market.bazaar.BazaarLifecycleCommand;
import com.enviouse.futureshops.server.market.bazaar.BazaarMutation;
import com.enviouse.futureshops.server.market.bazaar.BazaarMutationCodec;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderState;
import com.enviouse.futureshops.server.market.bazaar.BazaarSavedData;
import com.enviouse.futureshops.server.market.bazaar.BazaarTimeInForce;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarCreateEscrowIntent;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLedgerAccounts;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecycleEventCodec;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowLifecyclePlanner;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarEscrowTestFixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarEscrowLifecycleWalIntegrationTest {
    private static final long STARTING_BALANCE = 10_000_000L;

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void buyReserveCommitsAtomicallyAndReplaysExactly(
            @TempDir Path directory
    ) throws Exception {
        Path journal = directory.resolve("bazaar.buy.lifecycle.wal");
        Fixture fixture = fixture(new EscrowRuntimeSavedData());
        EscrowRuntimeCoordinator coordinator = coordinator(journal,
                fixture);
        assertEquals(EscrowRuntimeState.READY, coordinator.start());
        seedCatalog(coordinator, fixture);

        BazaarCreateEscrowIntent intent =
                BazaarEscrowTestFixtures.buy(400L,
                        BazaarEscrowTestFixtures.id(40L), 2, 100_000L,
                        BazaarTimeInForce.GOOD_UNTIL_CANCELLED,
                        BazaarEscrowTestFixtures.rules());
        seedWallet(coordinator, intent.command().ownerId());
        BazaarEscrowLifecycleEvent.Prepare prepare =
                new BazaarEscrowLifecycleEvent.Prepare(intent);
        assertFalse(coordinator.commitBazaarEscrowLifecycle(
                intent.requestId(), event(prepare)).replayed());

        var planned = BazaarEscrowLifecyclePlanner.commitCreate(
                fixture.bazaar().snapshot(),
                fixture.bazaar().escrowLifecycleSnapshot(), intent,
                Optional.empty(), BazaarEscrowTestFixtures.NOW);
        BazaarEscrowLifecycleEvent.Commit complete =
                new BazaarEscrowLifecycleEvent.Commit(
                        Optional.of(planned.terminalIntent()),
                        planned.commit());
        assertFalse(coordinator.commitBazaarEscrowLifecycle(
                intent.requestId(), event(complete)).replayed());

        long committedBytes = Files.size(journal);
        assertTrue(coordinator.commitBazaarEscrowLifecycle(
                intent.requestId(), event(complete)).replayed());
        assertEquals(committedBytes, Files.size(journal));
        assertEquals(BazaarOrderState.OPEN,
                fixture.bazaar().order(intent.orderId()).state());
        assertTrue(fixture.bazaar().escrowLifecycleSnapshot()
                .activeBackings().containsKey(intent.orderId()));
        long reserve = BazaarCreateEscrowIntent.initialReserve(
                intent.command());
        assertEquals(STARTING_BALANCE - reserve,
                fixture.ledger().balance(
                        BazaarEscrowLedgerAccounts.wallet(
                                intent.command().ownerId())));
        assertEquals(reserve, fixture.ledger().balance(
                BazaarEscrowLedgerAccounts.hold(
                        intent.buyFunding().orElseThrow().holdAccountId())));
        coordinator.stop();
    }

    @Test
    void sellCreationRequiresDurableExactItemCustody(
            @TempDir Path directory
    ) {
        Path journal = directory.resolve("bazaar.sell.lifecycle.wal");
        Fixture fixture = fixture(new EscrowRuntimeSavedData());
        EscrowRuntimeCoordinator coordinator = coordinator(journal,
                fixture);
        assertEquals(EscrowRuntimeState.READY, coordinator.start());
        seedCatalog(coordinator, fixture);
        var sell = BazaarEscrowTestFixtures.sell(800L,
                BazaarEscrowTestFixtures.id(80L), 3, 90_000L,
                BazaarEscrowTestFixtures.rules());
        BazaarEscrowLifecycleEvent.Prepare prepare =
                new BazaarEscrowLifecycleEvent.Prepare(sell.intent());
        coordinator.commitBazaarEscrowLifecycle(
                sell.intent().requestId(), event(prepare));
        var planned = BazaarEscrowLifecyclePlanner.commitCreate(
                fixture.bazaar().snapshot(),
                fixture.bazaar().escrowLifecycleSnapshot(),
                sell.intent(), Optional.of(sell.receipt()),
                BazaarEscrowTestFixtures.NOW);
        BazaarEscrowLifecycleEvent.Commit complete =
                new BazaarEscrowLifecycleEvent.Commit(
                        Optional.of(planned.terminalIntent()),
                        planned.commit());
        assertThrows(EscrowRuntimeException.class,
                () -> fixture.applier().preflight(
                        sell.intent().requestId(), event(complete)));

        EscrowItemInventoryMutationGateway itemGateway =
                new EscrowItemInventoryMutationGateway(coordinator,
                        fixture.itemJournal(), fixture.claims(),
                        () -> true);
        itemGateway.appendPreparedDurably(
                sell.intent().itemMutationIntent().orElseThrow());
        itemGateway.appendCommittedDurably(sell.receipt());
        assertFalse(coordinator.commitBazaarEscrowLifecycle(
                sell.intent().requestId(), event(complete)).replayed());
        assertEquals(BazaarOrderState.OPEN,
                fixture.bazaar().order(sell.intent().orderId()).state());
        assertEquals(3, fixture.bazaar().escrowLifecycleSnapshot()
                .activeBackings().get(sell.intent().orderId())
                .sellCustody().orElseThrow().remainingQuantity());
        coordinator.stop();
    }

    private static EscrowRuntimeCoordinator coordinator(
            Path journal,
            Fixture fixture
    ) {
        return new EscrowRuntimeCoordinator(journal, fixture.cursor(),
                fixture.applier(), fixture::hasMaterializedState);
    }

    private static Fixture fixture(EscrowRuntimeSavedData cursor) {
        EscrowTransactionSavedData transactions =
                new EscrowTransactionSavedData();
        LedgerSavedData ledger = new LedgerSavedData();
        ClaimSavedData claims = new ClaimSavedData();
        ItemInventoryJournalSavedData itemJournal =
                new ItemInventoryJournalSavedData();
        BazaarSavedData bazaar = new BazaarSavedData();
        EscrowSavedDataMutationApplier applier =
                new EscrowSavedDataMutationApplier(transactions,
                        ledger, claims,
                        new EscrowAdministrativeAuditSavedData(),
                        new CustodySavedData(),
                        new ProtectedMintSavedData(),
                        new StockSavedData(), itemJournal,
                        new AuctionHouseSavedData(), bazaar,
                        new ServerShopIntentSavedData(),
                        MaintenanceRuntimeMutationHandler.unavailable(),
                        AtmWithdrawalApplyFaultInjector.NONE, null);
        return new Fixture(cursor, transactions, ledger, claims,
                itemJournal, bazaar, applier);
    }

    private static void seedCatalog(
            EscrowRuntimeCoordinator coordinator,
            Fixture fixture
    ) {
        BazaarLifecycleCommand rules =
                BazaarLifecycleCommand.setEffectiveRules(
                        BazaarEscrowTestFixtures.id(900L),
                        BazaarEscrowTestFixtures.rules());
        commitMutation(coordinator,
                BazaarMutation.lifecycle(fixture.bazaar().snapshot(),
                        rules));
        BazaarLifecycleCommand product =
                BazaarLifecycleCommand.registerProduct(
                        BazaarEscrowTestFixtures.id(901L),
                        BazaarEscrowTestFixtures.product());
        commitMutation(coordinator,
                BazaarMutation.lifecycle(fixture.bazaar().snapshot(),
                        product));
    }

    private static void seedWallet(
            EscrowRuntimeCoordinator coordinator,
            UUID ownerId
    ) {
        UUID transactionId = BazaarEscrowTestFixtures.id(902L);
        LedgerTransaction transaction = new LedgerTransaction(
                transactionId, "bazaar.test.seed", "Bazaar test seed",
                List.of(new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.ADMIN_SOURCE,
                                "bazaar.test"),
                                Math.negateExact(STARTING_BALANCE)),
                        new LedgerLeg(BazaarEscrowLedgerAccounts.wallet(
                                ownerId), STARTING_BALANCE)));
        assertFalse(coordinator.commit(transactionId,
                new EscrowJournalEvent(
                        EscrowJournalEventType.LEDGER_APPLY,
                        LedgerJournalCodec.encode(transaction))).replayed());
    }

    private static void commitMutation(
            EscrowRuntimeCoordinator coordinator,
            BazaarMutation mutation
    ) {
        assertFalse(coordinator.commitBazaarMutation(
                mutation.mutationId(), new EscrowJournalEvent(
                        EscrowJournalEventType.BAZAAR_MUTATION,
                        BazaarMutationCodec.encode(mutation))).replayed());
    }

    private static EscrowJournalEvent event(
            BazaarEscrowLifecycleEvent event
    ) {
        return new EscrowJournalEvent(
                EscrowJournalEventType.BAZAAR_ESCROW_LIFECYCLE,
                BazaarEscrowLifecycleEventCodec.encode(event));
    }

    private record Fixture(
            EscrowRuntimeSavedData cursor,
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            ItemInventoryJournalSavedData itemJournal,
            BazaarSavedData bazaar,
            EscrowSavedDataMutationApplier applier
    ) {
        private boolean hasMaterializedState() {
            return transactions.hasMaterializedState()
                    || ledger.hasMaterializedState()
                    || claims.hasMaterializedState()
                    || itemJournal.hasMaterializedState()
                    || bazaar.hasMaterializedState();
        }
    }
}
