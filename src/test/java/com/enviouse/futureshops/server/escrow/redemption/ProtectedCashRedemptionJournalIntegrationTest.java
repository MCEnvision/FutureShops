package com.enviouse.futureshops.server.escrow.redemption;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.audit.EscrowCrossDomainConservationAudit;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodyLotState;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.journal.JournalRecord;
import com.enviouse.futureshops.server.escrow.journal.WriteAheadJournal;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.runtime.EscrowCommitResult;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEvent;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventCodec;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventType;
import com.enviouse.futureshops.server.escrow.runtime.EscrowPreflightResult;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeCoordinator;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeException;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeSavedData;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeState;
import com.enviouse.futureshops.server.escrow.runtime.EscrowSavedDataMutationApplier;
import com.enviouse.futureshops.server.escrow.runtime.EscrowStepIds;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectedCashRedemptionJournalIntegrationTest {
    private static final UUID MINT_A_SOURCE = UUID.fromString(
            "3997ff5b-ed2c-48cf-b948-f66dfe0048db");
    private static final UUID MINT_B_SOURCE = UUID.fromString(
            "02028774-3f28-4b4d-a156-2f5cba50db40");
    private static final UUID LEDGER_SEED = UUID.fromString(
            "5fda3d0f-35e9-4055-adaf-175606588811");
    private static final long ISSUED_VALUE = 2_800L;

    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void scopedReservationAndSettlementAppendAndReplayExactlyOnce()
            throws Exception {
        ProtectedCashRedemptionReservation reservation =
                ProtectedCashRedemptionTestFixtures.reservation();
        ProtectedCashRedemptionSettlement settlement =
                ProtectedCashRedemptionTestFixtures.settlement();
        Stores stores = Stores.seeded();
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        Path journalPath = temporaryDirectory.resolve("redemption.wal");
        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(
                journalPath, cursor, stores.applier());

        assertEquals(EscrowRuntimeState.READY, coordinator.start());
        EscrowCommitResult reserved = coordinator
                .commitProtectedCashReservation(reservation);
        assertFalse(reserved.replayed());
        assertEquals(2L, reserved.record().orElseThrow().sequence());
        assertReservationMaterialized(stores, reservation);
        assertTrue(EscrowCrossDomainConservationAudit.verify(
                stores.ledger(), stores.claims(), stores.custody(),
                stores.mints()).conserved());

        long reservationBytes = Files.size(journalPath);
        assertTrue(coordinator.commitProtectedCashReservation(
                reservation).replayed());
        assertEquals(reservationBytes, Files.size(journalPath));

        EscrowCommitResult settled = coordinator
                .commitProtectedCashSettlement(settlement);
        assertFalse(settled.replayed());
        assertEquals(3L, settled.record().orElseThrow().sequence());
        assertSettlementMaterialized(stores, settlement);
        assertTrue(EscrowCrossDomainConservationAudit.verify(
                stores.ledger(), stores.claims(), stores.custody(),
                stores.mints()).conserved());

        long settlementBytes = Files.size(journalPath);
        assertTrue(coordinator.commitProtectedCashSettlement(
                settlement).replayed());
        assertEquals(settlementBytes, Files.size(journalPath));
        assertEquals(3L, cursor.lastAppliedSequence());
        coordinator.close();

        try (WriteAheadJournal journal = WriteAheadJournal.open(journalPath)) {
            List<JournalRecord> records = journal.replayBatch(
                    0L, 1L, 10,
                    WriteAheadJournal.MAX_REPLAY_BATCH_BYTES).records();
            assertEquals(3, records.size());
            assertEquals(EscrowJournalEventType
                            .PROTECTED_CASH_REDEMPTION_RESERVATION,
                    EscrowJournalEventCodec.decode(
                            records.get(1).payload()).type());
            assertEquals(EscrowJournalEventType
                            .PROTECTED_CASH_REDEMPTION_SETTLEMENT,
                    EscrowJournalEventCodec.decode(
                            records.get(2).payload()).type());
        }
    }

    @Test
    void terminalEvidenceBeforeTerminalWalReplaysAfterRestart()
            throws Exception {
        ProtectedCashRedemptionTestFixtures.ProductionScenario scenario =
                ProtectedCashRedemptionTestFixtures.productionScenario();
        byte[] durableEvidence = ProtectedCashRedemptionEvidence.settlement(
                scenario.settlement(), scenario.after()).encode();
        Stores stores = Stores.seeded();
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        Path journalPath = temporaryDirectory.resolve(
                "terminal-evidence-first.wal");
        EscrowRuntimeCoordinator first = new EscrowRuntimeCoordinator(
                journalPath, cursor, stores.applier());

        assertEquals(EscrowRuntimeState.READY, first.start());
        first.commitProtectedCashReservation(scenario.reservation());
        first.close();

        ProtectedCashRedemptionSettlement recovered =
                ProtectedCashRedemptionEvidence.decode(durableEvidence)
                        .settlement().orElseThrow();
        EscrowRuntimeCoordinator restarted = new EscrowRuntimeCoordinator(
                journalPath, cursor, stores.applier());
        assertEquals(EscrowRuntimeState.READY, restarted.start());
        assertFalse(restarted.commitProtectedCashSettlement(
                recovered).replayed());
        assertSettlementMaterialized(stores, recovered);
        assertTrue(EscrowCrossDomainConservationAudit.verify(
                stores.ledger(), stores.claims(), stores.custody(),
                stores.mints()).conserved());
        restarted.close();
    }

    @Test
    void terminalWalBeforeEvidenceCleanupReplaysAfterRestart()
            throws Exception {
        ProtectedCashRedemptionTestFixtures.ProductionScenario scenario =
                ProtectedCashRedemptionTestFixtures.productionScenario();
        byte[] staleEvidence = ProtectedCashRedemptionEvidence.cancellation(
                scenario.cancellation(), scenario.before()).encode();
        Stores stores = Stores.seeded();
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        Path journalPath = temporaryDirectory.resolve(
                "terminal-wal-first.wal");
        EscrowRuntimeCoordinator first = new EscrowRuntimeCoordinator(
                journalPath, cursor, stores.applier());

        assertEquals(EscrowRuntimeState.READY, first.start());
        first.commitProtectedCashReservation(scenario.reservation());
        first.commitProtectedCashCancellation(scenario.cancellation());
        long journalBytes = Files.size(journalPath);
        first.close();

        ProtectedCashRedemptionCancellation recovered =
                ProtectedCashRedemptionEvidence.decode(staleEvidence)
                        .cancellation().orElseThrow();
        EscrowRuntimeCoordinator restarted = new EscrowRuntimeCoordinator(
                journalPath, cursor, stores.applier());
        assertEquals(EscrowRuntimeState.READY, restarted.start());
        assertTrue(restarted.commitProtectedCashCancellation(
                recovered).replayed());
        assertEquals(journalBytes, Files.size(journalPath));
        assertCancellationMaterialized(stores, recovered);
        restarted.close();
    }

    @Test
    void replayConvergesFromEveryCompositeBoundary() {
        ProtectedCashRedemptionReservation reservation =
                ProtectedCashRedemptionTestFixtures.reservation();
        EscrowJournalEvent reservationEvent = reservationEvent(reservation);

        Stores reservationStores = Stores.seeded();
        reservationStores.transactions().applyFoldedHeldCommitted(
                reservation.heldTransaction());
        assertThrows(EscrowRuntimeException.class, () ->
                reservationStores.applier().preflight(
                        reservation.transactionId(), reservationEvent));
        reservationStores.applier().apply(
                record(2L, reservation.transactionId(), reservationEvent),
                reservationEvent);
        assertEquals(EscrowPreflightResult.REPLAY,
                reservationStores.applier().preflight(
                        reservation.transactionId(), reservationEvent));
        assertReservationMaterialized(reservationStores, reservation);

        ProtectedCashRedemptionSettlement settlement =
                ProtectedCashRedemptionTestFixtures.settlement();
        Stores settlementStores = Stores.seeded();
        settlementStores.applier().apply(
                record(2L, reservation.transactionId(), reservationEvent),
                reservationEvent);
        settlementStores.custody().applyCommittedBatch(
                settlement.custodyConsumptions());
        EscrowJournalEvent settlementEvent = settlementEvent(settlement);
        assertThrows(EscrowRuntimeException.class, () ->
                settlementStores.applier().preflight(
                        settlement.transactionId(), settlementEvent));
        settlementStores.applier().apply(
                record(3L, settlement.transactionId(), settlementEvent),
                settlementEvent);
        assertEquals(EscrowPreflightResult.REPLAY,
                settlementStores.applier().preflight(
                        settlement.transactionId(), settlementEvent));
        assertSettlementMaterialized(settlementStores, settlement);
        assertTrue(EscrowCrossDomainConservationAudit.verify(
                settlementStores.ledger(), settlementStores.claims(),
                settlementStores.custody(), settlementStores.mints())
                .conserved());

        ProtectedCashRedemptionCancellation cancellation =
                ProtectedCashRedemptionTestFixtures.cancellation();
        Stores cancellationStores = Stores.seeded();
        cancellationStores.applier().apply(
                record(2L, reservation.transactionId(), reservationEvent),
                reservationEvent);
        cancellationStores.custody().applyCommittedBatch(
                cancellation.custodyReleases());
        EscrowJournalEvent cancellationEvent = cancellationEvent(
                cancellation);
        assertThrows(EscrowRuntimeException.class, () ->
                cancellationStores.applier().preflight(
                        cancellation.transactionId(), cancellationEvent));
        cancellationStores.applier().apply(
                record(3L, cancellation.transactionId(), cancellationEvent),
                cancellationEvent);
        assertEquals(EscrowPreflightResult.REPLAY,
                cancellationStores.applier().preflight(
                        cancellation.transactionId(), cancellationEvent));
        assertCancellationMaterialized(cancellationStores, cancellation);
    }

    @Test
    void cancellationAppendsReplaysAndRestoresOriginalBills() throws Exception {
        ProtectedCashRedemptionReservation reservation =
                ProtectedCashRedemptionTestFixtures.reservation();
        ProtectedCashRedemptionCancellation cancellation =
                ProtectedCashRedemptionTestFixtures.cancellation();
        Stores stores = Stores.seeded();
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        Path journalPath = temporaryDirectory.resolve("cancellation.wal");
        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(
                journalPath, cursor, stores.applier());

        assertEquals(EscrowRuntimeState.READY, coordinator.start());
        coordinator.commitProtectedCashReservation(reservation);
        EscrowCommitResult result = coordinator
                .commitProtectedCashCancellation(cancellation);
        assertFalse(result.replayed());
        assertEquals(3L, result.record().orElseThrow().sequence());
        assertCancellationMaterialized(stores, cancellation);
        assertTrue(EscrowCrossDomainConservationAudit.verify(
                stores.ledger(), stores.claims(), stores.custody(),
                stores.mints()).conserved());
        long journalBytes = Files.size(journalPath);
        assertTrue(coordinator.commitProtectedCashCancellation(
                cancellation).replayed());
        assertEquals(journalBytes, Files.size(journalPath));
        coordinator.close();

        try (WriteAheadJournal journal = WriteAheadJournal.open(journalPath)) {
            List<JournalRecord> records = journal.replayBatch(
                    0L, 1L, 10,
                    WriteAheadJournal.MAX_REPLAY_BATCH_BYTES).records();
            assertEquals(EscrowJournalEventType
                            .PROTECTED_CASH_REDEMPTION_CANCELLATION,
                    EscrowJournalEventCodec.decode(
                            records.get(2).payload()).type());
        }
    }

    @Test
    void walletOverflowCreatesExactMoneyClaimWithoutExceedingBound()
            throws Exception {
        long walletBefore = 9_500L;
        long reservedBefore = 300L;
        ProtectedCashRedemptionReservation reservation =
                ProtectedCashRedemptionTestFixtures.reservation();
        ProtectedCashRedemptionSettlement settlement =
                ProtectedCashRedemptionTestFixtures.settlement(
                        walletBefore, reservedBefore);
        Stores stores = Stores.seeded();
        stores.seedPlayerBalances(walletBefore, reservedBefore);
        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(
                temporaryDirectory.resolve("overflow.wal"),
                new EscrowRuntimeSavedData(), stores.applier());

        assertEquals(EscrowRuntimeState.READY, coordinator.start());
        coordinator.commitProtectedCashReservation(reservation);
        coordinator.commitProtectedCashSettlement(settlement);

        assertEquals(200L, settlement.walletCreditMinorUnits());
        assertEquals(600L, settlement.overflowClaimMinorUnits());
        assertEquals(9_700L, stores.ledger().balance(
                reservation.destinationAccount()));
        assertEquals(300L, stores.ledger().balance(new LedgerAccountId(
                LedgerAccountType.PLAYER_RESERVED,
                reservation.playerId().toString())));
        var claim = stores.claims().getClaim(
                settlement.overflowClaim().orElseThrow().claimId());
        assertNotNull(claim);
        assertEquals(600L, claim.remainingUnits());
        assertEquals(600L, stores.ledger().balance(new LedgerAccountId(
                LedgerAccountType.PLAYER_CLAIM,
                claim.claimId().toString())));
        coordinator.close();
    }

    @Test
    void walletSnapshotChangeFailsBeforeCashSettlementMaterializes() {
        ProtectedCashRedemptionReservation reservation =
                ProtectedCashRedemptionTestFixtures.reservation();
        ProtectedCashRedemptionSettlement settlement =
                ProtectedCashRedemptionTestFixtures.settlement(9_500L, 300L);
        Stores stores = Stores.seeded();
        stores.seedPlayerBalances(9_499L, 300L);
        EscrowJournalEvent reservationEvent = reservationEvent(reservation);
        stores.applier().apply(
                record(2L, reservation.transactionId(), reservationEvent),
                reservationEvent);

        assertThrows(EscrowRuntimeException.class, () ->
                stores.applier().preflight(settlement.transactionId(),
                        settlementEvent(settlement)));
        assertReservationMaterialized(stores, reservation);
    }

    @Test
    void corruptAndOutOfOrderEventsFailBeforeMaterialization() {
        ProtectedCashRedemptionReservation reservation =
                ProtectedCashRedemptionTestFixtures.reservation();
        Stores stores = Stores.seeded();
        byte[] corrupt = ProtectedCashRedemptionReservationCodec.encode(
                reservation);
        corrupt[0] ^= 1;
        EscrowJournalEvent corruptEvent = new EscrowJournalEvent(
                EscrowJournalEventType
                        .PROTECTED_CASH_REDEMPTION_RESERVATION, corrupt);

        assertThrows(IllegalArgumentException.class, () ->
                stores.applier().preflight(reservation.transactionId(),
                        corruptEvent));
        assertNull(stores.transactions().getTransaction(
                new EscrowTransactionId(reservation.transactionId())));
        assertTrue(stores.custody().conservation().conserved());
        assertThrows(EscrowRuntimeException.class, () -> stores.applier()
                .preflight(reservation.transactionId(), settlementEvent(
                        ProtectedCashRedemptionTestFixtures.settlement())));
        assertThrows(EscrowRuntimeException.class, () -> stores.applier()
                .preflight(reservation.transactionId(), cancellationEvent(
                        ProtectedCashRedemptionTestFixtures.cancellation())));
    }

    private static EscrowJournalEvent reservationEvent(
            ProtectedCashRedemptionReservation reservation
    ) {
        return new EscrowJournalEvent(EscrowJournalEventType
                .PROTECTED_CASH_REDEMPTION_RESERVATION,
                ProtectedCashRedemptionReservationCodec.encode(reservation));
    }

    private static EscrowJournalEvent settlementEvent(
            ProtectedCashRedemptionSettlement settlement
    ) {
        return new EscrowJournalEvent(EscrowJournalEventType
                .PROTECTED_CASH_REDEMPTION_SETTLEMENT,
                ProtectedCashRedemptionSettlementCodec.encode(settlement));
    }

    private static EscrowJournalEvent cancellationEvent(
            ProtectedCashRedemptionCancellation cancellation
    ) {
        return new EscrowJournalEvent(EscrowJournalEventType
                .PROTECTED_CASH_REDEMPTION_CANCELLATION,
                ProtectedCashRedemptionCancellationCodec.encode(
                        cancellation));
    }

    private static JournalRecord record(long sequence,
                                        UUID transactionId,
                                        EscrowJournalEvent event) {
        return new JournalRecord(sequence, transactionId,
                EscrowStepIds.forEvent(transactionId, event),
                EscrowJournalEventCodec.encode(event));
    }

    private static void assertReservationMaterialized(
            Stores stores,
            ProtectedCashRedemptionReservation reservation
    ) {
        assertEquals(EscrowState.HELD,
                stores.transactions().getTransaction(
                        new EscrowTransactionId(
                                reservation.transactionId())).state());
        for (var mutation : reservation.custodyReservations()) {
            assertEquals(CustodyLotState.HELD, stores.custody().getLot(
                    mutation.resultingLot().lotId()).state());
        }
        for (ProtectedMintJournalEvent event :
                reservation.mintReservations()) {
            assertEquals(event.quantity(), stores.mints().getBatch(
                    event.targetBatchId().orElseThrow()).reservedFor(
                    reservation.transactionId()));
        }
        assertEquals(ISSUED_VALUE, stores.ledger().balance(
                LedgerAccountId.system(
                        LedgerAccountType.PROTECTED_CURRENCY_OUTSTANDING)));
    }

    private static void assertSettlementMaterialized(
            Stores stores,
            ProtectedCashRedemptionSettlement settlement
    ) {
        assertEquals(EscrowState.COMPLETED,
                stores.transactions().getTransaction(
                        new EscrowTransactionId(
                                settlement.transactionId())).state());
        for (var mutation : settlement.custodyConsumptions()) {
            assertEquals(CustodyLotState.CONSUMED, stores.custody().getLot(
                    mutation.resultingLot().lotId()).state());
        }
        for (ProtectedMintJournalEvent event : settlement.mintCommits()) {
            assertEquals(event.quantity(), stores.mints().getBatch(
                    event.targetBatchId().orElseThrow()).spentFor(
                    settlement.transactionId()));
        }
        assertEquals(settlement.amountMinorUnits(), stores.ledger().balance(
                settlement.destinationAccount()));
        assertEquals(ISSUED_VALUE - settlement.amountMinorUnits(),
                stores.ledger().balance(LedgerAccountId.system(
                        LedgerAccountType.PROTECTED_CURRENCY_OUTSTANDING)));
    }

    private static void assertCancellationMaterialized(
            Stores stores,
            ProtectedCashRedemptionCancellation cancellation
    ) {
        assertEquals(EscrowState.REFUNDED,
                stores.transactions().getTransaction(
                        new EscrowTransactionId(
                                cancellation.transactionId())).state());
        for (var mutation : cancellation.custodyReleases()) {
            assertEquals(CustodyLotState.RELEASED, stores.custody().getLot(
                    mutation.resultingLot().lotId()).state());
        }
        for (ProtectedMintJournalEvent event :
                cancellation.mintReleases()) {
            var batch = stores.mints().getBatch(
                    event.targetBatchId().orElseThrow());
            assertEquals(0, batch.reservedFor(cancellation.transactionId()));
            assertEquals(batch.authorizedCount(), batch.availableQuantity());
        }
        assertEquals(ISSUED_VALUE, stores.ledger().balance(
                LedgerAccountId.system(
                        LedgerAccountType.PROTECTED_CURRENCY_OUTSTANDING)));
    }

    private record Stores(
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            EscrowAdministrativeAuditSavedData audit,
            CustodySavedData custody,
            ProtectedMintSavedData mints
    ) {
        private static Stores seeded() {
            Stores stores = new Stores(new EscrowTransactionSavedData(),
                    new LedgerSavedData(), new ClaimSavedData(),
                    new EscrowAdministrativeAuditSavedData(),
                    new CustodySavedData(), new ProtectedMintSavedData());
            seedMint(stores.mints(),
                    ProtectedCashRedemptionTestFixtures.MINT_A,
                    MINT_A_SOURCE, 100L, 8);
            seedMint(stores.mints(),
                    ProtectedCashRedemptionTestFixtures.MINT_B,
                    MINT_B_SOURCE, 500L, 4);
            stores.ledger().applyCommitted(new LedgerTransaction(
                    LEDGER_SEED, "protected cash ledger seed",
                    "Protected cash test seed", List.of(
                    new LedgerLeg(LedgerAccountId.system(
                            LedgerAccountType.ADMIN_SOURCE), -ISSUED_VALUE),
                    new LedgerLeg(LedgerAccountId.system(
                            LedgerAccountType
                                    .PROTECTED_CURRENCY_OUTSTANDING),
                            ISSUED_VALUE))));
            return stores;
        }

        private EscrowSavedDataMutationApplier applier() {
            return new EscrowSavedDataMutationApplier(transactions, ledger,
                    claims, audit, custody, mints);
        }

        private void seedPlayerBalances(long wallet, long reserved) {
            long total = Math.addExact(wallet, reserved);
            ledger.applyCommitted(new LedgerTransaction(UUID.fromString(
                    "68eb1dc9-eedf-4099-9b41-93cdad9358cc"),
                    "protected cash player balance seed",
                    "Protected cash player balance seed", List.of(
                    new LedgerLeg(LedgerAccountId.system(
                            LedgerAccountType.ADMIN_SOURCE),
                            Math.negateExact(total)),
                    new LedgerLeg(new LedgerAccountId(
                            LedgerAccountType.PLAYER_WALLET,
                            ProtectedCashRedemptionTestFixtures.PLAYER_ID
                                    .toString()), wallet),
                    new LedgerLeg(new LedgerAccountId(
                            LedgerAccountType.PLAYER_RESERVED,
                            ProtectedCashRedemptionTestFixtures.PLAYER_ID
                                    .toString()), reserved))));
        }

        private static void seedMint(ProtectedMintSavedData mints,
                                     UUID batchId,
                                     UUID sourceTransactionId,
                                     long denomination,
                                     int count) {
            Instant authorizedAt = ProtectedCashRedemptionTestFixtures.CREATED_AT
                    .minusSeconds(10L);
            String authorizeKey = "protected cash test authorize " + batchId;
            String serverEvidence = "server evidence " + batchId;
            ProtectedMintBatch batch = ProtectedMintBatch.plan(batchId,
                    sourceTransactionId, authorizeKey, denomination, count,
                    serverEvidence, authorizedAt,
                    (mintId, transactionId, minor, authorizedCount, server,
                     at) -> "checksum evidence " + mintId);
            mints.applyCommitted(ProtectedMintJournalEvent.authorize(batch));
            mints.applyCommitted(ProtectedMintJournalEvent.materialize(
                    sourceTransactionId, batchId,
                    "protected cash test materialize " + batchId, count,
                    authorizedAt.plusSeconds(1L)));
        }
    }
}
