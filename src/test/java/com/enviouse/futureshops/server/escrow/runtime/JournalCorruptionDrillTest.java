package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.item.runtime.ItemInventoryJournalSavedData;
import com.enviouse.futureshops.server.escrow.journal.JournalCorruptionException;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.stock.StockSavedData;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import com.enviouse.futureshops.server.market.auction.AuctionHouseSavedData;
import com.enviouse.futureshops.server.market.auction.escrow.AuctionEscrowLedgerAccounts;
import com.enviouse.futureshops.server.market.bazaar.BazaarSavedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6 corruption drills (plan §18) at the coordinator level, on top of
 * the record-level coverage in {@code WriteAheadJournalTest}: a torn final
 * record loses exactly that record — every earlier record still replays and
 * the journal keeps accepting new work — while a corrupted middle record
 * fails the whole runtime closed into MAINTENANCE with nothing materialized
 * and every mutation lane refused.
 */
class JournalCorruptionDrillTest {
    private static final UUID PLAYER = new UUID(99L, 1L);
    private static final LedgerAccountId WALLET =
            AuctionEscrowLedgerAccounts.wallet(PLAYER);

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void truncatedFinalRecordLosesOnlyThatRecordOnRestart(
            @TempDir Path directory
    ) throws Exception {
        Path journal = directory.resolve("corruption.tail.wal");
        Fixture live = fixture(new EscrowRuntimeSavedData());
        EscrowRuntimeCoordinator first = coordinator(journal, live);
        assertEquals(EscrowRuntimeState.READY, first.start());
        commitDeposit(first, 1L, 100L);
        commitDeposit(first, 2L, 200L);
        commitDeposit(first, 3L, 300L);
        assertEquals(600L, live.ledger().balance(WALLET));
        long completeLength = Files.size(journal);
        UUID lineage = live.cursor().journalLineage().orElseThrow();
        first.stop();

        // Tear the tail of the final record, as a crash mid-write would.
        try (FileChannel channel = FileChannel.open(journal,
                StandardOpenOption.WRITE)) {
            channel.truncate(completeLength - 3L);
        }

        Fixture cold = fixture(cursorAtLineage(lineage));
        EscrowRuntimeCoordinator restarted = coordinator(journal, cold);
        assertEquals(EscrowRuntimeState.READY, restarted.start(),
                () -> restarted.failure().map(Throwable::toString)
                        .orElse("No recovery failure"));

        // Lineage + first two deposits replayed; only the torn one is lost.
        assertEquals(3L, cold.cursor().lastAppliedSequence());
        assertEquals(300L, cold.ledger().balance(WALLET));
        assertTrue(restarted.journalHealthyAndAligned());

        // The recovered journal keeps accepting new work at the cut point.
        commitDeposit(restarted, 4L, 50L);
        assertEquals(4L, cold.cursor().lastAppliedSequence());
        assertEquals(350L, cold.ledger().balance(WALLET));
        restarted.stop();
    }

    @Test
    void corruptedMiddleRecordFailsClosedIntoMaintenance(
            @TempDir Path directory
    ) throws Exception {
        Path journal = directory.resolve("corruption.middle.wal");
        Fixture live = fixture(new EscrowRuntimeSavedData());
        EscrowRuntimeCoordinator first = coordinator(journal, live);
        assertEquals(EscrowRuntimeState.READY, first.start());
        commitDeposit(first, 1L, 100L);
        long afterFirstDeposit = Files.size(journal);
        commitDeposit(first, 2L, 200L);
        long afterSecondDeposit = Files.size(journal);
        commitDeposit(first, 3L, 300L);
        UUID lineage = live.cursor().journalLineage().orElseThrow();
        first.stop();

        // Flip one byte near the end of the middle deposit record so its
        // checksum no longer matches while the record framing stays intact.
        byte[] file = Files.readAllBytes(journal);
        file[(int) (afterSecondDeposit - 5L)] ^= 0x40;
        Files.write(journal, file);

        Fixture cold = fixture(cursorAtLineage(lineage));
        EscrowRuntimeCoordinator restarted = coordinator(journal, cold);
        assertEquals(EscrowRuntimeState.MAINTENANCE, restarted.start());

        JournalCorruptionException corruption = assertInstanceOf(
                JournalCorruptionException.class,
                restarted.failure().orElseThrow());
        // The failure points at the corrupted record, not the tail.
        assertEquals(afterFirstDeposit, corruption.offset());

        // Fail closed: nothing materialized, nothing accepted. The cursor
        // never advanced past its pre-seeded lineage record (sequence 1).
        assertEquals(0L, cold.ledger().balance(WALLET));
        assertEquals(1L, cold.cursor().lastAppliedSequence());
        assertFalse(restarted.isReady());
        assertFalse(restarted.journalHealthyAndAligned());
        assertThrows(EscrowRuntimeException.class,
                restarted::journalMetrics);
        assertThrows(EscrowRuntimeException.class,
                () -> commitDeposit(restarted, 4L, 50L));

        // The corrupted bytes are preserved on disk for the operator.
        assertEquals(file.length, Files.size(journal));
    }

    private static void commitDeposit(
            EscrowRuntimeCoordinator coordinator,
            long seed,
            long amountMinor
    ) {
        UUID transactionId = new UUID(98L, seed);
        LedgerTransaction transaction = new LedgerTransaction(
                transactionId, "drill.seed." + seed,
                "Corruption drill deposit",
                List.of(new LedgerLeg(new LedgerAccountId(
                                LedgerAccountType.ADMIN_SOURCE,
                                "corruption.drill"),
                                Math.negateExact(amountMinor)),
                        new LedgerLeg(WALLET, amountMinor)));
        assertFalse(coordinator.commit(transactionId,
                new EscrowJournalEvent(
                        EscrowJournalEventType.LEDGER_APPLY,
                        LedgerJournalCodec.encode(transaction)))
                .replayed());
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
        EscrowSavedDataMutationApplier applier =
                new EscrowSavedDataMutationApplier(transactions,
                        ledger, claims,
                        new EscrowAdministrativeAuditSavedData(),
                        new CustodySavedData(),
                        new ProtectedMintSavedData(),
                        new StockSavedData(), itemJournal,
                        new AuctionHouseSavedData(),
                        new BazaarSavedData(),
                        new ServerShopIntentSavedData(),
                        MaintenanceRuntimeMutationHandler.unavailable(),
                        AtmWithdrawalApplyFaultInjector.NONE, null);
        return new Fixture(cursor, transactions, ledger, claims,
                itemJournal, applier);
    }

    private record Fixture(
            EscrowRuntimeSavedData cursor,
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            ItemInventoryJournalSavedData itemJournal,
            EscrowSavedDataMutationApplier applier
    ) {
        private boolean hasMaterializedState() {
            return transactions.hasMaterializedState()
                    || ledger.hasMaterializedState()
                    || claims.hasMaterializedState()
                    || itemJournal.hasMaterializedState();
        }
    }
}
