package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.journal.JournalRecord;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForeignAtmWithdrawalCompositeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void validForeignCompositeAppliesAndReplaysWithoutProtectedMintState() {
        ForeignAtmWithdrawalCommit commit =
                ForeignAtmWithdrawalTestFixtures.commit();
        Stores stores = Stores.seeded(commit.amountMinorUnits());
        EscrowSavedDataMutationApplier applier = stores.applier(
                AtmWithdrawalApplyFaultInjector.NONE);
        EscrowJournalEvent event = event(commit);
        JournalRecord record = record(event, commit);

        assertEquals(EscrowPreflightResult.APPLY,
                applier.preflight(commit.requestId(), event));
        applier.apply(record, event);
        assertMaterialized(stores, commit);
        assertEquals(EscrowPreflightResult.REPLAY,
                applier.preflight(commit.requestId(), event));

        applier.apply(record, event);
        assertMaterialized(stores, commit);
        assertFalse(stores.mints().hasMaterializedState());
    }

    @Test
    void scopedLaneWritesOneRecordAndRejectsTheGeneralLane() throws Exception {
        ForeignAtmWithdrawalCommit commit =
                ForeignAtmWithdrawalTestFixtures.commit();
        Stores stores = Stores.seeded(commit.amountMinorUnits());
        Path path = temporaryDirectory.resolve("foreign-atm.wal");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(
                path, cursor,
                stores.applier(AtmWithdrawalApplyFaultInjector.NONE));
        assertEquals(EscrowRuntimeState.READY, coordinator.start());

        assertThrows(IllegalArgumentException.class,
                () -> coordinator.commit(commit.requestId(), event(commit)));
        EscrowCommitResult applied = coordinator.commitAtmWithdrawal(
                commit.requestId(), event(commit));
        assertFalse(applied.replayed());
        assertEquals(2L, cursor.lastAppliedSequence());
        long size = Files.size(path);

        EscrowCommitResult replayed = coordinator.commitAtmWithdrawal(
                commit.requestId(), event(commit));
        assertTrue(replayed.replayed());
        assertEquals(2L, cursor.lastAppliedSequence());
        assertEquals(size, Files.size(path));
        coordinator.close();
    }

    @Test
    void crashAfterEveryMutationConvergesOnReplay() {
        ForeignAtmWithdrawalCommit commit =
                ForeignAtmWithdrawalTestFixtures.commit();
        EscrowJournalEvent event = event(commit);
        int mutations = Math.addExact(2, commit.cashClaims().size());
        for (int failedStep = 0; failedStep < mutations; failedStep++) {
            Stores stores = Stores.seeded(commit.amountMinorUnits());
            AtomicBoolean failed = new AtomicBoolean();
            int selected = failedStep;
            EscrowSavedDataMutationApplier crashing = stores.applier(step -> {
                if (step == selected && failed.compareAndSet(false, true)) {
                    throw new IllegalStateException(
                            "Simulated foreign ATM materialization crash");
                }
            });

            assertThrows(IllegalStateException.class,
                    () -> crashing.apply(record(event, commit), event));
            assertTrue(failed.get());

            EscrowSavedDataMutationApplier recovery = stores.applier(
                    AtmWithdrawalApplyFaultInjector.NONE);
            recovery.apply(record(event, commit), event);
            assertEquals(EscrowPreflightResult.REPLAY,
                    recovery.preflight(commit.requestId(), event));
            assertMaterialized(stores, commit);
        }
    }

    @Test
    void insufficientWalletAndPartialMaterializationRejectPreflight() {
        ForeignAtmWithdrawalCommit commit =
                ForeignAtmWithdrawalTestFixtures.commit();
        Stores insufficient = Stores.seeded(
                commit.amountMinorUnits() - 1L);
        assertThrows(RuntimeException.class,
                () -> insufficient.applier(
                        AtmWithdrawalApplyFaultInjector.NONE)
                        .preflight(commit.requestId(), event(commit)));

        Stores partial = Stores.seeded(commit.amountMinorUnits());
        partial.transactions().applyCommitted(
                commit.committedTransaction());
        assertThrows(EscrowRuntimeException.class,
                () -> partial.applier(
                        AtmWithdrawalApplyFaultInjector.NONE)
                        .preflight(commit.requestId(), event(commit)));
    }

    private static EscrowJournalEvent event(
            ForeignAtmWithdrawalCommit commit
    ) {
        return new EscrowJournalEvent(
                EscrowJournalEventType.FOREIGN_ATM_WITHDRAWAL_COMMIT,
                ForeignAtmWithdrawalCommitCodec.encode(commit));
    }

    private static JournalRecord record(
            EscrowJournalEvent event,
            ForeignAtmWithdrawalCommit commit
    ) {
        return new JournalRecord(
                2L, commit.requestId(),
                EscrowStepIds.forEvent(commit.requestId(), event),
                EscrowJournalEventCodec.encode(event));
    }

    private static void assertMaterialized(
            Stores stores,
            ForeignAtmWithdrawalCommit commit
    ) {
        assertEquals(EscrowState.COMMIT_DECIDED,
                stores.transactions().getTransaction(
                        new EscrowTransactionId(commit.requestId())).state());
        assertEquals(0L, stores.ledger().balance(new LedgerAccountId(
                LedgerAccountType.PLAYER_WALLET,
                commit.playerId().toString())));
        assertEquals(commit.amountMinorUnits(), stores.ledger().balance(
                LedgerAccountId.system(
                        LedgerAccountType.FOREIGN_CURRENCY_SINK)));
        for (var claim : commit.cashClaims()) {
            assertEquals(claim,
                    stores.claims().getClaim(claim.claimId()));
        }
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
            ForeignAtmWithdrawalCommit commit =
                    ForeignAtmWithdrawalTestFixtures.commit();
            EscrowTransaction candidate = commit.committedTransaction();
            EscrowTransaction created = EscrowTransaction.create(
                    candidate.transactionId(),
                    candidate.parentTransactionId(),
                    candidate.requestKey(),
                    candidate.operation(),
                    candidate.participants(),
                    candidate.assetLots(),
                    ForeignAtmWithdrawalTestFixtures.CREATED,
                    candidate.configRevision(),
                    candidate.shopReference());
            stores.transactions.applyCommitted(created);
            EscrowTransaction validated = created.transitionTo(
                    EscrowState.VALIDATED,
                    ForeignAtmWithdrawalTestFixtures.CREATED.plusSeconds(1));
            stores.transactions.applyCommitted(validated);
            EscrowTransaction holding = validated.transitionTo(
                    EscrowState.HOLDING,
                    ForeignAtmWithdrawalTestFixtures.CREATED.plusSeconds(2));
            stores.transactions.applyCommitted(holding);
            stores.transactions.applyCommitted(holding.transitionTo(
                    EscrowState.HELD,
                    ForeignAtmWithdrawalTestFixtures.CREATED.plusSeconds(3)));
            UUID fundingId = UUID.nameUUIDFromBytes(
                    "foreign atm funding".getBytes(StandardCharsets.UTF_8));
            stores.ledger.applyCommitted(new LedgerTransaction(
                    fundingId, "foreign atm funding", "fund", List.of(
                            new LedgerLeg(LedgerAccountId.system(
                                    LedgerAccountType.ADMIN_SOURCE),
                                    Math.negateExact(walletBalance)),
                            new LedgerLeg(new LedgerAccountId(
                                    LedgerAccountType.PLAYER_WALLET,
                                    commit.playerId().toString()),
                                    walletBalance))));
            return stores;
        }

        private EscrowSavedDataMutationApplier applier(
                AtmWithdrawalApplyFaultInjector faults
        ) {
            return new EscrowSavedDataMutationApplier(
                    transactions, ledger, claims, audit, custody, mints,
                    MaintenanceRuntimeMutationHandler.unavailable(), faults);
        }
    }
}
