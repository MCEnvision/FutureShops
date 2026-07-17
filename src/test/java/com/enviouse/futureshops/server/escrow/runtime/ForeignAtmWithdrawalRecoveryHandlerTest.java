package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForeignAtmWithdrawalRecoveryHandlerTest {
    private static final Instant RECOVERED_AT =
            ForeignAtmWithdrawalTestFixtures.COMMITTED.plusSeconds(60);
    private static final Clock CLOCK = Clock.fixed(
            RECOVERED_AT, ZoneOffset.UTC);

    @Test
    void exactForeignEvidenceAdvancesThroughEveryPostdecisionState() {
        Harness harness = new Harness(
                transactionAt(EscrowState.COMMIT_DECIDED));
        harness.seedExactEvidence();

        EscrowRecoveryAttempt committed = harness.handler.recover(
                harness.current);
        EscrowRecoveryAttempt claimsCreated = harness.handler.recover(
                harness.current);
        EscrowRecoveryAttempt completed = harness.handler.recover(
                harness.current);

        assertEquals(EscrowRecoveryDisposition.PROGRESSED,
                committed.disposition());
        assertEquals(EscrowRecoveryDisposition.PROGRESSED,
                claimsCreated.disposition());
        assertEquals(EscrowRecoveryDisposition.RESOLVED,
                completed.disposition());
        assertEquals(List.of(
                        EscrowState.COMMITTED,
                        EscrowState.CLAIMS_CREATED,
                        EscrowState.COMPLETED),
                harness.committedStates());
        assertEquals(EscrowState.COMPLETED, harness.current.state());
        assertTrue(!harness.protectedMints.hasMaterializedState());
    }

    @Test
    void everyForeignPredecisionStateRefundsWithoutDebitingTheWallet() {
        for (EscrowState state : List.of(
                EscrowState.CREATED,
                EscrowState.VALIDATED,
                EscrowState.HOLDING,
                EscrowState.HELD)) {
            Harness harness = new Harness(transactionAt(state));
            ForeignAtmWithdrawalCommit composite =
                    ForeignAtmWithdrawalTestFixtures.commit();
            harness.fundWallet(composite.amountMinorUnits(), state.name());
            long walletBefore = harness.walletBalance();

            EscrowRecoveryAttempt result = harness.handler.recover(
                    harness.current);

            assertEquals(EscrowRecoveryDisposition.RESOLVED,
                    result.disposition());
            assertEquals(EscrowState.REFUNDED, harness.current.state());
            assertEquals(List.of(
                            EscrowState.ABORTING,
                            EscrowState.REFUND_PENDING,
                            EscrowState.REFUNDED),
                    harness.committedStates());
            assertEquals(walletBefore, harness.walletBalance());
            assertEquals(0L, harness.foreignSinkBalance());
            assertTrue(harness.ledger.transactionReceipt(
                    ForeignAtmWithdrawalTestFixtures.REQUEST_ID).isEmpty());
        }
    }

    @Test
    void missingForeignClaimEntersManualReviewWithoutRefunding() {
        Harness harness = new Harness(
                transactionAt(EscrowState.COMMIT_DECIDED));
        ForeignAtmWithdrawalCommit composite =
                ForeignAtmWithdrawalTestFixtures.commit();
        harness.seedEvidence(composite.cashClaims().subList(
                1, composite.cashClaims().size()));
        long walletBefore = harness.walletBalance();

        EscrowRecoveryAttempt result = harness.handler.recover(
                harness.current);

        assertManualReview(harness, result);
        assertEquals(walletBefore, harness.walletBalance());
    }

    @Test
    void corruptedForeignClaimPayloadEntersManualReviewWithoutRefunding() {
        Harness harness = new Harness(
                transactionAt(EscrowState.COMMIT_DECIDED));
        ForeignAtmWithdrawalCommit composite =
                ForeignAtmWithdrawalTestFixtures.commit();
        List<EscrowClaim> claims = new ArrayList<>(composite.cashClaims());
        EscrowClaim original = claims.get(0);
        ForeignCashClaimPayload payload =
                ForeignAtmWithdrawalTestFixtures.payload(original);
        ForeignCashClaimPayload corrupted = ForeignCashClaimPayload.capture(
                payload.providerId(),
                payload.configSignature(),
                payload.registryItemId(),
                payload.denominationMinorUnits(),
                payload.stackCount(),
                payload.denominationIndex(),
                payload.portionIndex(),
                payload.portionCount(),
                "corrupted foreign item stack"
                        .getBytes(StandardCharsets.UTF_8));
        claims.set(0, new EscrowClaim(
                original.claimId(),
                original.transactionId(),
                original.ownerId(),
                original.sourceKey(),
                original.kind(),
                original.originalUnits(),
                original.remainingUnits(),
                ForeignCashClaimPayloadCodec.encode(corrupted),
                original.status(),
                original.label(),
                original.createdAt(),
                original.updatedAt()));
        harness.seedEvidence(claims);
        long walletBefore = harness.walletBalance();

        EscrowRecoveryAttempt result = harness.handler.recover(
                harness.current);

        assertManualReview(harness, result);
        assertEquals(walletBefore, harness.walletBalance());
    }

    @Test
    void protectedMintEvidenceOnForeignRouteEntersManualReview() {
        Harness harness = new Harness(
                transactionAt(EscrowState.COMMIT_DECIDED));
        harness.seedExactEvidence();
        ProtectedMintBatch batch = ProtectedMintBatch.issue(
                ForeignAtmWithdrawalTestFixtures.REQUEST_ID,
                "foreign.atm.unexpected.protected.mint",
                100L,
                1,
                "foreign-recovery-test-server",
                ForeignAtmWithdrawalTestFixtures.COMMITTED,
                (batchId, transactionId, denominationMinorUnits,
                 authorizedCount, serverIdentityEvidence, issuedAt) ->
                        "foreign.recovery.checksum."
                                + batchId + "." + transactionId);
        harness.protectedMints.applyCommitted(
                ProtectedMintJournalEvent.issue(batch));

        EscrowRecoveryAttempt result = harness.handler.recover(
                harness.current);

        assertManualReview(harness, result);
        assertTrue(harness.protectedMints.hasMaterializedState());
    }

    private static void assertManualReview(
            Harness harness,
            EscrowRecoveryAttempt result
    ) {
        assertEquals(EscrowRecoveryDisposition.MANUAL_REVIEW,
                result.disposition());
        assertEquals(EscrowState.MANUAL_REVIEW, harness.current.state());
        assertEquals(List.of(
                        EscrowState.RECOVERY_REQUIRED,
                        EscrowState.MANUAL_REVIEW),
                harness.committedStates());
        assertEquals("ATM_RECOVERY_EVIDENCE_INVALID",
                harness.current.lastError().orElseThrow().code());
    }

    private static EscrowTransaction transactionAt(EscrowState target) {
        EscrowTransaction decision =
                ForeignAtmWithdrawalTestFixtures.commit()
                        .committedTransaction();
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
                ForeignAtmWithdrawalTestFixtures.CREATED.plusSeconds(1));
        if (target == EscrowState.VALIDATED) {
            return validated;
        }
        EscrowTransaction holding = validated.transitionTo(
                EscrowState.HOLDING,
                ForeignAtmWithdrawalTestFixtures.CREATED.plusSeconds(2));
        if (target == EscrowState.HOLDING) {
            return holding;
        }
        EscrowTransaction held = holding.transitionTo(
                EscrowState.HELD,
                ForeignAtmWithdrawalTestFixtures.CREATED.plusSeconds(3));
        if (target == EscrowState.HELD) {
            return held;
        }
        EscrowTransaction commitDecided = held.transitionTo(
                EscrowState.COMMIT_DECIDED,
                ForeignAtmWithdrawalTestFixtures.COMMITTED);
        if (target == EscrowState.COMMIT_DECIDED) {
            return commitDecided;
        }
        EscrowTransaction committed = commitDecided.transitionTo(
                EscrowState.COMMITTED,
                ForeignAtmWithdrawalTestFixtures.COMMITTED.plusSeconds(1));
        if (target == EscrowState.COMMITTED) {
            return committed;
        }
        EscrowTransaction claimsCreated = committed.transitionTo(
                EscrowState.CLAIMS_CREATED,
                ForeignAtmWithdrawalTestFixtures.COMMITTED.plusSeconds(2));
        if (target == EscrowState.CLAIMS_CREATED) {
            return claimsCreated;
        }
        if (target == EscrowState.COMPLETED) {
            return claimsCreated.transitionTo(
                    EscrowState.COMPLETED,
                    ForeignAtmWithdrawalTestFixtures.COMMITTED.plusSeconds(3));
        }
        throw new IllegalArgumentException(
                "Unsupported foreign ATM recovery test state " + target);
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

        private void commit(EscrowTransaction transaction) {
            current = transaction;
            commits.add(transaction);
        }

        private List<EscrowState> committedStates() {
            return commits.stream().map(EscrowTransaction::state).toList();
        }

        private void seedExactEvidence() {
            seedEvidence(ForeignAtmWithdrawalTestFixtures.commit().cashClaims());
        }

        private void seedEvidence(List<EscrowClaim> cashClaims) {
            ForeignAtmWithdrawalCommit composite =
                    ForeignAtmWithdrawalTestFixtures.commit();
            fundWallet(composite.amountMinorUnits(), "evidence");
            ledger.applyCommitted(composite.ledgerTransaction());
            cashClaims.forEach(claims::createCommitted);
        }

        private void fundWallet(long amount, String suffix) {
            ledger.applyCommitted(new LedgerTransaction(
                    java.util.UUID.nameUUIDFromBytes(
                            ("foreign recovery funding " + suffix)
                                    .getBytes(StandardCharsets.UTF_8)),
                    "foreign recovery funding " + suffix,
                    "fund",
                    List.of(
                            new LedgerLeg(LedgerAccountId.system(
                                    LedgerAccountType.ADMIN_SOURCE),
                                    Math.negateExact(amount)),
                            new LedgerLeg(walletAccount(), amount))));
        }

        private long walletBalance() {
            return ledger.balance(walletAccount());
        }

        private long foreignSinkBalance() {
            return ledger.balance(LedgerAccountId.system(
                    LedgerAccountType.FOREIGN_CURRENCY_SINK));
        }

        private static LedgerAccountId walletAccount() {
            return new LedgerAccountId(
                    LedgerAccountType.PLAYER_WALLET,
                    ForeignAtmWithdrawalTestFixtures.PLAYER_ID.toString());
        }
    }
}
