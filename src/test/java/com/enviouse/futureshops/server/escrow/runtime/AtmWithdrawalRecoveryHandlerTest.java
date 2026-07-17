package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLot;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowError;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmWithdrawalRecoveryHandlerTest {
    private static final Instant RECOVERED_AT =
            AtmWithdrawalTestFixtures.COMMITTED.plusSeconds(60);
    private static final Clock CLOCK = Clock.fixed(RECOVERED_AT, ZoneOffset.UTC);

    @Test
    void everyPrecommitStateDurablyReachesRefundedWithoutLedgerMutation() {
        Map<EscrowState, List<EscrowState>> expectations = Map.of(
                EscrowState.CREATED, List.of(
                        EscrowState.ABORTING,
                        EscrowState.REFUND_PENDING,
                        EscrowState.REFUNDED),
                EscrowState.VALIDATED, List.of(
                        EscrowState.ABORTING,
                        EscrowState.REFUND_PENDING,
                        EscrowState.REFUNDED),
                EscrowState.HOLDING, List.of(
                        EscrowState.ABORTING,
                        EscrowState.REFUND_PENDING,
                        EscrowState.REFUNDED),
                EscrowState.HELD, List.of(
                        EscrowState.ABORTING,
                        EscrowState.REFUND_PENDING,
                        EscrowState.REFUNDED),
                EscrowState.ABORTING, List.of(
                        EscrowState.REFUND_PENDING,
                        EscrowState.REFUNDED),
                EscrowState.REFUND_PENDING, List.of(EscrowState.REFUNDED));

        for (Map.Entry<EscrowState, List<EscrowState>> expectation
                : expectations.entrySet()) {
            Harness harness = new Harness(transactionAt(expectation.getKey()));
            AtmWithdrawalCommit composite = AtmWithdrawalTestFixtures.commit();
            harness.ledger.applyCommitted(AtmWithdrawalTestFixtures.fundWallet(
                    composite.amountMinorUnits(), expectation.getKey().name()));
            Map<?, ?> balancesBefore = harness.ledger.snapshotBalances();

            EscrowRecoveryAttempt result = harness.handler.recover(harness.current());

            assertEquals(EscrowRecoveryDisposition.RESOLVED, result.disposition());
            assertEquals(expectation.getValue(), harness.committedStates());
            assertEquals(EscrowState.REFUNDED, harness.current().state());
            assertEquals(balancesBefore, harness.ledger.snapshotBalances());
            assertTrue(harness.claims.claimsForTransaction(
                    AtmWithdrawalTestFixtures.TRANSACTION_ID).isEmpty());
            assertTrue(harness.protectedMints.liabilitySnapshot().batches().isEmpty());
        }
    }

    @Test
    void committedStatesAdvanceOnlyAfterExactEvidenceVerification() {
        Map<EscrowState, EscrowState> expectations = Map.of(
                EscrowState.COMMIT_DECIDED, EscrowState.COMMITTED,
                EscrowState.COMMITTED, EscrowState.CLAIMS_CREATED,
                EscrowState.CLAIMS_CREATED, EscrowState.COMPLETED);

        for (Map.Entry<EscrowState, EscrowState> expectation
                : expectations.entrySet()) {
            Harness harness = new Harness(transactionAt(expectation.getKey()));
            harness.seedExactEvidence();

            EscrowRecoveryAttempt result = harness.handler.recover(harness.current());

            assertEquals(expectation.getValue(), harness.current().state());
            assertEquals(List.of(expectation.getValue()), harness.committedStates());
            assertEquals(expectation.getValue().isTerminal()
                            ? EscrowRecoveryDisposition.RESOLVED
                            : EscrowRecoveryDisposition.PROGRESSED,
                    result.disposition());
        }
    }

    @Test
    void recoveryRequiredResumesEveryRecordedStateWithoutSkipping() {
        List<EscrowState> resumeStates = List.of(
                EscrowState.HOLDING,
                EscrowState.HELD,
                EscrowState.REFUND_PENDING,
                EscrowState.COMMIT_DECIDED,
                EscrowState.COMMITTED,
                EscrowState.CLAIMS_CREATED);

        for (EscrowState resumeState : resumeStates) {
            Harness harness = new Harness(recoveryRequiredFor(resumeState, 3));
            if (resumeState.requiresCommitDecision()) {
                harness.seedExactEvidence();
            }

            EscrowRecoveryAttempt result = harness.handler.recover(harness.current());

            assertEquals(EscrowRecoveryDisposition.PROGRESSED, result.disposition());
            assertEquals(resumeState, harness.current().state());
            assertEquals(List.of(resumeState), harness.committedStates());
            assertTrue(!harness.current().retryMetadata().isScheduled());
        }
    }

    @Test
    void terminalAndManualReviewStatesDoNotWriteAgain() {
        for (EscrowState state : List.of(
                EscrowState.COMPLETED,
                EscrowState.REFUNDED,
                EscrowState.MANUAL_REVIEW)) {
            Harness harness = new Harness(transactionAt(state));

            EscrowRecoveryAttempt result = harness.handler.recover(harness.current());

            assertEquals(state == EscrowState.MANUAL_REVIEW
                            ? EscrowRecoveryDisposition.MANUAL_REVIEW
                            : EscrowRecoveryDisposition.RESOLVED,
                    result.disposition());
            assertTrue(harness.commits.isEmpty());
            assertEquals(state, harness.current().state());
        }
    }

    @Test
    void missingAndConflictingCompositeEvidenceDurablyEntersManualReview() {
        assertManualReview(harness -> harness.seedEvidence(
                false,
                AtmWithdrawalTestFixtures.commit().mintIssues(),
                AtmWithdrawalTestFixtures.commit().cashClaims()));
        assertManualReview(harness -> {
            AtmWithdrawalCommit composite = AtmWithdrawalTestFixtures.commit();
            harness.seedEvidence(
                    true,
                    composite.mintIssues().subList(1, composite.mintIssues().size()),
                    composite.cashClaims());
        });
        assertManualReview(harness -> {
            AtmWithdrawalCommit composite = AtmWithdrawalTestFixtures.commit();
            harness.seedEvidence(
                    true,
                    composite.mintIssues(),
                    composite.cashClaims().subList(1, composite.cashClaims().size()));
        });
        assertManualReview(Harness::seedWrongLedgerReceipt);
        assertManualReview(Harness::seedExtraMintBatch);
        assertManualReview(Harness::seedClaimWithWrongLabel);
        assertManualReview(harness -> harness.seedExactEvidence(),
                AtmWithdrawalRecoveryHandlerTest::transactionWithConflictingAssetPayload);
    }

    @Test
    void unexpectedCommittedEvidenceBeforeDecisionNeverRefundsTheWallet() {
        for (EscrowState state : List.of(
                EscrowState.CREATED,
                EscrowState.VALIDATED,
                EscrowState.HOLDING,
                EscrowState.HELD)) {
            Harness harness = new Harness(transactionAt(state));
            harness.seedExactEvidence();
            Map<?, ?> balancesBefore = harness.ledger.snapshotBalances();

            EscrowRecoveryAttempt result = harness.handler.recover(harness.current());

            assertEquals(EscrowRecoveryDisposition.MANUAL_REVIEW,
                    result.disposition());
            assertEquals(EscrowState.MANUAL_REVIEW, harness.current().state());
            assertEquals(state == EscrowState.CREATED
                            || state == EscrowState.VALIDATED
                            ? List.of(
                                    EscrowState.ABORTING,
                                    EscrowState.RECOVERY_REQUIRED,
                                    EscrowState.MANUAL_REVIEW)
                            : List.of(
                                    EscrowState.RECOVERY_REQUIRED,
                                    EscrowState.MANUAL_REVIEW),
                    harness.committedStates());
            assertEquals(balancesBefore, harness.ledger.snapshotBalances());
        }
    }

    @Test
    void everyPostdecisionStateRejectsMissingEvidence() {
        for (EscrowState state : List.of(
                EscrowState.COMMIT_DECIDED,
                EscrowState.COMMITTED,
                EscrowState.CLAIMS_CREATED)) {
            Harness harness = new Harness(transactionAt(state));

            EscrowRecoveryAttempt result = harness.handler.recover(harness.current());

            assertEquals(EscrowRecoveryDisposition.MANUAL_REVIEW,
                    result.disposition());
            assertEquals(EscrowState.MANUAL_REVIEW, harness.current().state());
            assertEquals(List.of(
                            EscrowState.RECOVERY_REQUIRED,
                            EscrowState.MANUAL_REVIEW),
                    harness.committedStates());
        }
    }

    @Test
    void recoveryEvidenceFailureMovesDirectlyToManualReview() {
        Harness harness = new Harness(recoveryRequiredFor(
                EscrowState.COMMIT_DECIDED, 3));

        EscrowRecoveryAttempt result = harness.handler.recover(harness.current());

        assertEquals(EscrowRecoveryDisposition.MANUAL_REVIEW, result.disposition());
        assertEquals(EscrowState.MANUAL_REVIEW, harness.current().state());
        assertEquals(List.of(EscrowState.MANUAL_REVIEW), harness.committedStates());
    }

    @Test
    void exhaustedRetryMetadataLeavesClearBlockedEvidence() {
        EscrowTransaction recovery = recoveryRequiredFor(
                EscrowState.COMMIT_DECIDED, 1);
        EscrowTransaction resumed = recovery.transitionTo(
                EscrowState.COMMIT_DECIDED,
                recovery.timestamps().updatedAt().plusSeconds(1));
        Harness harness = new Harness(resumed);

        EscrowRecoveryAttempt result = harness.handler.recover(harness.current());

        assertEquals(EscrowRecoveryDisposition.MANUAL_REVIEW, result.disposition());
        assertEquals(EscrowState.COMMIT_DECIDED, harness.current().state());
        assertTrue(harness.commits.isEmpty());
        assertTrue(result.detail().contains("Durable state transition was blocked"));
    }

    private static void assertManualReview(EvidenceSeeder seeder) {
        assertManualReview(seeder, transaction -> transaction);
    }

    private static void assertManualReview(
            EvidenceSeeder seeder,
            TransactionMutation mutation
    ) {
        EscrowTransaction transaction = mutation.apply(
                transactionAt(EscrowState.COMMIT_DECIDED));
        Harness harness = new Harness(transaction);
        seeder.seed(harness);

        EscrowRecoveryAttempt result = harness.handler.recover(harness.current());

        assertEquals(EscrowRecoveryDisposition.MANUAL_REVIEW, result.disposition());
        assertEquals(EscrowState.MANUAL_REVIEW, harness.current().state());
        assertEquals(List.of(
                        EscrowState.RECOVERY_REQUIRED,
                        EscrowState.MANUAL_REVIEW),
                harness.committedStates());
        assertEquals("ATM_RECOVERY_EVIDENCE_INVALID",
                harness.current().lastError().orElseThrow().code());
    }

    private static EscrowTransaction transactionAt(EscrowState target) {
        AtmWithdrawalCommit composite = AtmWithdrawalTestFixtures.commit();
        EscrowTransaction decision = composite.committedTransaction();
        EscrowTransaction created = EscrowTransaction.create(
                decision.transactionId(),
                decision.parentTransactionId(),
                decision.requestKey(),
                decision.operation(),
                decision.participants(),
                decision.assetLots(),
                decision.timestamps().createdAt(),
                decision.configRevision(),
                decision.shopReference());
        if (target == EscrowState.CREATED) {
            return created;
        }
        EscrowTransaction validated = created.transitionTo(
                EscrowState.VALIDATED,
                AtmWithdrawalTestFixtures.CREATED.plusSeconds(1));
        if (target == EscrowState.VALIDATED) {
            return validated;
        }
        EscrowTransaction holding = validated.transitionTo(
                EscrowState.HOLDING,
                AtmWithdrawalTestFixtures.CREATED.plusSeconds(2));
        if (target == EscrowState.HOLDING) {
            return holding;
        }
        EscrowTransaction held = holding.transitionTo(
                EscrowState.HELD,
                AtmWithdrawalTestFixtures.CREATED.plusSeconds(3));
        if (target == EscrowState.HELD) {
            return held;
        }
        if (target == EscrowState.ABORTING
                || target == EscrowState.REFUND_PENDING
                || target == EscrowState.REFUNDED) {
            EscrowTransaction aborting = held.transitionTo(
                    EscrowState.ABORTING,
                    AtmWithdrawalTestFixtures.COMMITTED);
            if (target == EscrowState.ABORTING) {
                return aborting;
            }
            EscrowTransaction refundPending = aborting.transitionTo(
                    EscrowState.REFUND_PENDING,
                    AtmWithdrawalTestFixtures.COMMITTED.plusSeconds(1));
            return target == EscrowState.REFUND_PENDING
                    ? refundPending
                    : refundPending.transitionTo(
                            EscrowState.REFUNDED,
                            AtmWithdrawalTestFixtures.COMMITTED.plusSeconds(2));
        }
        EscrowTransaction commitDecided = held.transitionTo(
                EscrowState.COMMIT_DECIDED,
                AtmWithdrawalTestFixtures.COMMITTED);
        if (target == EscrowState.COMMIT_DECIDED) {
            return commitDecided;
        }
        EscrowTransaction committed = commitDecided.transitionTo(
                EscrowState.COMMITTED,
                AtmWithdrawalTestFixtures.COMMITTED.plusSeconds(1));
        if (target == EscrowState.COMMITTED) {
            return committed;
        }
        EscrowTransaction claimsCreated = committed.transitionTo(
                EscrowState.CLAIMS_CREATED,
                AtmWithdrawalTestFixtures.COMMITTED.plusSeconds(2));
        if (target == EscrowState.CLAIMS_CREATED) {
            return claimsCreated;
        }
        if (target == EscrowState.COMPLETED) {
            return claimsCreated.transitionTo(
                    EscrowState.COMPLETED,
                    AtmWithdrawalTestFixtures.COMMITTED.plusSeconds(3));
        }
        if (target == EscrowState.MANUAL_REVIEW) {
            Instant at = AtmWithdrawalTestFixtures.COMMITTED.plusSeconds(3);
            EscrowTransaction recovery = claimsCreated.requireRecovery(
                    recoveryError(at), 3, at, at);
            return recovery.transitionTo(EscrowState.MANUAL_REVIEW, at);
        }
        throw new IllegalArgumentException("Unsupported ATM test state " + target);
    }

    private static EscrowTransaction recoveryRequiredFor(
            EscrowState resumeState,
            int maximumAttempts
    ) {
        EscrowTransaction source = transactionAt(resumeState);
        Instant at = source.timestamps().updatedAt().plusSeconds(1);
        return source.requireRecovery(
                recoveryError(at), maximumAttempts, at, at);
    }

    private static EscrowError recoveryError(Instant at) {
        return new EscrowError(
                "ATM_TEST_RECOVERY",
                "ATM test recovery",
                true,
                at,
                Map.of("test", "recovery"));
    }

    private static EscrowTransaction transactionWithConflictingAssetPayload(
            EscrowTransaction transaction
    ) {
        List<EscrowAssetLot> lots = new ArrayList<>(transaction.assetLots());
        List<Integer> cashIndexes = new ArrayList<>();
        for (int index = 0; index < lots.size(); index++) {
            if (lots.get(index).type()
                    == EscrowAssetLotType.PROTECTED_PHYSICAL_CURRENCY) {
                cashIndexes.add(index);
            }
        }
        EscrowAssetLot original = lots.get(cashIndexes.get(0));
        byte[] conflictingPayload = lots.get(cashIndexes.get(1)).serializedPayload();
        lots.set(cashIndexes.get(0), new EscrowAssetLot(
                original.lotId(),
                original.type(),
                original.protectionLevel(),
                original.source(),
                original.destination(),
                original.quantity(),
                original.money(),
                conflictingPayload,
                original.attributes()));
        return new EscrowTransaction(
                transaction.transactionId(),
                transaction.parentTransactionId(),
                transaction.requestKey(),
                transaction.operation(),
                transaction.state(),
                transaction.participants(),
                lots,
                transaction.timestamps(),
                transaction.revision(),
                transaction.configRevision(),
                transaction.lastError(),
                transaction.retryMetadata(),
                transaction.shopReference());
    }

    @FunctionalInterface
    private interface EvidenceSeeder {
        void seed(Harness harness);
    }

    @FunctionalInterface
    private interface TransactionMutation {
        EscrowTransaction apply(EscrowTransaction transaction);
    }

    private static final class Harness {
        private final LedgerSavedData ledger = new LedgerSavedData();
        private final ClaimSavedData claims = new ClaimSavedData();
        private final ProtectedMintSavedData protectedMints =
                new ProtectedMintSavedData();
        private final List<EscrowTransaction> commits = new ArrayList<>();
        private final AtmWithdrawalRecoveryHandler handler;
        private EscrowTransaction current;

        private Harness(EscrowTransaction initial) {
            current = initial;
            handler = new AtmWithdrawalRecoveryHandler(
                    this::commit,
                    ledger,
                    claims,
                    protectedMints,
                    CLOCK);
        }

        private EscrowTransaction current() {
            return current;
        }

        private void commit(EscrowTransaction transaction) {
            current = transaction;
            commits.add(transaction);
        }

        private List<EscrowState> committedStates() {
            return commits.stream().map(EscrowTransaction::state).toList();
        }

        private void seedExactEvidence() {
            AtmWithdrawalCommit composite = AtmWithdrawalTestFixtures.commit();
            seedEvidence(true, composite.mintIssues(), composite.cashClaims());
        }

        private void seedEvidence(
                boolean includeLedger,
                List<ProtectedMintJournalEvent> issues,
                List<EscrowClaim> cashClaims
        ) {
            AtmWithdrawalCommit composite = AtmWithdrawalTestFixtures.commit();
            if (includeLedger) {
                ledger.applyCommitted(AtmWithdrawalTestFixtures.fundWallet(
                        composite.amountMinorUnits(), "recovery evidence"));
                ledger.applyCommitted(composite.ledgerTransaction());
            }
            issues.forEach(protectedMints::applyCommitted);
            cashClaims.forEach(claims::createCommitted);
        }

        private void seedWrongLedgerReceipt() {
            AtmWithdrawalCommit composite = AtmWithdrawalTestFixtures.commit();
            ledger.applyCommitted(AtmWithdrawalTestFixtures.fundWallet(
                    composite.amountMinorUnits(), "wrong receipt"));
            ledger.applyCommitted(new LedgerTransaction(
                    composite.ledgerTransaction().transactionId(),
                    composite.ledgerTransaction().idempotencyKey(),
                    "Wrong ATM reason",
                    composite.ledgerTransaction().legs()));
            composite.mintIssues().forEach(protectedMints::applyCommitted);
            composite.cashClaims().forEach(claims::createCommitted);
        }

        private void seedExtraMintBatch() {
            AtmWithdrawalCommit composite = AtmWithdrawalTestFixtures.commit();
            List<ProtectedMintJournalEvent> issues = new ArrayList<>(
                    composite.mintIssues());
            issues.add(AtmWithdrawalTestFixtures.issue("extra", 50L, 1));
            seedEvidence(true, issues, composite.cashClaims());
        }

        private void seedClaimWithWrongLabel() {
            AtmWithdrawalCommit composite = AtmWithdrawalTestFixtures.commit();
            List<EscrowClaim> cashClaims = new ArrayList<>(composite.cashClaims());
            EscrowClaim original = cashClaims.get(0);
            cashClaims.set(0, new EscrowClaim(
                    original.claimId(),
                    original.transactionId(),
                    original.ownerId(),
                    original.sourceKey(),
                    original.kind(),
                    original.originalUnits(),
                    original.remainingUnits(),
                    original.payload(),
                    original.status(),
                    "Conflicting protected cash label",
                    original.createdAt(),
                    original.updatedAt()));
            seedEvidence(true, composite.mintIssues(), cashClaims);
        }
    }
}
