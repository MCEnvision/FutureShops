package com.enviouse.futureshops.server.escrow.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record EscrowTransaction(
        EscrowTransactionId transactionId,
        Optional<EscrowTransactionId> parentTransactionId,
        EscrowRequestKey requestKey,
        EscrowOperation operation,
        EscrowState state,
        Set<EscrowParticipant> participants,
        List<EscrowAssetLot> assetLots,
        EscrowTimestamps timestamps,
        long revision,
        long configRevision,
        Optional<EscrowError> lastError,
        EscrowRetryMetadata retryMetadata,
        Optional<DimensionAwareShopReference> shopReference
) {
    public EscrowTransaction {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(parentTransactionId, "parentTransactionId");
        Objects.requireNonNull(requestKey, "requestKey");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(participants, "participants");
        Objects.requireNonNull(assetLots, "assetLots");
        Objects.requireNonNull(timestamps, "timestamps");
        Objects.requireNonNull(lastError, "lastError");
        Objects.requireNonNull(retryMetadata, "retryMetadata");
        Objects.requireNonNull(shopReference, "shopReference");
        participants = Set.copyOf(participants);
        assetLots = List.copyOf(assetLots);
        validateIdentity(transactionId, parentTransactionId, revision, configRevision);
        validateParticipants(participants);
        validateAssets(participants, assetLots);
        validateLocation(operation, shopReference);
        validateState(state, timestamps, lastError, retryMetadata);
    }

    public static EscrowTransaction create(
            EscrowTransactionId transactionId,
            Optional<EscrowTransactionId> parentTransactionId,
            EscrowRequestKey requestKey,
            EscrowOperation operation,
            Set<EscrowParticipant> participants,
            List<EscrowAssetLot> assetLots,
            Instant createdAt,
            long configRevision,
            Optional<DimensionAwareShopReference> shopReference
    ) {
        return new EscrowTransaction(
                transactionId,
                parentTransactionId,
                requestKey,
                operation,
                EscrowState.CREATED,
                participants,
                assetLots,
                EscrowTimestamps.createdAt(createdAt),
                0L,
                configRevision,
                Optional.empty(),
                EscrowRetryMetadata.none(),
                shopReference
        );
    }

    public EscrowTransaction transitionTo(EscrowState targetState, Instant at) {
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(at, "at");
        state.requireTransitionTo(targetState);
        if (targetState == EscrowState.RECOVERY_REQUIRED) {
            throw new IllegalStateException("Recovery transitions require error and retry metadata");
        }
        if (state == EscrowState.MANUAL_REVIEW) {
            throw new IllegalStateException("Manual review requires an explicit recovery decision");
        }
        if (state == EscrowState.RECOVERY_REQUIRED
                && targetState != EscrowState.REFUND_PENDING
                && targetState != EscrowState.MANUAL_REVIEW
                && !retryMetadata.resumeState().filter(targetState::equals).isPresent()) {
            throw new IllegalStateException("Recovery can only resume its recorded state");
        }
        EscrowRetryMetadata nextRetry = state == EscrowState.RECOVERY_REQUIRED
                ? retryMetadata.clearSchedule()
                : retryMetadata;
        return copy(
                targetState,
                timestamps.advance(targetState, at),
                incrementRevision(),
                lastError,
                nextRetry
        );
    }

    public EscrowTransaction requireRecovery(
            EscrowError error,
            int maxAttempts,
            Instant nextAttemptAt,
            Instant at
    ) {
        EscrowState resumeState = state == EscrowState.ABORTING
                ? EscrowState.REFUND_PENDING
                : state;
        return requireRecoveryFrom(resumeState, error, maxAttempts, nextAttemptAt, at);
    }

    public EscrowTransaction requireRecoveryFrom(
            EscrowState resumeState,
            EscrowError error,
            int maxAttempts,
            Instant nextAttemptAt,
            Instant at
    ) {
        Objects.requireNonNull(resumeState, "resumeState");
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        Objects.requireNonNull(at, "at");
        state.requireTransitionTo(EscrowState.RECOVERY_REQUIRED);
        if (state != EscrowState.MANUAL_REVIEW
                && resumeState != state
                && !(state == EscrowState.ABORTING && resumeState == EscrowState.REFUND_PENDING)) {
            throw new IllegalArgumentException("Recovery resume state must match current state");
        }
        if (!error.retryable()) {
            throw new IllegalArgumentException("Escrow recovery requires a retryable error");
        }
        if (error.occurredAt().isAfter(at)) {
            throw new IllegalArgumentException("Escrow error occurs after its transaction update");
        }
        if (nextAttemptAt.isBefore(at)) {
            throw new IllegalArgumentException("Escrow retry time precedes its transaction update");
        }
        EscrowRetryMetadata nextRetry = retryMetadata.schedule(resumeState, maxAttempts, nextAttemptAt);
        return copy(
                EscrowState.RECOVERY_REQUIRED,
                timestamps.touch(at),
                incrementRevision(),
                Optional.of(error),
                nextRetry
        );
    }

    public EscrowTransaction resolveManualReviewTo(
            EscrowState targetState,
            Instant at
    ) {
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(at, "at");
        if (state != EscrowState.MANUAL_REVIEW) {
            throw new IllegalStateException("Transaction is not in manual review");
        }
        state.requireTransitionTo(targetState);
        if (targetState == EscrowState.RECOVERY_REQUIRED) {
            throw new IllegalArgumentException("Manual recovery requires retry metadata");
        }
        return copy(
                targetState,
                timestamps.advance(targetState, at),
                incrementRevision(),
                lastError,
                retryMetadata.clearSchedule()
        );
    }

    private EscrowTransaction copy(
            EscrowState nextState,
            EscrowTimestamps nextTimestamps,
            long nextRevision,
            Optional<EscrowError> nextError,
            EscrowRetryMetadata nextRetry
    ) {
        return new EscrowTransaction(
                transactionId,
                parentTransactionId,
                requestKey,
                operation,
                nextState,
                participants,
                assetLots,
                nextTimestamps,
                nextRevision,
                configRevision,
                nextError,
                nextRetry,
                shopReference
        );
    }

    private long incrementRevision() {
        return Math.addExact(revision, 1L);
    }

    private static void validateIdentity(
            EscrowTransactionId transactionId,
            Optional<EscrowTransactionId> parentTransactionId,
            long revision,
            long configRevision
    ) {
        if (parentTransactionId.filter(transactionId::equals).isPresent()) {
            throw new IllegalArgumentException("Escrow transaction cannot be its own parent");
        }
        if (revision < 0L || configRevision < 0L) {
            throw new IllegalArgumentException("Escrow revisions cannot be negative");
        }
    }

    private static void validateParticipants(Set<EscrowParticipant> participants) {
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("Escrow transaction requires participants");
        }
        Set<EscrowParty> parties = new HashSet<>();
        boolean hasInitiator = false;
        for (EscrowParticipant participant : participants) {
            if (!parties.add(participant.party())) {
                throw new IllegalArgumentException("Escrow party must have one participant record");
            }
            hasInitiator |= participant.hasRole(EscrowParticipantRole.INITIATOR);
        }
        if (!hasInitiator) {
            throw new IllegalArgumentException("Escrow transaction requires an initiator");
        }
    }

    private static void validateAssets(
            Set<EscrowParticipant> participants,
            List<EscrowAssetLot> assetLots
    ) {
        if (assetLots.isEmpty()) {
            throw new IllegalArgumentException("Escrow transaction requires asset lots");
        }
        Set<EscrowParty> parties = new HashSet<>();
        for (EscrowParticipant participant : participants) {
            parties.add(participant.party());
        }
        Set<UUID> lotIds = new HashSet<>();
        for (EscrowAssetLot assetLot : assetLots) {
            if (!lotIds.add(assetLot.lotId())) {
                throw new IllegalArgumentException("Escrow asset lot ids must be unique");
            }
            if (!parties.contains(assetLot.source()) || !parties.contains(assetLot.destination())) {
                throw new IllegalArgumentException("Escrow asset parties must be transaction participants");
            }
        }
    }

    private static void validateLocation(
            EscrowOperation operation,
            Optional<DimensionAwareShopReference> shopReference
    ) {
        if (operation.requiresShopReference() && shopReference.isEmpty()) {
            throw new IllegalArgumentException("Shop escrow operation requires a dimension aware shop reference");
        }
    }

    private static void validateState(
            EscrowState state,
            EscrowTimestamps timestamps,
            Optional<EscrowError> lastError,
            EscrowRetryMetadata retryMetadata
    ) {
        if (state.isTerminal() != timestamps.terminalAt().isPresent()) {
            throw new IllegalArgumentException("Escrow terminal state and timestamp do not match");
        }
        if (state.requiresCommitDecision() && timestamps.commitDecidedAt().isEmpty()) {
            throw new IllegalArgumentException("Escrow committed state requires a commit decision time");
        }
        if ((state == EscrowState.ABORTING
                || state == EscrowState.REFUND_PENDING
                || state == EscrowState.REFUNDED)
                && timestamps.commitDecidedAt().isPresent()) {
            throw new IllegalArgumentException("Escrow cannot refund after its commit decision");
        }
        if (state == EscrowState.RECOVERY_REQUIRED) {
            if (lastError.isEmpty() || !retryMetadata.isScheduled()) {
                throw new IllegalArgumentException("Escrow recovery requires an error and scheduled retry");
            }
            EscrowState resumeState = retryMetadata.resumeState().orElseThrow();
            if (!state.canTransitionTo(resumeState)) {
                throw new IllegalArgumentException("Escrow recovery cannot transition to its resume state");
            }
            if (resumeState.requiresCommitDecision() && timestamps.commitDecidedAt().isEmpty()) {
                throw new IllegalArgumentException("Escrow recovery state requires a commit decision time");
            }
            if (resumeState == EscrowState.REFUND_PENDING && timestamps.commitDecidedAt().isPresent()) {
                throw new IllegalArgumentException("Escrow cannot recover to refund after its commit decision");
            }
        } else if (retryMetadata.isScheduled()) {
            throw new IllegalArgumentException("Only escrow recovery can have a scheduled retry");
        }
        if (state == EscrowState.MANUAL_REVIEW && lastError.isEmpty()) {
            throw new IllegalArgumentException("Manual review requires an escrow error");
        }
        lastError.ifPresent(error -> {
            if (error.occurredAt().isBefore(timestamps.createdAt())
                    || error.occurredAt().isAfter(timestamps.updatedAt())) {
                throw new IllegalArgumentException("Escrow error time is outside its transaction range");
            }
        });
    }
}
