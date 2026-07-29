package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalEntry;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalSavedData;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalStatus;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.stock.StockSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSavedData;
import com.enviouse.futureshops.server.market.auction.AuctionListingState;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionCreateEscrowIntent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionCreateRecoveryDecision;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowCommit;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowIds;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLedgerAccounts;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleEvent;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecycleEventCodec;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLifecyclePlanner;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowTestFixtures;
import com.enviouse.futureshops.server.market.bazaar.BazaarLifecycleCommand;
import com.enviouse.futureshops.server.market.bazaar.BazaarMutation;
import com.enviouse.futureshops.server.market.bazaar.BazaarMutationCodec;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderState;
import com.enviouse.futureshops.server.market.bazaar.BazaarSavedData;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarCreateEscrowIntent;
import com.enviouse.futureshops.server.market.bazaar.escrow.BazaarCreateRecoveryDecision;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6 crash-window drills (plan §17 crash injection, §18 corruption and
 * recovery drills): the coordinator is torn down between escrow steps and
 * recreated from the on-disk WAL, then the create-recovery decision logic is
 * exercised against the replayed state exactly as the expiration schedulers
 * drive it. Every crash window resolves to abort, retry, or resume-commit,
 * and replaying the final commit is idempotent with zero journal growth.
 */
class EscrowCrashWindowDrillTest {
    private static final long STARTING_BALANCE = 10_000L;

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void auctionCreateCrashAfterPrepareOnlyDecidesAbort(
            @TempDir Path directory
    ) {
        Path journal = directory.resolve("auction.prepare.crash.wal");
        Fixture live = fixture(new EscrowRuntimeSavedData());
        EscrowRuntimeCoordinator first = coordinator(journal, live);
        assertEquals(EscrowRuntimeState.READY, first.start());
        seedSeller(first);
        AuctionCreateEscrowIntent intent =
                AuctionEscrowTestFixtures.preparedCreate();
        first.commitAuctionEscrowLifecycle(intent.requestId(),
                auctionEvent(new AuctionEscrowLifecycleEvent.Prepare(
                        intent)));
        UUID lineage = live.cursor().journalLineage().orElseThrow();
        first.stop();

        Fixture cold = fixture(cursorAtLineage(lineage));
        EscrowRuntimeCoordinator restarted = coordinator(journal, cold);
        assertEquals(EscrowRuntimeState.READY, restarted.start(),
                () -> restarted.failure().map(Throwable::toString)
                        .orElse("No recovery failure"));

        AuctionCreateEscrowIntent recovered = cold.auctions()
                .createIntent(intent.requestId());
        assertEquals(AuctionCreateEscrowIntent.Status.PREPARED,
                recovered.status());
        // The item extraction never reached the durable journal: the only
        // safe decision is to abort — nothing was taken from the player.
        assertTrue(cold.itemJournal().find(AuctionEscrowIds
                .custodyRequestId(intent.requestId())).isEmpty());
        AuctionCreateRecoveryDecision decision = decide(cold, recovered);
        assertEquals(AuctionCreateRecoveryDecision.Action.ABORT,
                decision.action());
        assertTrue(decision.receipt().isEmpty());
        assertNull(cold.auctions().listing(intent.listingId()));
        restarted.stop();
    }

    @Test
    void auctionCreateCrashAfterPreparedCustodyDecidesRetry(
            @TempDir Path directory
    ) {
        Path journal = directory.resolve("auction.custody.crash.wal");
        Fixture live = fixture(new EscrowRuntimeSavedData());
        EscrowRuntimeCoordinator first = coordinator(journal, live);
        assertEquals(EscrowRuntimeState.READY, first.start());
        seedSeller(first);
        AuctionCreateEscrowIntent intent =
                AuctionEscrowTestFixtures.preparedCreate();
        first.commitAuctionEscrowLifecycle(intent.requestId(),
                auctionEvent(new AuctionEscrowLifecycleEvent.Prepare(
                        intent)));
        itemGateway(first, live).appendPreparedDurably(
                intent.itemMutationIntent());
        UUID lineage = live.cursor().journalLineage().orElseThrow();
        first.stop();

        Fixture cold = fixture(cursorAtLineage(lineage));
        EscrowRuntimeCoordinator restarted = coordinator(journal, cold);
        assertEquals(EscrowRuntimeState.READY, restarted.start());

        AuctionCreateEscrowIntent recovered = cold.auctions()
                .createIntent(intent.requestId());
        assertEquals(ItemInventoryJournalStatus.PREPARED,
                cold.itemJournal().find(AuctionEscrowIds
                                .custodyRequestId(intent.requestId()))
                        .orElseThrow().status());
        // Prepared-but-uncommitted custody must retry the item step, not
        // decide the listing either way.
        assertEquals(AuctionCreateRecoveryDecision.Action.RETRY_CUSTODY,
                decide(cold, recovered).action());
        restarted.stop();
    }

    @Test
    void auctionCrashAfterCommittedCustodyResumesCommitIdempotently(
            @TempDir Path directory
    ) throws Exception {
        Path journal = directory.resolve("auction.commit.crash.wal");
        Fixture live = fixture(new EscrowRuntimeSavedData());
        EscrowRuntimeCoordinator first = coordinator(journal, live);
        assertEquals(EscrowRuntimeState.READY, first.start());
        seedSeller(first);
        AuctionCreateEscrowIntent intent =
                AuctionEscrowTestFixtures.preparedCreate();
        first.commitAuctionEscrowLifecycle(intent.requestId(),
                auctionEvent(new AuctionEscrowLifecycleEvent.Prepare(
                        intent)));
        EscrowItemInventoryMutationGateway gateway = itemGateway(first,
                live);
        gateway.appendPreparedDurably(intent.itemMutationIntent());
        gateway.appendCommittedDurably(
                intent.plannedCustody().receipt());
        UUID lineage = live.cursor().journalLineage().orElseThrow();
        first.stop(); // crash window: custody committed, listing not

        Fixture cold = fixture(cursorAtLineage(lineage));
        EscrowRuntimeCoordinator restarted = coordinator(journal, cold);
        assertEquals(EscrowRuntimeState.READY, restarted.start());

        AuctionCreateEscrowIntent recovered = cold.auctions()
                .createIntent(intent.requestId());
        AuctionCreateRecoveryDecision decision = decide(cold, recovered);
        assertEquals(AuctionCreateRecoveryDecision.Action.COMMIT,
                decision.action());
        // The decision carries the exact stored receipt, not a fresh plan.
        assertEquals(recovered.plannedCustody().receipt(),
                decision.receipt().orElseThrow());

        AuctionEscrowCommit commit = AuctionEscrowLifecyclePlanner
                .commitCreate(cold.auctions().snapshot(), recovered,
                        decision.receipt().orElseThrow());
        EscrowJournalEvent complete = auctionEvent(
                new AuctionEscrowLifecycleEvent.Commit(
                        Optional.of(recovered.complete()), commit));
        assertFalse(restarted.commitAuctionEscrowLifecycle(
                intent.requestId(), complete).replayed());
        assertEquals(AuctionListingState.ACTIVE,
                cold.auctions().listing(intent.listingId()).state());
        assertEquals(STARTING_BALANCE
                        - intent.command().rules().listingFeeMinor(),
                cold.ledger().balance(AuctionEscrowLedgerAccounts
                        .wallet(intent.sellerId())));

        // Replaying the resumed commit grows nothing.
        long committedBytes = Files.size(journal);
        long committedSequence = cold.cursor().lastAppliedSequence();
        assertTrue(restarted.commitAuctionEscrowLifecycle(
                intent.requestId(), complete).replayed());
        assertEquals(committedBytes, Files.size(journal));
        assertEquals(committedSequence,
                cold.cursor().lastAppliedSequence());
        restarted.stop();
    }

    @Test
    void bazaarSellCrashAfterPrepareOnlyAbortsSafely(
            @TempDir Path directory
    ) throws Exception {
        Path journal = directory.resolve("bazaar.prepare.crash.wal");
        Fixture live = fixture(new EscrowRuntimeSavedData());
        EscrowRuntimeCoordinator first = coordinator(journal, live);
        assertEquals(EscrowRuntimeState.READY, first.start());
        seedBazaarCatalog(first, live);
        BazaarEscrowTestFixtures.PreparedSell sell =
                BazaarEscrowTestFixtures.sell(800L,
                        BazaarEscrowTestFixtures.id(80L), 3, 90_000L,
                        BazaarEscrowTestFixtures.rules());
        first.commitBazaarEscrowLifecycle(sell.intent().requestId(),
                bazaarEvent(new BazaarEscrowLifecycleEvent.Prepare(
                        sell.intent())));
        UUID lineage = live.cursor().journalLineage().orElseThrow();
        first.stop();

        Fixture cold = fixture(cursorAtLineage(lineage));
        EscrowRuntimeCoordinator restarted = coordinator(journal, cold);
        assertEquals(EscrowRuntimeState.READY, restarted.start());

        BazaarCreateEscrowIntent recovered = cold.bazaar()
                .createIntent(sell.intent().requestId());
        assertEquals(BazaarCreateEscrowIntent.Status.PREPARED,
                recovered.status());
        Optional<ItemInventoryMutationReceipt> committed =
                committedSellReceipt(cold, recovered);
        assertTrue(committed.isEmpty());
        BazaarCreateRecoveryDecision decision =
                BazaarCreateRecoveryDecision.inspect(recovered,
                        committed);
        assertEquals(BazaarCreateRecoveryDecision.Action.ABORT_SAFE,
                decision.action());

        EscrowJournalEvent resolve = bazaarEvent(
                new BazaarEscrowLifecycleEvent.Resolve(recovered,
                        decision.terminalIntent().orElseThrow()));
        assertFalse(restarted.commitBazaarEscrowLifecycle(
                recovered.requestId(), resolve).replayed());
        assertEquals(BazaarCreateEscrowIntent.Status.ABORTED,
                cold.bazaar().createIntent(recovered.requestId())
                        .status());
        assertNull(cold.bazaar().order(recovered.orderId()));
        assertFalse(cold.bazaar().escrowLifecycleSnapshot()
                .activeBackings().containsKey(recovered.orderId()));

        long committedBytes = Files.size(journal);
        assertTrue(restarted.commitBazaarEscrowLifecycle(
                recovered.requestId(), resolve).replayed());
        assertEquals(committedBytes, Files.size(journal));
        restarted.stop();
    }

    @Test
    void bazaarSellCrashAfterCommittedCustodyResumesCommit(
            @TempDir Path directory
    ) throws Exception {
        Path journal = directory.resolve("bazaar.commit.crash.wal");
        Fixture live = fixture(new EscrowRuntimeSavedData());
        EscrowRuntimeCoordinator first = coordinator(journal, live);
        assertEquals(EscrowRuntimeState.READY, first.start());
        seedBazaarCatalog(first, live);
        BazaarEscrowTestFixtures.PreparedSell sell =
                BazaarEscrowTestFixtures.sell(900L,
                        BazaarEscrowTestFixtures.id(90L), 3, 90_000L,
                        BazaarEscrowTestFixtures.rules());
        first.commitBazaarEscrowLifecycle(sell.intent().requestId(),
                bazaarEvent(new BazaarEscrowLifecycleEvent.Prepare(
                        sell.intent())));
        EscrowItemInventoryMutationGateway gateway = itemGateway(first,
                live);
        gateway.appendPreparedDurably(
                sell.intent().itemMutationIntent().orElseThrow());
        gateway.appendCommittedDurably(sell.receipt());
        UUID lineage = live.cursor().journalLineage().orElseThrow();
        first.stop(); // crash window: items extracted, order not opened

        Fixture cold = fixture(cursorAtLineage(lineage));
        EscrowRuntimeCoordinator restarted = coordinator(journal, cold);
        assertEquals(EscrowRuntimeState.READY, restarted.start());

        BazaarCreateEscrowIntent recovered = cold.bazaar()
                .createIntent(sell.intent().requestId());
        Optional<ItemInventoryMutationReceipt> committed =
                committedSellReceipt(cold, recovered);
        assertEquals(sell.receipt(), committed.orElseThrow());
        BazaarCreateRecoveryDecision decision =
                BazaarCreateRecoveryDecision.inspect(recovered,
                        committed);
        assertEquals(BazaarCreateRecoveryDecision.Action.RESUME_COMMIT,
                decision.action());

        var planned = BazaarEscrowLifecyclePlanner.commitCreate(
                cold.bazaar().snapshot(),
                cold.bazaar().escrowLifecycleSnapshot(), recovered,
                committed, BazaarEscrowTestFixtures.NOW);
        EscrowJournalEvent complete = bazaarEvent(
                new BazaarEscrowLifecycleEvent.Commit(
                        Optional.of(planned.terminalIntent()),
                        planned.commit()));
        assertFalse(restarted.commitBazaarEscrowLifecycle(
                recovered.requestId(), complete).replayed());
        assertEquals(BazaarOrderState.OPEN,
                cold.bazaar().order(recovered.orderId()).state());
        assertEquals(3, cold.bazaar().escrowLifecycleSnapshot()
                .activeBackings().get(recovered.orderId())
                .sellCustody().orElseThrow().remainingQuantity());

        long committedBytes = Files.size(journal);
        assertTrue(restarted.commitBazaarEscrowLifecycle(
                recovered.requestId(), complete).replayed());
        assertEquals(committedBytes, Files.size(journal));
        restarted.stop();
    }

    /** Maps the replayed item journal onto the auction recovery decision
     * exactly the way {@code AuctionExpirationScheduler} does. */
    private static AuctionCreateRecoveryDecision decide(
            Fixture fixture,
            AuctionCreateEscrowIntent intent
    ) {
        Optional<ItemInventoryJournalEntry> entry = fixture.itemJournal()
                .find(AuctionEscrowIds.custodyRequestId(
                        intent.requestId()));
        AuctionCreateRecoveryDecision.CustodyState custodyState = entry
                .map(value -> switch (value.status()) {
                    case PREPARED -> AuctionCreateRecoveryDecision
                            .CustodyState.PREPARED;
                    case COMMITTED -> AuctionCreateRecoveryDecision
                            .CustodyState.COMMITTED;
                    case ABORTED -> AuctionCreateRecoveryDecision
                            .CustodyState.ABORTED;
                    case QUARANTINED -> AuctionCreateRecoveryDecision
                            .CustodyState.QUARANTINED;
                })
                .orElse(AuctionCreateRecoveryDecision.CustodyState.NONE);
        return AuctionCreateRecoveryDecision.decide(intent, custodyState,
                entry.flatMap(ItemInventoryJournalEntry::committedReceipt));
    }

    private static Optional<ItemInventoryMutationReceipt>
    committedSellReceipt(Fixture fixture, BazaarCreateEscrowIntent intent) {
        return intent.sellCustody()
                .flatMap(custody -> fixture.itemJournal().find(
                        custody.receipt().token().requestId()))
                .flatMap(ItemInventoryJournalEntry::committedReceipt);
    }

    private static EscrowItemInventoryMutationGateway itemGateway(
            EscrowRuntimeCoordinator coordinator,
            Fixture fixture
    ) {
        return new EscrowItemInventoryMutationGateway(coordinator,
                fixture.itemJournal(), fixture.claims(), () -> true);
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
                        LedgerJournalCodec.encode(transaction)))
                .replayed());
    }

    private static void seedBazaarCatalog(
            EscrowRuntimeCoordinator coordinator,
            Fixture fixture
    ) {
        BazaarLifecycleCommand rules =
                BazaarLifecycleCommand.setEffectiveRules(
                        BazaarEscrowTestFixtures.id(990L),
                        BazaarEscrowTestFixtures.rules());
        commitBazaarMutation(coordinator,
                BazaarMutation.lifecycle(fixture.bazaar().snapshot(),
                        rules));
        BazaarLifecycleCommand product =
                BazaarLifecycleCommand.registerProduct(
                        BazaarEscrowTestFixtures.id(991L),
                        BazaarEscrowTestFixtures.product());
        commitBazaarMutation(coordinator,
                BazaarMutation.lifecycle(fixture.bazaar().snapshot(),
                        product));
    }

    private static void commitBazaarMutation(
            EscrowRuntimeCoordinator coordinator,
            BazaarMutation mutation
    ) {
        assertFalse(coordinator.commitBazaarMutation(
                mutation.mutationId(), new EscrowJournalEvent(
                        EscrowJournalEventType.BAZAAR_MUTATION,
                        BazaarMutationCodec.encode(mutation)))
                .replayed());
    }

    private static EscrowJournalEvent auctionEvent(
            AuctionEscrowLifecycleEvent event
    ) {
        return new EscrowJournalEvent(EscrowJournalEventType
                .AUCTION_HOUSE_ESCROW_LIFECYCLE,
                AuctionEscrowLifecycleEventCodec.encode(event));
    }

    private static EscrowJournalEvent bazaarEvent(
            BazaarEscrowLifecycleEvent event
    ) {
        return new EscrowJournalEvent(
                EscrowJournalEventType.BAZAAR_ESCROW_LIFECYCLE,
                BazaarEscrowLifecycleEventCodec.encode(event));
    }

    private static EscrowRuntimeCoordinator coordinator(
            Path journal,
            Fixture fixture
    ) {
        return new EscrowRuntimeCoordinator(journal, fixture.cursor(),
                fixture.applier(), fixture::hasMaterializedState);
    }

    private static EscrowRuntimeSavedData cursorAtLineage(UUID lineage) {
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        cursor.establishLineage(lineage, 1L);
        return cursor;
    }

    private static Fixture fixture(EscrowRuntimeSavedData cursor) {
        EscrowTransactionSavedData transactions =
                new EscrowTransactionSavedData();
        LedgerSavedData ledger = new LedgerSavedData();
        ClaimSavedData claims = new ClaimSavedData();
        ItemInventoryJournalSavedData itemJournal =
                new ItemInventoryJournalSavedData();
        AuctionHouseSavedData auctions = new AuctionHouseSavedData();
        BazaarSavedData bazaar = new BazaarSavedData();
        EscrowSavedDataMutationApplier applier =
                new EscrowSavedDataMutationApplier(transactions,
                        ledger, claims,
                        new EscrowAdministrativeAuditSavedData(),
                        new CustodySavedData(),
                        new ProtectedMintSavedData(),
                        new StockSavedData(), itemJournal, auctions,
                        bazaar, new ServerShopIntentSavedData(),
                        MaintenanceRuntimeMutationHandler.unavailable(),
                        AtmWithdrawalApplyFaultInjector.NONE, null);
        return new Fixture(cursor, transactions, ledger, claims,
                itemJournal, auctions, bazaar, applier);
    }

    private record Fixture(
            EscrowRuntimeSavedData cursor,
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            ItemInventoryJournalSavedData itemJournal,
            AuctionHouseSavedData auctions,
            BazaarSavedData bazaar,
            EscrowSavedDataMutationApplier applier
    ) {
        private boolean hasMaterializedState() {
            return transactions.hasMaterializedState()
                    || ledger.hasMaterializedState()
                    || claims.hasMaterializedState()
                    || itemJournal.hasMaterializedState()
                    || auctions.hasMaterializedState()
                    || bazaar.hasMaterializedState();
        }
    }
}
