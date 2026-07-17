package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimSavedData;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.ledger.LedgerApplyResult;
import com.enviouse.futureshops.server.escrow.ledger.LedgerSavedData;
import com.enviouse.futureshops.server.escrow.ledger.LedgerTransactionReceipt;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintApplyResult;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatch;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintBatchLiability;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintJournalEvent;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintReceipt;
import com.enviouse.futureshops.server.escrow.mint.ProtectedMintSavedData;
import com.enviouse.futureshops.server.escrow.model.EscrowError;
import com.enviouse.futureshops.server.escrow.model.EscrowAssetLotType;
import com.enviouse.futureshops.server.escrow.model.EscrowOperation;
import com.enviouse.futureshops.server.escrow.model.EscrowPartyType;
import com.enviouse.futureshops.server.escrow.model.EscrowRetryMetadata;
import com.enviouse.futureshops.server.escrow.model.EscrowState;
import com.enviouse.futureshops.server.escrow.model.EscrowTimestamps;
import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

final class AtmWithdrawalRecoveryHandler implements EscrowRecoveryHandler {
    private static final String EVIDENCE_ERROR_CODE =
            "ATM_RECOVERY_EVIDENCE_INVALID";

    private final Consumer<EscrowTransaction> committer;
    private final LedgerSavedData ledger;
    private final ClaimSavedData claims;
    private final ProtectedMintSavedData protectedMints;
    private final Clock clock;

    AtmWithdrawalRecoveryHandler(
            EscrowRuntimeService runtime,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            ProtectedMintSavedData protectedMints,
            Clock clock
    ) {
        this(transaction -> runtime.commitTransaction(transaction),
                ledger, claims, protectedMints, clock);
    }

    AtmWithdrawalRecoveryHandler(
            Consumer<EscrowTransaction> committer,
            LedgerSavedData ledger,
            ClaimSavedData claims,
            ProtectedMintSavedData protectedMints,
            Clock clock
    ) {
        this.committer = Objects.requireNonNull(committer, "committer");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.protectedMints = Objects.requireNonNull(protectedMints, "protectedMints");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public EscrowRecoveryAttempt recover(EscrowTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (transaction.operation() != EscrowOperation.ATM_WITHDRAWAL) {
            return EscrowRecoveryAttempt.manualReview(
                    "ATM recovery received a transaction for another operation");
        }
        return switch (transaction.state()) {
            case CREATED, VALIDATED, HOLDING, HELD, ABORTING, REFUND_PENDING ->
                    recoverBeforeCommitDecision(transaction);
            case COMMIT_DECIDED -> advanceVerified(
                    transaction, EscrowState.COMMITTED,
                    "ATM withdrawal commit evidence was verified");
            case COMMITTED -> advanceVerified(
                    transaction, EscrowState.CLAIMS_CREATED,
                    "ATM withdrawal claims were verified");
            case CLAIMS_CREATED -> advanceVerified(
                    transaction, EscrowState.COMPLETED,
                    "ATM withdrawal recovery completed");
            case RECOVERY_REQUIRED -> resumeRecordedState(transaction);
            case COMPLETED, REFUNDED -> EscrowRecoveryAttempt.resolved(
                    "ATM withdrawal is already terminal");
            case MANUAL_REVIEW -> EscrowRecoveryAttempt.manualReview(
                    "ATM withdrawal is already in manual review");
        };
    }

    private EscrowRecoveryAttempt recoverBeforeCommitDecision(
            EscrowTransaction transaction
    ) {
        try {
            requireNoCompositeEvidence(transaction.transactionId().value());
        } catch (RuntimeException exception) {
            return requireManualReview(transaction, evidenceDetail(exception));
        }
        EscrowTransaction current = transaction;
        Instant at = transitionTime(current);
        if (current.state() != EscrowState.ABORTING
                && current.state() != EscrowState.REFUND_PENDING) {
            current = current.transitionTo(EscrowState.ABORTING, at);
            commit(current);
        }
        if (current.state() == EscrowState.ABORTING) {
            current = current.transitionTo(EscrowState.REFUND_PENDING, at);
            commit(current);
        }
        current = current.transitionTo(EscrowState.REFUNDED, at);
        commit(current);
        return EscrowRecoveryAttempt.resolved(
                "ATM withdrawal ended before its composite committed");
    }

    private EscrowRecoveryAttempt advanceVerified(
            EscrowTransaction transaction,
            EscrowState target,
            String detail
    ) {
        try {
            requireCommittedEvidence(transaction);
        } catch (RuntimeException exception) {
            return requireManualReview(transaction, evidenceDetail(exception));
        }
        EscrowTransaction advanced = transaction.transitionTo(
                target, transitionTime(transaction));
        commit(advanced);
        return target.isTerminal()
                ? EscrowRecoveryAttempt.resolved(detail)
                : EscrowRecoveryAttempt.progressed(detail);
    }

    private EscrowRecoveryAttempt resumeRecordedState(EscrowTransaction transaction) {
        EscrowState resumeState = transaction.retryMetadata()
                .resumeState()
                .orElseThrow(() -> new IllegalStateException(
                        "ATM recovery resume state is missing"));
        try {
            switch (resumeState) {
                case HOLDING, HELD, REFUND_PENDING ->
                        requireNoCompositeEvidence(transaction.transactionId().value());
                case COMMIT_DECIDED, COMMITTED, CLAIMS_CREATED ->
                        requireCommittedEvidence(transaction);
                default -> throw new EvidenceException(
                        "ATM recovery resume state is unsupported");
            }
        } catch (RuntimeException exception) {
            return requireManualReview(transaction, evidenceDetail(exception));
        }
        EscrowTransaction resumed = transaction.transitionTo(
                resumeState, transitionTime(transaction));
        commit(resumed);
        return EscrowRecoveryAttempt.progressed(
                "ATM withdrawal resumed its recorded recovery state");
    }

    private void requireNoCompositeEvidence(UUID transactionId) {
        if (ledger.wasApplied(transactionId)
                || ledger.transactionReceipt(transactionId).isPresent()) {
            throw new EvidenceException(
                    "A ledger receipt exists before the ATM commit decision");
        }
        if (!claims.claimsForTransaction(transactionId).isEmpty()) {
            throw new EvidenceException(
                    "A cash claim exists before the ATM commit decision");
        }
        for (ProtectedMintBatchLiability liability
                : protectedMints.liabilitySnapshot().batches()) {
            ProtectedMintBatch batch = protectedMints.getBatch(liability.batchId());
            if (batch == null) {
                throw new EvidenceException(
                        "The protected mint snapshot contains a missing batch");
            }
            if (batch.transactionId().equals(transactionId)) {
                throw new EvidenceException(
                        "A protected mint batch exists before the ATM commit decision");
            }
        }
    }

    private void requireCommittedEvidence(EscrowTransaction transaction) {
        UUID transactionId = transaction.transactionId().value();
        LedgerTransactionReceipt ledgerReceipt = ledger
                .transactionReceipt(transactionId)
                .orElseThrow(() -> new EvidenceException(
                        "The ATM ledger receipt is missing"));
        LedgerApplyResult ledgerReplay = ledger.preflightCommitted(
                ledgerReceipt.transaction());
        if (ledgerReplay.applied() || !ledgerReplay.replayed()) {
            throw new EvidenceException(
                    "The ATM ledger receipt does not replay exactly");
        }

        Instant committedAt = transaction.timestamps()
                .commitDecidedAt()
                .orElseThrow(() -> new EvidenceException(
                        "The ATM commit decision time is missing"));
        UUID playerId = requirePlayerId(transaction);
        List<EscrowClaim> cashClaims = claims.claimsForTransaction(transactionId);
        if (cashClaims.isEmpty()) {
            throw new EvidenceException("The ATM cash claims are missing");
        }
        if (foreignRoute(transaction)) {
            requireForeignCommittedEvidence(
                    transaction, playerId, committedAt,
                    ledgerReceipt, cashClaims);
            return;
        }
        requireProtectedCommittedEvidence(
                transaction, playerId, committedAt,
                ledgerReceipt, cashClaims);
    }

    private void requireProtectedCommittedEvidence(
            EscrowTransaction transaction,
            UUID playerId,
            Instant committedAt,
            LedgerTransactionReceipt ledgerReceipt,
            List<EscrowClaim> cashClaims
    ) {
        UUID transactionId = transaction.transactionId().value();
        for (EscrowClaim claim : cashClaims) {
            ProtectedCashClaimPayload payload =
                    ProtectedCashClaimPayloadCodec.decode(claim.payload());
            EscrowClaim expected = expectedClaim(
                    transactionId, playerId, payload, committedAt);
            if (!claim.equals(expected)
                    || !claims.preflightCreateCommitted(expected).equals(expected)) {
                throw new EvidenceException(
                        "An ATM protected cash claim conflicts with its evidence");
            }
        }

        List<ProtectedMintJournalEvent> mintIssues = new ArrayList<>();
        for (ProtectedMintBatchLiability liability
                : protectedMints.liabilitySnapshot().batches()) {
            ProtectedMintBatch batch = protectedMints.getBatch(liability.batchId());
            if (batch == null) {
                throw new EvidenceException(
                        "The protected mint snapshot contains a missing batch");
            }
            if (!batch.transactionId().equals(transactionId)) {
                continue;
            }
            ProtectedMintJournalEvent issue = ProtectedMintJournalEvent.issue(batch);
            ProtectedMintApplyResult replay = protectedMints.preflightCommitted(issue);
            ProtectedMintReceipt receipt = protectedMints.receiptForRequest(
                    issue.requestKey());
            if (!replay.replayed() || receipt == null
                    || !receipt.equals(replay.receipt())) {
                throw new EvidenceException(
                        "An ATM protected mint issue does not replay exactly");
            }
            mintIssues.add(issue);
        }
        if (mintIssues.isEmpty()) {
            throw new EvidenceException("The ATM protected mint batches are missing");
        }

        new AtmWithdrawalCommit(
                playerId,
                commitDecisionView(transaction, committedAt),
                ledgerReceipt.transaction(),
                mintIssues,
                cashClaims);
    }

    private void requireForeignCommittedEvidence(
            EscrowTransaction transaction,
            UUID playerId,
            Instant committedAt,
            LedgerTransactionReceipt ledgerReceipt,
            List<EscrowClaim> cashClaims
    ) {
        UUID transactionId = transaction.transactionId().value();
        for (ProtectedMintBatchLiability liability
                : protectedMints.liabilitySnapshot().batches()) {
            ProtectedMintBatch batch = protectedMints.getBatch(
                    liability.batchId());
            if (batch == null) {
                throw new EvidenceException(
                        "The protected mint snapshot contains a missing batch");
            }
            if (batch.transactionId().equals(transactionId)) {
                throw new EvidenceException(
                        "A foreign ATM withdrawal has protected mint evidence");
            }
        }
        for (EscrowClaim claim : cashClaims) {
            if (claim.kind() != ClaimKind.FOREIGN_CASH) {
                throw new EvidenceException(
                        "A foreign ATM withdrawal has another claim kind");
            }
            ForeignCashClaimPayload payload =
                    ForeignCashClaimPayloadCodec.decode(claim.payload());
            long claimUnits = Math.multiplyExact(
                    payload.denominationMinorUnits(),
                    (long) payload.stackCount());
            EscrowClaim expected = new EscrowClaim(
                    ForeignAtmWithdrawalCommit.claimId(
                            transactionId,
                            payload.denominationIndex(),
                            payload.portionIndex()),
                    transactionId,
                    playerId,
                    ForeignAtmWithdrawalCommit.claimSourceKey(
                            transactionId,
                            payload.denominationIndex(),
                            payload.portionIndex()),
                    ClaimKind.FOREIGN_CASH,
                    claimUnits,
                    claimUnits,
                    ForeignCashClaimPayloadCodec.encode(payload),
                    ClaimStatus.PENDING,
                    "Foreign cash " + payload.registryItemId(),
                    committedAt,
                    committedAt);
            if (!claim.equals(expected)
                    || !claims.preflightCreateCommitted(expected)
                    .equals(expected)) {
                throw new EvidenceException(
                        "A foreign ATM cash claim conflicts with its evidence");
            }
        }
        new ForeignAtmWithdrawalCommit(
                transactionId,
                playerId,
                commitDecisionView(transaction, committedAt),
                ledgerReceipt.transaction(),
                cashClaims);
    }

    private static boolean foreignRoute(EscrowTransaction transaction) {
        boolean protectedCash = transaction.assetLots().stream().anyMatch(
                lot -> lot.type()
                        == EscrowAssetLotType.PROTECTED_PHYSICAL_CURRENCY);
        boolean foreignCash = transaction.assetLots().stream().anyMatch(
                lot -> lot.type()
                        == EscrowAssetLotType.FOREIGN_PHYSICAL_CURRENCY);
        if (protectedCash == foreignCash) {
            throw new EvidenceException(
                    "The ATM currency protection route is ambiguous");
        }
        return foreignCash;
    }

    private static UUID requirePlayerId(EscrowTransaction transaction) {
        List<UUID> players = transaction.participants().stream()
                .map(participant -> participant.party())
                .filter(party -> party.type() == EscrowPartyType.PLAYER)
                .map(party -> UUID.fromString(party.id()))
                .toList();
        if (players.size() != 1) {
            throw new EvidenceException(
                    "The ATM transaction does not have exactly one player");
        }
        return players.get(0);
    }

    private static EscrowClaim expectedClaim(
            UUID transactionId,
            UUID playerId,
            ProtectedCashClaimPayload payload,
            Instant committedAt
    ) {
        long claimUnits = Math.multiplyExact(
                payload.denominationMinorUnits(),
                (long) payload.billCount());
        return new EscrowClaim(
                AtmWithdrawalCommit.claimId(
                        transactionId, payload.batchId(), payload.portionIndex()),
                transactionId,
                playerId,
                AtmWithdrawalCommit.claimSourceKey(
                        transactionId, payload.batchId(), payload.portionIndex()),
                ClaimKind.PROTECTED_CASH,
                claimUnits,
                claimUnits,
                ProtectedCashClaimPayloadCodec.encode(payload),
                ClaimStatus.PENDING,
                "Protected cash " + payload.denominationMinorUnits(),
                committedAt,
                committedAt);
    }

    private static EscrowTransaction commitDecisionView(
            EscrowTransaction transaction,
            Instant committedAt
    ) {
        return new EscrowTransaction(
                transaction.transactionId(),
                transaction.parentTransactionId(),
                transaction.requestKey(),
                transaction.operation(),
                EscrowState.COMMIT_DECIDED,
                transaction.participants(),
                transaction.assetLots(),
                new EscrowTimestamps(
                        transaction.timestamps().createdAt(),
                        committedAt,
                        Optional.of(committedAt),
                        Optional.empty()),
                transaction.revision(),
                transaction.configRevision(),
                Optional.empty(),
                EscrowRetryMetadata.none(),
                transaction.shopReference());
    }

    private EscrowRecoveryAttempt requireManualReview(
            EscrowTransaction transaction,
            String reason
    ) {
        Instant at = transitionTime(transaction);
        EscrowTransaction current = transaction;
        if (current.state() == EscrowState.CREATED
                || current.state() == EscrowState.VALIDATED) {
            EscrowTransaction aborting;
            try {
                aborting = current.transitionTo(EscrowState.ABORTING, at);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                return blockedManualReview(reason);
            }
            commit(aborting);
            current = aborting;
        }
        if (current.state() != EscrowState.RECOVERY_REQUIRED) {
            if (!current.state().canTransitionTo(EscrowState.RECOVERY_REQUIRED)) {
                return blockedManualReview(reason);
            }
            EscrowRetryMetadata retry = current.retryMetadata();
            int maximumAttempts = retry.maxAttempts() == 0
                    ? 1 : retry.maxAttempts();
            if (retry.attemptCount() >= maximumAttempts) {
                return blockedManualReview(reason);
            }
            EscrowError error = new EscrowError(
                    EVIDENCE_ERROR_CODE,
                    limited(reason, 1024),
                    true,
                    at,
                    Map.of(
                            "operation", EscrowOperation.ATM_WITHDRAWAL.name(),
                            "state", current.state().name()));
            EscrowTransaction recovery;
            try {
                recovery = current.requireRecovery(
                        error, maximumAttempts, at, at);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                return blockedManualReview(reason);
            }
            commit(recovery);
            current = recovery;
        }
        EscrowTransaction manual;
        try {
            manual = current.transitionTo(EscrowState.MANUAL_REVIEW, at);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return blockedManualReview(reason);
        }
        commit(manual);
        return EscrowRecoveryAttempt.manualReview(limited(
                "ATM withdrawal moved to manual review. " + reason, 1024));
    }

    private static EscrowRecoveryAttempt blockedManualReview(String reason) {
        return EscrowRecoveryAttempt.manualReview(limited(
                "ATM withdrawal requires manual review. Durable state transition was blocked. "
                        + reason,
                1024));
    }

    private Instant transitionTime(EscrowTransaction transaction) {
        Instant now = clock.instant();
        Instant updatedAt = transaction.timestamps().updatedAt();
        return now.isBefore(updatedAt) ? updatedAt : now;
    }

    private void commit(EscrowTransaction transaction) {
        committer.accept(transaction);
    }

    private static String evidenceDetail(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return limited(message, 900);
    }

    private static String limited(String value, int maximumLength) {
        String normalized = Objects.requireNonNull(value, "value").strip();
        if (normalized.length() <= maximumLength) {
            return normalized;
        }
        return normalized.substring(0, maximumLength);
    }

    private static final class EvidenceException extends IllegalStateException {
        private EvidenceException(String message) {
            super(message);
        }
    }
}
