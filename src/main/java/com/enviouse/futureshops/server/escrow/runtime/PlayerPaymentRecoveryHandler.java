package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransaction;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

final class PlayerPaymentRecoveryHandler implements EscrowRecoveryHandler {
    private final Consumer<EscrowTransaction> committer;
    private final LedgerSavedData ledger;
    private final ClaimSavedData claims;
    private final Clock clock;

    PlayerPaymentRecoveryHandler(
            EscrowRuntimeService runtime,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            Clock clock
    ) {
        this(runtime::commitTransaction, ledger, claims, clock);
    }

    PlayerPaymentRecoveryHandler(
            Consumer<EscrowTransaction> committer,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            Clock clock
    ) {
        this.committer = Objects.requireNonNull(committer, "committer");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public EscrowRecoveryAttempt recover(EscrowTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (transaction.operation() != EscrowOperation.PLAYER_PAYMENT) {
            return EscrowRecoveryAttempt.manualReview(
                    "Player payment recovery received another operation");
        }
        Optional<LedgerTransaction> receipt = ledger.transactionReceipt(
                transaction.transactionId().value()).map(value ->
                value.transaction());
        List<EscrowClaim> paymentClaims = claims.claimsForTransaction(
                transaction.transactionId().value());
        try {
            PlayerPaymentCommit.identityFromTransaction(transaction);
        } catch (RuntimeException exception) {
            return EscrowRecoveryAttempt.manualReview(
                    "Player payment immutable evidence conflicts");
        }
        if (transaction.state() == EscrowState.COMPLETED) {
            if (receipt.isEmpty()) {
                return EscrowRecoveryAttempt.manualReview(
                        "Completed player payment lacks ledger evidence");
            }
            try {
                PlayerPaymentCommit.fromEvidence(
                        transaction, receipt.orElseThrow(),
                        paymentClaims);
                return EscrowRecoveryAttempt.resolved(
                        "Completed player payment evidence is exact");
            } catch (RuntimeException exception) {
                return EscrowRecoveryAttempt.manualReview(
                        "Completed player payment evidence conflicts");
            }
        }
        if (transaction.state() == EscrowState.REFUNDED) {
            return receipt.isEmpty() && paymentClaims.isEmpty()
                    ? EscrowRecoveryAttempt.resolved(
                    "Refunded player payment has no value evidence")
                    : EscrowRecoveryAttempt.manualReview(
                    "Refunded player payment has unexpected value evidence");
        }
        if (transaction.state().isTerminal()) {
            return EscrowRecoveryAttempt.manualReview(
                    "Player payment terminal state is unsupported");
        }
        if (transaction.state() == EscrowState.RECOVERY_REQUIRED
                || transaction.state() == EscrowState.MANUAL_REVIEW) {
            return recoverSpecialState(
                    transaction, receipt, paymentClaims);
        }
        if (beforeCommitDecision(transaction.state())) {
            if (receipt.isPresent() || !paymentClaims.isEmpty()) {
                return EscrowRecoveryAttempt.manualReview(
                        "Player payment has value evidence before its commit decision");
            }
            try {
                refundBeforeCustody(transaction);
                return EscrowRecoveryAttempt.resolved(
                        "Player payment ended before value custody");
            } catch (RuntimeException exception) {
                return EscrowRecoveryAttempt.manualReview(
                        "Player payment could not safely end before custody");
            }
        }
        if (receipt.isEmpty()) {
            return EscrowRecoveryAttempt.manualReview(
                    "Player payment terminal evidence is incomplete");
        }
        try {
            EscrowTransaction completed = completeFromDecision(
                    transaction, paymentClaims);
            PlayerPaymentCommit.fromEvidence(
                    completed, receipt.orElseThrow(), paymentClaims);
            advance(transaction, completed);
            return EscrowRecoveryAttempt.resolved(
                    "Player payment terminal evidence was recovered");
        } catch (RuntimeException exception) {
            return EscrowRecoveryAttempt.manualReview(
                    "Player payment terminal evidence conflicts");
        }
    }

    private EscrowRecoveryAttempt recoverSpecialState(
            EscrowTransaction transaction,
            Optional<LedgerTransaction> receipt,
            List<EscrowClaim> paymentClaims
    ) {
        if (receipt.isEmpty() && paymentClaims.isEmpty()) {
            try {
                EscrowTransaction resumed = resumeForRefund(transaction);
                committer.accept(resumed);
                refundBeforeCustody(resumed);
                return EscrowRecoveryAttempt.resolved(
                        "Player payment recovery ended without value custody");
            } catch (RuntimeException exception) {
                return EscrowRecoveryAttempt.manualReview(
                        "Player payment recovery cannot safely refund");
            }
        }
        if (receipt.isEmpty()) {
            return EscrowRecoveryAttempt.manualReview(
                    "Player payment recovery has partial value evidence");
        }
        try {
            EscrowTransaction resumed = resumeForCompletion(transaction);
            EscrowTransaction completed = completeFromDecision(
                    resumed, paymentClaims);
            PlayerPaymentCommit.fromEvidence(
                    completed, receipt.orElseThrow(), paymentClaims);
            committer.accept(resumed);
            advance(resumed, completed);
            return EscrowRecoveryAttempt.resolved(
                    "Player payment recovery evidence converged");
        } catch (RuntimeException exception) {
            return EscrowRecoveryAttempt.manualReview(
                    "Player payment recovery evidence conflicts");
        }
    }

    private EscrowTransaction resumeForRefund(
            EscrowTransaction transaction
    ) {
        Instant at = transitionTime(transaction);
        if (transaction.state() == EscrowState.MANUAL_REVIEW) {
            return transaction.resolveManualReviewTo(
                    EscrowState.REFUND_PENDING, at);
        }
        EscrowState resume = transaction.retryMetadata()
                .resumeState().orElseThrow();
        if (resume != EscrowState.HOLDING
                && resume != EscrowState.HELD
                && resume != EscrowState.REFUND_PENDING) {
            throw new IllegalStateException(
                    "Player payment recovery refund state is invalid");
        }
        return transaction.transitionTo(resume, at);
    }

    private EscrowTransaction resumeForCompletion(
            EscrowTransaction transaction
    ) {
        Instant at = transitionTime(transaction);
        if (transaction.state() == EscrowState.MANUAL_REVIEW) {
            return transaction.resolveManualReviewTo(
                    EscrowState.CLAIMS_CREATED, at);
        }
        EscrowState resume = transaction.retryMetadata()
                .resumeState().orElseThrow();
        if (resume != EscrowState.COMMIT_DECIDED
                && resume != EscrowState.COMMITTED
                && resume != EscrowState.CLAIMS_CREATED) {
            throw new IllegalStateException(
                    "Player payment recovery completion state is invalid");
        }
        return transaction.transitionTo(resume, at);
    }

    private void refundBeforeCustody(EscrowTransaction transaction) {
        EscrowTransaction current = transaction;
        Instant at = transitionTime(current);
        if (current.state() != EscrowState.ABORTING
                && current.state() != EscrowState.REFUND_PENDING) {
            current = current.transitionTo(EscrowState.ABORTING, at);
            committer.accept(current);
        }
        if (current.state() == EscrowState.ABORTING) {
            current = current.transitionTo(
                    EscrowState.REFUND_PENDING, at);
            committer.accept(current);
        }
        if (current.state() == EscrowState.REFUND_PENDING) {
            committer.accept(current.transitionTo(
                    EscrowState.REFUNDED, at));
        }
    }

    private EscrowTransaction completeFromDecision(
            EscrowTransaction transaction,
            List<EscrowClaim> paymentClaims
    ) {
        EscrowTransaction current = transaction;
        Instant at = paymentClaims.isEmpty()
                ? transitionTime(current)
                : paymentClaims.get(0).createdAt();
        if (at.isBefore(current.timestamps().updatedAt())) {
            throw new IllegalStateException(
                    "Player payment evidence time moved backward");
        }
        if (current.state() == EscrowState.COMMIT_DECIDED) {
            current = current.transitionTo(EscrowState.COMMITTED, at);
        }
        if (current.state() == EscrowState.COMMITTED) {
            current = current.transitionTo(
                    EscrowState.CLAIMS_CREATED, at);
        }
        if (current.state() == EscrowState.CLAIMS_CREATED) {
            current = current.transitionTo(EscrowState.COMPLETED, at);
        }
        if (current.state() != EscrowState.COMPLETED) {
            throw new IllegalStateException(
                    "Player payment cannot advance from its recovery state");
        }
        return current;
    }

    private void advance(
            EscrowTransaction before,
            EscrowTransaction completed
    ) {
        EscrowTransaction current = before;
        Instant at = completed.timestamps().updatedAt();
        while (!current.equals(completed)) {
            EscrowState next = switch (current.state()) {
                case COMMIT_DECIDED -> EscrowState.COMMITTED;
                case COMMITTED -> EscrowState.CLAIMS_CREATED;
                case CLAIMS_CREATED -> EscrowState.COMPLETED;
                default -> throw new IllegalStateException(
                        "Player payment recovery state is invalid");
            };
            current = current.transitionTo(next, at);
            committer.accept(current);
        }
    }

    private Instant transitionTime(EscrowTransaction transaction) {
        Instant now = clock.instant();
        return now.isBefore(transaction.timestamps().updatedAt())
                ? transaction.timestamps().updatedAt() : now;
    }

    private static boolean beforeCommitDecision(EscrowState state) {
        return state == EscrowState.CREATED
                || state == EscrowState.VALIDATED
                || state == EscrowState.HOLDING
                || state == EscrowState.HELD
                || state == EscrowState.ABORTING
                || state == EscrowState.REFUND_PENDING;
    }
}
