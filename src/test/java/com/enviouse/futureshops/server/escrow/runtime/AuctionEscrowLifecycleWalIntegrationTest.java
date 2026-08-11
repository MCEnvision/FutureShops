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
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.stock.StockSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSavedData;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionListingState;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionCreateEscrowIntent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowCommit;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLedgerAccounts;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleEventCodec;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecyclePlanner;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowTestFixtures;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionEscrowLifecycleWalIntegrationTest {
    private static final long STARTING_BALANCE = 10_000L;

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void createRequiresCommittedCustodyAndReplaysExactly(
            @TempDir Path directory
    ) throws Exception {
        Path journal = directory.resolve("auction.lifecycle.wal");
        Fixture fixture = fixture(new EscrowRuntimeSavedData());
        EscrowRuntimeCoordinator coordinator = coordinator(journal,
                fixture);
        assertEquals(EscrowRuntimeState.READY, coordinator.start());
        seedSeller(coordinator);

        AuctionCreateEscrowIntent intent =
                AuctionEscrowTestFixtures.preparedCreate();
        AuctionEscrowLifecycleEvent.Prepare prepare =
                new AuctionEscrowLifecycleEvent.Prepare(intent);
        AuctionEscrowCommit commit = AuctionEscrowLifecyclePlanner
                .commitCreate(AuctionHouseSnapshot.empty(), intent,
                        intent.plannedCustody().receipt());
        AuctionEscrowLifecycleEvent.Commit complete =
                new AuctionEscrowLifecycleEvent.Commit(
                        Optional.of(intent.complete()), commit);
        EscrowJournalEvent prepareEvent = event(prepare);
        EscrowJournalEvent completeEvent = event(complete);

        assertThrows(IllegalArgumentException.class,
                () -> coordinator.commit(intent.requestId(),
                        prepareEvent));
        assertFalse(coordinator.commitAuctionEscrowLifecycle(
                intent.requestId(), prepareEvent).replayed());
        assertThrows(EscrowRuntimeException.class,
                () -> fixture.applier().preflight(
                        intent.requestId(), completeEvent));

        EscrowItemInventoryMutationGateway itemGateway =
                new EscrowItemInventoryMutationGateway(coordinator,
                        fixture.itemJournal(), fixture.claims(),
                        () -> true);
        assertFalse(itemGateway.appendPreparedDurably(
                intent.itemMutationIntent()).replayed());
        assertFalse(itemGateway.appendCommittedDurably(
                intent.plannedCustody().receipt()).replayed());
        assertFalse(coordinator.commitAuctionEscrowLifecycle(
                intent.requestId(), completeEvent).replayed());

        long committedBytes = Files.size(journal);
        assertTrue(coordinator.commitAuctionEscrowLifecycle(
                intent.requestId(), completeEvent).replayed());
        assertEquals(committedBytes, Files.size(journal));
        assertMaterialized(fixture, intent, commit);
        assertEquals(6L, fixture.cursor().lastAppliedSequence());
        coordinator.stop();
    }

    @Test
    void restartRebuildsColdStateAndAcceptsMaterializedReplay(
            @TempDir Path directory
    ) {
        Path journal = directory.resolve("auction.restart.wal");
        Fixture materialized = fixture(new EscrowRuntimeSavedData());
        EscrowRuntimeCoordinator first = coordinator(journal,
                materialized);
        assertEquals(EscrowRuntimeState.READY, first.start());
        seedSeller(first);
        AuctionCreateEscrowIntent intent =
                AuctionEscrowTestFixtures.preparedCreate();
        AuctionEscrowLifecycleEvent.Prepare prepare =
                new AuctionEscrowLifecycleEvent.Prepare(intent);
        AuctionEscrowCommit commit = AuctionEscrowLifecyclePlanner
                .commitCreate(AuctionHouseSnapshot.empty(), intent,
                        intent.plannedCustody().receipt());
        AuctionEscrowLifecycleEvent.Commit complete =
                new AuctionEscrowLifecycleEvent.Commit(
                        Optional.of(intent.complete()), commit);
        first.commitAuctionEscrowLifecycle(intent.requestId(),
                event(prepare));
        EscrowItemInventoryMutationGateway gateway =
                new EscrowItemInventoryMutationGateway(first,
                        materialized.itemJournal(), materialized.claims(),
                        () -> true);
        gateway.appendPreparedDurably(intent.itemMutationIntent());
        gateway.appendCommittedDurably(
                intent.plannedCustody().receipt());
        first.commitAuctionEscrowLifecycle(intent.requestId(),
                event(complete));
        UUID lineage = materialized.cursor().journalLineage()
                .orElseThrow();
        first.stop();

        Fixture replayed = materialized.withCursor(
                cursorAtLineage(lineage));
        EscrowRuntimeCoordinator partial = coordinator(journal, replayed);
        assertEquals(EscrowRuntimeState.READY, partial.start(),
                () -> partial.failure().map(Throwable::toString)
                        .orElse("No recovery failure"));
        assertEquals(6L, replayed.cursor().lastAppliedSequence());
        assertMaterialized(replayed, intent, commit);
        partial.stop();

        Fixture cold = fixture(cursorAtLineage(lineage));
        EscrowRuntimeCoordinator restarted = coordinator(journal, cold);
        assertEquals(EscrowRuntimeState.READY, restarted.start(),
                () -> restarted.failure().map(Throwable::toString)
                        .orElse("No recovery failure"));
        assertEquals(6L, cold.cursor().lastAppliedSequence());
        assertMaterialized(cold, intent, commit);
        restarted.stop();
    }

    private static void assertMaterialized(
            Fixture fixture,
            AuctionCreateEscrowIntent intent,
            AuctionEscrowCommit commit
    ) {
        assertEquals(AuctionListingState.ACTIVE,
                fixture.auctions().listing(intent.listingId()).state());
        assertEquals(intent.complete(), fixture.auctions().createIntent(
                intent.requestId()));
        assertEquals(commit, fixture.auctions().escrowCommit(
                intent.requestId()));
        assertEquals(intent.itemMutationIntent(),
                fixture.itemJournal().find(
                        intent.itemMutationIntent().token().requestId())
                        .orElseThrow().intent());
        assertEquals(STARTING_BALANCE
                        - intent.command().rules().listingFeeMinor(),
                fixture.ledger().balance(
                        AuctionEscrowLedgerAccounts.wallet(
                                intent.sellerId())));
        assertEquals(intent.command().rules().listingFeeMinor(),
                fixture.ledger().balance(
                        AuctionEscrowLedgerAccounts.fee()));
        assertNotNull(fixture.transactions().getTransaction(
                new EscrowTransactionId(intent.requestId())));
        assertEquals(commit.completedTransaction().orElseThrow(),
                fixture.transactions().getTransaction(
                        new EscrowTransactionId(intent.requestId())));
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
        AuctionHouseSavedData auctions = new AuctionHouseSavedData();
        EscrowSavedDataMutationApplier applier =
                new EscrowSavedDataMutationApplier(transactions,
                        ledger, claims,
                        new EscrowAdministrativeAuditSavedData(),
                        new CustodySavedData(),
                        new ProtectedMintSavedData(),
                        new StockSavedData(), itemJournal, auctions,
                        MaintenanceRuntimeMutationHandler.unavailable(),
                        AtmWithdrawalApplyFaultInjector.NONE, null);
        return new Fixture(cursor, transactions, ledger, claims,
                itemJournal, auctions, applier);
    }

    private static void seedSeller(
            EscrowRuntimeCoordinator coordinator
    ) {
        AuctionCreateEscrowIntent intent =
                AuctionEscrowTestFixtures.preparedCreate();
        UUID transactionId = AuctionEscrowTestFixtures.id(900);
        LedgerTransaction transaction = new LedgerTransaction(
                transactionId, "auction.test.seed", "Auction test seed",
                List.of(new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.ADMIN_SOURCE,
                                "auction.test"),
                                Math.negateExact(STARTING_BALANCE)),
                        new LedgerLeg(AuctionEscrowLedgerAccounts.wallet(
                                intent.sellerId()), STARTING_BALANCE)));
        assertFalse(coordinator.commit(transactionId,
                new EscrowJournalEvent(
                        EscrowJournalEventType.LEDGER_APPLY,
                        LedgerJournalCodec.encode(transaction))).replayed());
    }

    private static EscrowJournalEvent event(
            AuctionEscrowLifecycleEvent event
    ) {
        return new EscrowJournalEvent(EscrowJournalEventType
                .AUCTION_HOUSE_ESCROW_LIFECYCLE,
                AuctionEscrowLifecycleEventCodec.encode(event));
    }

    private static EscrowRuntimeSavedData cursorAtLineage(UUID lineage) {
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        cursor.establishLineage(lineage, 1L);
        return cursor;
    }

    private record Fixture(
            EscrowRuntimeSavedData cursor,
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            ItemInventoryJournalSavedData itemJournal,
            AuctionHouseSavedData auctions,
            EscrowSavedDataMutationApplier applier
    ) {
        private Fixture withCursor(EscrowRuntimeSavedData replacement) {
            return new Fixture(replacement, transactions, ledger, claims,
                    itemJournal, auctions, applier);
        }

        private boolean hasMaterializedState() {
            return transactions.hasMaterializedState()
                    || ledger.hasMaterializedState()
                    || claims.hasMaterializedState()
                    || itemJournal.hasMaterializedState()
                    || auctions.hasMaterializedState();
        }
    }
}
