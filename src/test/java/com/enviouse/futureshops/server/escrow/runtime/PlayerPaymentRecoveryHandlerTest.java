package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountId;
import com.enviouse.futureshops.server.escrow.ledger.LedgerAccountType;
import com.enviouse.futureshops.server.escrow.ledger.LedgerLeg;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowError;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPaymentRecoveryHandlerTest {
    @Test
    void preDecisionWithoutValueEvidenceConvergesToRefunded() {
        PlayerPaymentCommit commit = PlayerPaymentTestFixtures.direct();
        EscrowTransaction created = created(commit);
        List<EscrowTransaction> applied = new ArrayList<>();
        PlayerPaymentRecoveryHandler handler = new PlayerPaymentRecoveryHandler(
                applied::add, new LedgerSavedData(), new ClaimSavedData(),
                Clock.fixed(PlayerPaymentTestFixtures.NOW,
                        ZoneOffset.UTC));

        EscrowRecoveryAttempt attempt = handler.recover(created);

        assertEquals(EscrowRecoveryDisposition.RESOLVED,
                attempt.disposition());
        assertEquals(EscrowState.REFUNDED,
                applied.get(applied.size() - 1).state());
    }

    @Test
    void exactDecisionEvidenceConvergesToCompleted() {
        PlayerPaymentCommit commit = PlayerPaymentTestFixtures.direct();
        LedgerSavedData ledger = seededAndPaid(commit);
        List<EscrowTransaction> applied = new ArrayList<>();
        PlayerPaymentRecoveryHandler handler = new PlayerPaymentRecoveryHandler(
                applied::add, ledger, new ClaimSavedData(),
                Clock.fixed(PlayerPaymentTestFixtures.NOW,
                        ZoneOffset.UTC));

        EscrowRecoveryAttempt attempt = handler.recover(
                commitDecided(commit));

        assertEquals(EscrowRecoveryDisposition.RESOLVED,
                attempt.disposition());
        assertEquals(commit.completedTransaction(),
                applied.get(applied.size() - 1));
    }

    @Test
    void terminalEvidenceMustExactlyMatchItsLifecycle() {
        PlayerPaymentCommit commit = PlayerPaymentTestFixtures.direct();
        PlayerPaymentRecoveryHandler missingLedger =
                new PlayerPaymentRecoveryHandler(
                        ignored -> {
                        }, new LedgerSavedData(), new ClaimSavedData(),
                        Clock.fixed(PlayerPaymentTestFixtures.NOW,
                                ZoneOffset.UTC));

        assertEquals(EscrowRecoveryDisposition.MANUAL_REVIEW,
                missingLedger.recover(commit.completedTransaction())
                        .disposition());

        LedgerSavedData ledger = seededAndPaid(commit);
        PlayerPaymentRecoveryHandler refundedWithValue =
                new PlayerPaymentRecoveryHandler(
                        ignored -> {
                        }, ledger, new ClaimSavedData(),
                        Clock.fixed(PlayerPaymentTestFixtures.NOW,
                                ZoneOffset.UTC));
        assertEquals(EscrowRecoveryDisposition.MANUAL_REVIEW,
                refundedWithValue.recover(refunded(commit))
                        .disposition());
    }

    @Test
    void completedExactEvidenceIsResolvedWithoutMutation() {
        PlayerPaymentCommit commit = PlayerPaymentTestFixtures.direct();
        LedgerSavedData ledger = seededAndPaid(commit);
        List<EscrowTransaction> applied = new ArrayList<>();
        PlayerPaymentRecoveryHandler handler = new PlayerPaymentRecoveryHandler(
                applied::add, ledger, new ClaimSavedData(),
                Clock.fixed(PlayerPaymentTestFixtures.NOW,
                        ZoneOffset.UTC));

        assertEquals(EscrowRecoveryDisposition.RESOLVED,
                handler.recover(commit.completedTransaction())
                        .disposition());
        assertTrue(applied.isEmpty());
    }

    @Test
    void exactEvidenceConvergesFromRecoveryAndManualReview() {
        PlayerPaymentCommit commit = PlayerPaymentTestFixtures.direct();
        EscrowTransaction recovery = commitDecided(commit).requireRecovery(
                recoveryError(), 3,
                PlayerPaymentTestFixtures.NOW,
                PlayerPaymentTestFixtures.NOW);
        EscrowTransaction manual = recovery.transitionTo(
                EscrowState.MANUAL_REVIEW,
                PlayerPaymentTestFixtures.NOW);

        for (EscrowTransaction state : List.of(recovery, manual)) {
            List<EscrowTransaction> applied = new ArrayList<>();
            PlayerPaymentRecoveryHandler handler =
                    new PlayerPaymentRecoveryHandler(
                            applied::add, seededAndPaid(commit),
                            new ClaimSavedData(), Clock.fixed(
                            PlayerPaymentTestFixtures.NOW,
                            ZoneOffset.UTC));

            assertEquals(EscrowRecoveryDisposition.RESOLVED,
                    handler.recover(state).disposition());
            assertEquals(EscrowState.COMPLETED,
                    applied.get(applied.size() - 1).state());
        }
    }

    @Test
    void recoveryWithoutValueResumesItsRecordedRefundPath() {
        PlayerPaymentCommit commit = PlayerPaymentTestFixtures.direct();
        EscrowTransaction held = created(commit)
                .transitionTo(EscrowState.VALIDATED,
                        PlayerPaymentTestFixtures.NOW)
                .transitionTo(EscrowState.HOLDING,
                        PlayerPaymentTestFixtures.NOW)
                .transitionTo(EscrowState.HELD,
                        PlayerPaymentTestFixtures.NOW);
        EscrowTransaction recovery = held.requireRecovery(
                recoveryError(), 3,
                PlayerPaymentTestFixtures.NOW,
                PlayerPaymentTestFixtures.NOW);
        List<EscrowTransaction> applied = new ArrayList<>();
        PlayerPaymentRecoveryHandler handler =
                new PlayerPaymentRecoveryHandler(
                        applied::add, new LedgerSavedData(),
                        new ClaimSavedData(), Clock.fixed(
                        PlayerPaymentTestFixtures.NOW,
                        ZoneOffset.UTC));

        assertEquals(EscrowRecoveryDisposition.RESOLVED,
                handler.recover(recovery).disposition());
        assertEquals(EscrowState.REFUNDED,
                applied.get(applied.size() - 1).state());
    }

    private static LedgerSavedData seededAndPaid(
            PlayerPaymentCommit commit
    ) {
        LedgerSavedData ledger = new LedgerSavedData();
        seed(ledger, PlayerPaymentCommit.walletAccount(commit.payerId()),
                commit.payerWalletBeforeMinorUnits(), "recovery payer");
        seed(ledger,
                PlayerPaymentCommit.walletAccount(commit.recipientId()),
                commit.recipientWalletBeforeMinorUnits(),
                "recovery recipient");
        ledger.applyCommitted(commit.ledgerTransaction());
        return ledger;
    }

    private static void seed(
            LedgerSavedData ledger,
            LedgerAccountId account,
            long units,
            String key
    ) {
        if (units == 0L) {
            return;
        }
        ledger.applyCommitted(new LedgerTransaction(
                UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)),
                key, "seed", List.of(
                new LedgerLeg(LedgerAccountId.system(
                        LedgerAccountType.ADMIN_SOURCE),
                        Math.negateExact(units)),
                new LedgerLeg(account, units))));
    }

    private static EscrowTransaction created(PlayerPaymentCommit commit) {
        EscrowTransaction completed = commit.completedTransaction();
        return EscrowTransaction.create(
                completed.transactionId(),
                completed.parentTransactionId(),
                completed.requestKey(),
                completed.operation(),
                completed.participants(),
                completed.assetLots(),
                completed.timestamps().createdAt(),
                completed.configRevision(),
                completed.shopReference());
    }

    private static EscrowTransaction commitDecided(
            PlayerPaymentCommit commit
    ) {
        return created(commit)
                .transitionTo(EscrowState.VALIDATED,
                        PlayerPaymentTestFixtures.NOW)
                .transitionTo(EscrowState.HOLDING,
                        PlayerPaymentTestFixtures.NOW)
                .transitionTo(EscrowState.HELD,
                        PlayerPaymentTestFixtures.NOW)
                .transitionTo(EscrowState.COMMIT_DECIDED,
                        PlayerPaymentTestFixtures.NOW);
    }

    private static EscrowTransaction refunded(
            PlayerPaymentCommit commit
    ) {
        return created(commit)
                .transitionTo(EscrowState.ABORTING,
                        PlayerPaymentTestFixtures.NOW)
                .transitionTo(EscrowState.REFUND_PENDING,
                        PlayerPaymentTestFixtures.NOW)
                .transitionTo(EscrowState.REFUNDED,
                        PlayerPaymentTestFixtures.NOW);
    }

    private static EscrowError recoveryError() {
        return new EscrowError(
                "PAYMENT_RECOVERY", "Payment recovery test", true,
                PlayerPaymentTestFixtures.NOW, Map.of());
    }
}
