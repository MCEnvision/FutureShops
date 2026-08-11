package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.EscrowAdministrativeAuditSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.custody.CustodySavedData;
import com.enviouse.futureshops.server.escrow.journal.JournalRecord;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransactionId;
import com.enviouse.futureshops.server.escrow.store.EscrowTransactionSavedData;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPaymentCompositeTest {
    @Test
    void atomicPaymentAppliesAndExactReplayMovesNoValueTwice() {
        PlayerPaymentCommit commit = PlayerPaymentTestFixtures.overflow();
        Stores stores = Stores.seeded(commit);
        EscrowSavedDataMutationApplier applier = stores.applier(
                AtmWithdrawalApplyFaultInjector.NONE);
        EscrowJournalEvent event = event(commit);

        assertEquals(EscrowPreflightResult.APPLY,
                applier.preflight(commit.transactionId(), event));
        applier.apply(record(commit, event), event);
        assertMaterialized(stores, commit);
        assertEquals(EscrowPreflightResult.REPLAY,
                applier.preflight(commit.transactionId(), event));
        applier.apply(record(commit, event), event);
        assertMaterialized(stores, commit);
    }

    @Test
    void crashAfterEveryCompositeMutationConvergesOnWalReplay() {
        PlayerPaymentCommit commit = PlayerPaymentTestFixtures.overflow();
        EscrowJournalEvent event = event(commit);
        for (int failedStep = 0; failedStep < 3; failedStep++) {
            Stores stores = Stores.seeded(commit);
            AtomicBoolean failed = new AtomicBoolean();
            int selected = failedStep;
            EscrowSavedDataMutationApplier crashing = stores.applier(step -> {
                if (step == selected && failed.compareAndSet(false, true)) {
                    throw new IllegalStateException(
                            "Simulated payment materialization crash");
                }
            });
            assertThrows(IllegalStateException.class,
                    () -> crashing.apply(record(commit, event), event));
            assertTrue(failed.get());

            EscrowSavedDataMutationApplier recovery = stores.applier(
                    AtmWithdrawalApplyFaultInjector.NONE);
            recovery.apply(record(commit, event), event);
            assertEquals(EscrowPreflightResult.REPLAY,
                    recovery.preflight(commit.transactionId(), event));
            assertMaterialized(stores, commit);
        }
    }

    @Test
    void everyPartialMaterializationAndExtraClaimFailsClosed() {
        PlayerPaymentCommit commit = PlayerPaymentTestFixtures.overflow();
        EscrowJournalEvent event = event(commit);

        Stores transactionOnly = Stores.seeded(commit);
        transactionOnly.transactions()
                .applyFoldedAtomicCompletionCommitted(
                        commit.completedTransaction());
        assertThrows(EscrowRuntimeException.class,
                () -> transactionOnly.applier(
                        AtmWithdrawalApplyFaultInjector.NONE)
                        .preflight(commit.transactionId(), event));

        Stores ledgerOnly = Stores.seeded(commit);
        ledgerOnly.ledger().applyCommitted(commit.ledgerTransaction());
        assertThrows(EscrowRuntimeException.class,
                () -> ledgerOnly.applier(
                        AtmWithdrawalApplyFaultInjector.NONE)
                        .preflight(commit.transactionId(), event));

        Stores claimOnly = Stores.seeded(commit);
        claimOnly.claims().createCommitted(
                commit.overflowClaim().orElseThrow());
        assertThrows(EscrowRuntimeException.class,
                () -> claimOnly.applier(
                        AtmWithdrawalApplyFaultInjector.NONE)
                        .preflight(commit.transactionId(), event));

        Stores extraClaim = Stores.seeded(commit);
        extraClaim.claims().createCommitted(new EscrowClaim(
                UUID.randomUUID(), commit.transactionId(),
                commit.recipientId(),
                "player.payment.extra." + commit.transactionId(),
                ClaimKind.MONEY, 1L, 1L, new byte[0],
                ClaimStatus.PENDING, "Unexpected payment claim",
                PlayerPaymentTestFixtures.NOW,
                PlayerPaymentTestFixtures.NOW));
        assertThrows(EscrowRuntimeException.class,
                () -> extraClaim.applier(
                        AtmWithdrawalApplyFaultInjector.NONE)
                        .preflight(commit.transactionId(), event));
    }

    private static EscrowJournalEvent event(PlayerPaymentCommit commit) {
        return new EscrowJournalEvent(
                EscrowJournalEventType.PLAYER_PAYMENT_COMMIT,
                PlayerPaymentCommitCodec.encode(commit));
    }

    private static JournalRecord record(
            PlayerPaymentCommit commit,
            EscrowJournalEvent event
    ) {
        return new JournalRecord(
                2L, commit.transactionId(),
                EscrowStepIds.forEvent(commit.transactionId(), event),
                EscrowJournalEventCodec.encode(event));
    }

    private static void assertMaterialized(
            Stores stores,
            PlayerPaymentCommit commit
    ) {
        assertEquals(EscrowState.COMPLETED,
                stores.transactions().getTransaction(
                        new EscrowTransactionId(
                                commit.transactionId())).state());
        assertEquals(commit.payerBalanceAfterMinorUnits(),
                stores.ledger().balance(
                        PlayerPaymentCommit.walletAccount(
                                commit.payerId())));
        assertEquals(0L, stores.ledger().balance(
                PlayerPaymentCommit.debtAccount(
                        commit.recipientId())));
        assertEquals(100L, stores.ledger().balance(
                PlayerPaymentCommit.walletAccount(
                        commit.recipientId())));
        EscrowClaim claim = commit.overflowClaim().orElseThrow();
        assertEquals(claim, stores.claims().getClaim(claim.claimId()));
        assertEquals(commit.overflowClaimMinorUnits(),
                stores.ledger().balance(new LedgerAccountId(
                        LedgerAccountType.PLAYER_CLAIM,
                        claim.claimId().toString())));
    }

    private record Stores(
            EscrowTransactionSavedData transactions,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            EscrowAdministrativeAuditSavedData audit,
            CustodySavedData custody,
            ProtectedMintSavedData mints
    ) {
        private static Stores seeded(PlayerPaymentCommit commit) {
            Stores stores = new Stores(
                    new EscrowTransactionSavedData(),
                    new LedgerSavedData(),
                    new ClaimSavedData(),
                    new EscrowAdministrativeAuditSavedData(),
                    new CustodySavedData(),
                    new ProtectedMintSavedData());
            UUID fundingId = UUID.nameUUIDFromBytes(
                    "payment payer funding".getBytes(
                            StandardCharsets.UTF_8));
            stores.ledger.applyCommitted(new LedgerTransaction(
                    fundingId, "payment payer funding", "fund",
                    List.of(
                            new LedgerLeg(LedgerAccountId.system(
                                    LedgerAccountType.ADMIN_SOURCE),
                                    Math.negateExact(
                                            commit.payerWalletBeforeMinorUnits())),
                            new LedgerLeg(
                                    PlayerPaymentCommit.walletAccount(
                                            commit.payerId()),
                                    commit.payerWalletBeforeMinorUnits()))));
            if (commit.recipientDebtBeforeMinorUnits() < 0L) {
                long debt = commit.recipientDebtBeforeMinorUnits();
                UUID debtId = UUID.nameUUIDFromBytes(
                        "payment recipient debt".getBytes(
                                StandardCharsets.UTF_8));
                stores.ledger.applyCommitted(new LedgerTransaction(
                        debtId, "payment recipient debt", "debt",
                        List.of(
                                new LedgerLeg(
                                        PlayerPaymentCommit.debtAccount(
                                                commit.recipientId()), debt),
                                new LedgerLeg(LedgerAccountId.system(
                                        LedgerAccountType.ADMIN_SINK),
                                        Math.negateExact(debt)))));
            }
            if (commit.recipientWalletBeforeMinorUnits() > 0L) {
                UUID walletId = UUID.nameUUIDFromBytes(
                        "payment recipient wallet".getBytes(
                                StandardCharsets.UTF_8));
                stores.ledger.applyCommitted(new LedgerTransaction(
                        walletId, "payment recipient wallet", "fund",
                        List.of(
                                new LedgerLeg(LedgerAccountId.system(
                                        LedgerAccountType.ADMIN_SOURCE),
                                        Math.negateExact(commit
                                                .recipientWalletBeforeMinorUnits())),
                                new LedgerLeg(
                                        PlayerPaymentCommit.walletAccount(
                                                commit.recipientId()),
                                        commit.recipientWalletBeforeMinorUnits()))));
            }
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
