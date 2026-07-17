package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.journal.JournalRecord;
import com.enviouse.futureshops.server.escrow.journal.WriteAheadJournal;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmWithdrawalCompositeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void validCompositeAppliesAndReplaysEveryDomainTogether() {
        AtmWithdrawalCommit commit = AtmWithdrawalTestFixtures.commit();
        Stores stores = Stores.seeded(commit.amountMinorUnits());
        EscrowSavedDataMutationApplier applier = stores.applier(
                AtmWithdrawalApplyFaultInjector.NONE);
        EscrowJournalEvent event = event(commit);
        JournalRecord record = record(event, commit);

        assertEquals(EscrowPreflightResult.APPLY,
                applier.preflight(commit.transactionId(), event));
        applier.apply(record, event);
        assertMaterialized(stores, commit);
        assertEquals(EscrowPreflightResult.REPLAY,
                applier.preflight(commit.transactionId(), event));

        applier.apply(record, event);
        assertMaterialized(stores, commit);
        assertTrue(stores.mints().conservation().conserved());
    }

    @Test
    void insufficientWalletAndPartialStateRejectBeforeJournalAppend() throws Exception {
        AtmWithdrawalCommit commit = AtmWithdrawalTestFixtures.commit();
        Path insufficientPath = temporaryDirectory.resolve("insufficient.wal");
        Stores insufficient = Stores.seeded(commit.amountMinorUnits() - 1L);
        EscrowRuntimeSavedData insufficientCursor = new EscrowRuntimeSavedData();
        EscrowRuntimeCoordinator insufficientCoordinator = new EscrowRuntimeCoordinator(
                insufficientPath, insufficientCursor,
                insufficient.applier(AtmWithdrawalApplyFaultInjector.NONE));
        assertEquals(EscrowRuntimeState.READY, insufficientCoordinator.start());
        long insufficientBytes = Files.size(insufficientPath);

        assertThrows(RuntimeException.class, () -> insufficientCoordinator
                .commitAtmWithdrawal(commit.transactionId(), event(commit)));
        assertEquals(1L, insufficientCursor.lastAppliedSequence());
        assertEquals(insufficientBytes, Files.size(insufficientPath));
        assertEquals(EscrowRuntimeState.READY, insufficientCoordinator.state());
        insufficientCoordinator.close();

        Path partialPath = temporaryDirectory.resolve("partial.wal");
        Stores partial = Stores.seeded(commit.amountMinorUnits());
        partial.transactions().applyCommitted(commit.committedTransaction());
        EscrowRuntimeSavedData partialCursor = new EscrowRuntimeSavedData();
        EscrowRuntimeCoordinator partialCoordinator = new EscrowRuntimeCoordinator(
                partialPath, partialCursor,
                partial.applier(AtmWithdrawalApplyFaultInjector.NONE));
        assertEquals(EscrowRuntimeState.READY, partialCoordinator.start());
        long partialBytes = Files.size(partialPath);

        assertThrows(EscrowRuntimeException.class, () -> partialCoordinator
                .commitAtmWithdrawal(commit.transactionId(), event(commit)));
        assertEquals(1L, partialCursor.lastAppliedSequence());
        assertEquals(partialBytes, Files.size(partialPath));
        assertEquals(EscrowRuntimeState.READY, partialCoordinator.state());
        partialCoordinator.close();
    }

    @Test
    void scopedCompositeUsesOneForcedWalRecordAndOneCursorAdvance() throws Exception {
        AtmWithdrawalCommit commit = AtmWithdrawalTestFixtures.commit();
        Stores stores = Stores.seeded(commit.amountMinorUnits());
        Path path = temporaryDirectory.resolve("one-record.wal");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(
                path, cursor, stores.applier(AtmWithdrawalApplyFaultInjector.NONE));
        assertEquals(EscrowRuntimeState.READY, coordinator.start());

        assertThrows(IllegalArgumentException.class, () -> coordinator.commit(
                commit.transactionId(), event(commit)));
        EscrowCommitResult applied = coordinator.commitAtmWithdrawal(
                commit.transactionId(), event(commit));

        assertFalse(applied.replayed());
        assertEquals(2L, applied.record().orElseThrow().sequence());
        assertEquals(2L, cursor.lastAppliedSequence());
        long committedBytes = Files.size(path);
        EscrowCommitResult replayed = coordinator.commitAtmWithdrawal(
                commit.transactionId(), event(commit));
        assertTrue(replayed.replayed());
        assertEquals(2L, cursor.lastAppliedSequence());
        assertEquals(committedBytes, Files.size(path));
        coordinator.close();

        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            assertEquals(2, journal.replayBatch(
                    0L, 1L, 10,
                    WriteAheadJournal.MAX_REPLAY_BATCH_BYTES).records().size());
        }
    }

    @Test
    void crashAfterEveryInMemoryMutationConvergesOnJournalReplay() {
        AtmWithdrawalCommit commit = AtmWithdrawalTestFixtures.commit();
        EscrowJournalEvent event = event(commit);
        int componentCount = Math.addExact(2,
                Math.addExact(commit.mintIssues().size(), commit.cashClaims().size()));
        for (int failedStep = 0; failedStep < componentCount; failedStep++) {
            Stores stores = Stores.seeded(commit.amountMinorUnits());
            AtomicBoolean failed = new AtomicBoolean();
            int selectedStep = failedStep;
            EscrowSavedDataMutationApplier crashing = stores.applier(step -> {
                if (step == selectedStep && failed.compareAndSet(false, true)) {
                    throw new IllegalStateException("Simulated ATM materialization crash");
                }
            });
            JournalRecord record = record(event, commit);

            assertThrows(IllegalStateException.class,
                    () -> crashing.apply(record, event));
            assertTrue(failed.get());

            EscrowSavedDataMutationApplier recovery = stores.applier(
                    AtmWithdrawalApplyFaultInjector.NONE);
            recovery.apply(record, event);
            assertEquals(EscrowPreflightResult.REPLAY,
                    recovery.preflight(commit.transactionId(), event));
            assertMaterialized(stores, commit);
        }
    }

    @Test
    void closeAndReopenRecoversEveryPersistedPartialMaterialization() throws Exception {
        AtmWithdrawalCommit commit = AtmWithdrawalTestFixtures.commit();
        EscrowJournalEvent event = event(commit);
        int componentCount = Math.addExact(2,
                Math.addExact(commit.mintIssues().size(), commit.cashClaims().size()));
        for (int failedStep = 0; failedStep < componentCount; failedStep++) {
            Path path = temporaryDirectory.resolve(
                    "reopen-after-step-" + failedStep + ".wal");
            Stores liveStores = Stores.seeded(commit.amountMinorUnits());
            EscrowRuntimeSavedData liveCursor = new EscrowRuntimeSavedData();
            AtomicBoolean failed = new AtomicBoolean();
            int selectedStep = failedStep;
            EscrowRuntimeCoordinator crashing = new EscrowRuntimeCoordinator(
                    path, liveCursor, liveStores.applier(step -> {
                if (step == selectedStep && failed.compareAndSet(false, true)) {
                    throw new IllegalStateException(
                            "Simulated ATM materialization crash");
                }
            }));
            assertEquals(EscrowRuntimeState.READY, crashing.start());

            assertThrows(EscrowRuntimeException.class, () -> crashing
                    .commitAtmWithdrawal(commit.transactionId(), event));
            assertTrue(failed.get());
            assertEquals(EscrowRuntimeState.MAINTENANCE, crashing.state());
            assertEquals(1L, liveCursor.lastAppliedSequence());
            long journalBytes = Files.size(path);
            crashing.close();
            assertEquals(EscrowRuntimeState.STOPPED, crashing.state());

            Stores restoredStores = liveStores.reloaded();
            EscrowRuntimeSavedData restoredCursor = EscrowRuntimeSavedData.load(
                    liveCursor.save(new CompoundTag()));
            EscrowRuntimeCoordinator recovered = new EscrowRuntimeCoordinator(
                    path, restoredCursor,
                    restoredStores.applier(AtmWithdrawalApplyFaultInjector.NONE));
            assertEquals(EscrowRuntimeState.READY, recovered.start());
            assertEquals(2L, restoredCursor.lastAppliedSequence());
            assertEquals(journalBytes, Files.size(path));
            assertMaterialized(restoredStores, commit);

            EscrowCommitResult replay = recovered.commitAtmWithdrawal(
                    commit.transactionId(), event);
            assertTrue(replay.replayed());
            assertEquals(2L, restoredCursor.lastAppliedSequence());
            assertEquals(journalBytes, Files.size(path));
            recovered.close();
        }
    }

    @Test
    void preflightRejectsEveryPartialDomainShapeWithoutFurtherMutation() {
        AtmWithdrawalCommit commit = AtmWithdrawalTestFixtures.commit();
        EscrowJournalEvent event = event(commit);
        int componentCount = Math.addExact(2,
                Math.addExact(commit.mintIssues().size(), commit.cashClaims().size()));
        for (int selected = 0; selected < componentCount; selected++) {
            Stores stores = Stores.seeded(commit.amountMinorUnits());
            materializeOne(stores, commit, selected);
            EscrowSavedDataMutationApplier applier = stores.applier(
                    AtmWithdrawalApplyFaultInjector.NONE);

            assertThrows(EscrowRuntimeException.class,
                    () -> applier.preflight(commit.transactionId(), event));
            assertThrows(EscrowRuntimeException.class,
                    () -> applier.preflight(commit.transactionId(), event));
        }
    }

    private static EscrowJournalEvent event(AtmWithdrawalCommit commit) {
        return new EscrowJournalEvent(
                EscrowJournalEventType.ATM_WITHDRAWAL_COMMIT,
                AtmWithdrawalCommitCodec.encode(commit));
    }

    private static JournalRecord record(EscrowJournalEvent event,
                                        AtmWithdrawalCommit commit) {
        return new JournalRecord(2L, commit.transactionId(),
                EscrowStepIds.forEvent(commit.transactionId(), event),
                EscrowJournalEventCodec.encode(event));
    }

    private static void assertMaterialized(Stores stores,
                                           AtmWithdrawalCommit commit) {
        assertEquals(EscrowState.COMMIT_DECIDED,
                stores.transactions().getTransaction(
                        new EscrowTransactionId(commit.transactionId())).state());
        assertEquals(0L, stores.ledger().balance(new LedgerAccountId(
                LedgerAccountType.PLAYER_WALLET, commit.playerId().toString())));
        assertEquals(commit.amountMinorUnits(), stores.ledger().balance(
                LedgerAccountId.system(
                        LedgerAccountType.PROTECTED_CURRENCY_OUTSTANDING)));
        for (ProtectedMintJournalEvent issue : commit.mintIssues()) {
            assertEquals(issue.quantity(), stores.mints().getBatch(
                    issue.batch().orElseThrow().batchId()).availableQuantity());
        }
        for (var claim : commit.cashClaims()) {
            assertEquals(claim, stores.claims().getClaim(claim.claimId()));
        }
    }

    private static void materializeOne(Stores stores,
                                       AtmWithdrawalCommit commit,
                                       int selected) {
        if (selected == 0) {
            stores.transactions().applyCommitted(commit.committedTransaction());
            return;
        }
        if (selected == 1) {
            stores.ledger().applyCommitted(commit.ledgerTransaction());
            return;
        }
        int issueIndex = selected - 2;
        if (issueIndex < commit.mintIssues().size()) {
            stores.mints().applyCommitted(commit.mintIssues().get(issueIndex));
            return;
        }
        int claimIndex = issueIndex - commit.mintIssues().size();
        stores.claims().createCommitted(commit.cashClaims().get(claimIndex));
    }

    private record Stores(
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            EscrowAdministrativeAuditSavedData audit,
            CustodySavedData custody,
            ProtectedMintSavedData mints
    ) {
        private static Stores seeded(long walletBalance) {
            Stores stores = new Stores(
                    new EscrowTransactionSavedData(),
                    new LedgerSavedData(),
                    new ClaimSavedData(),
                    new EscrowAdministrativeAuditSavedData(),
                    new CustodySavedData(),
                    new ProtectedMintSavedData());
            AtmWithdrawalCommit commit = AtmWithdrawalTestFixtures.commit();
            var candidate = commit.committedTransaction();
            var created = com.enviouse.futureshops.server.escrow.model.EscrowTransaction.create(
                    candidate.transactionId(), candidate.parentTransactionId(),
                    candidate.requestKey(), candidate.operation(), candidate.participants(),
                    candidate.assetLots(), AtmWithdrawalTestFixtures.CREATED,
                    candidate.configRevision(), candidate.shopReference());
            stores.transactions.applyCommitted(created);
            var validated = created.transitionTo(
                    EscrowState.VALIDATED,
                    AtmWithdrawalTestFixtures.CREATED.plusSeconds(1));
            stores.transactions.applyCommitted(validated);
            var holding = validated.transitionTo(
                    EscrowState.HOLDING,
                    AtmWithdrawalTestFixtures.CREATED.plusSeconds(2));
            stores.transactions.applyCommitted(holding);
            stores.transactions.applyCommitted(holding.transitionTo(
                    EscrowState.HELD,
                    AtmWithdrawalTestFixtures.CREATED.plusSeconds(3)));
            stores.ledger.applyCommitted(
                    AtmWithdrawalTestFixtures.fundWallet(walletBalance, "seed"));
            return stores;
        }

        private EscrowSavedDataMutationApplier applier(
                AtmWithdrawalApplyFaultInjector faultInjector
        ) {
            return new EscrowSavedDataMutationApplier(
                    transactions, ledger, claims, audit, custody, mints,
                    MaintenanceRuntimeMutationHandler.unavailable(), faultInjector);
        }

        private Stores reloaded() {
            return new Stores(
                    EscrowTransactionSavedData.load(
                            transactions.save(new CompoundTag())),
                    LedgerSavedData.load(ledger.save(new CompoundTag())),
                    ClaimSavedData.load(claims.save(new CompoundTag())),
                    EscrowAdministrativeAuditSavedData.load(
                            audit.save(new CompoundTag())),
                    CustodySavedData.load(custody.save(new CompoundTag())),
                    ProtectedMintSavedData.load(mints.save(new CompoundTag())));
        }
    }
}
