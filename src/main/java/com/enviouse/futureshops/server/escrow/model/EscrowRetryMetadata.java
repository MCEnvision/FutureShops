package com.enviouse.futureshops.server.escrow.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record EscrowRetryMetadata(
        int attemptCount,
        int maxAttempts,
        Optional<Instant> nextAttemptAt,
        Optional<EscrowState> resumeState
) {
    public EscrowRetryMetadata {
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        Objects.requireNonNull(resumeState, "resumeState");
        if (attemptCount < 0 || maxAttempts < 0 || attemptCount > maxAttempts) {
            throw new IllegalArgumentException("Invalid escrow retry counts");
        }
        if (nextAttemptAt.isPresent() != resumeState.isPresent()) {
            throw new IllegalArgumentException("Retry time and resume state must appear together");
        }
        if (nextAttemptAt.isPresent() && (attemptCount == 0 || maxAttempts == 0)) {
            throw new IllegalArgumentException("Scheduled retry requires positive counts");
        }
        resumeState.ifPresent(EscrowRetryMetadata::validateResumeState);
    }

    public static EscrowRetryMetadata none() {
        return new EscrowRetryMetadata(0, 0, Optional.empty(), Optional.empty());
    }

    public EscrowRetryMetadata schedule(EscrowState targetState, int requestedMaxAttempts, Instant scheduledAt) {
        Objects.requireNonNull(targetState, "targetState");
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        validateResumeState(targetState);
        if (requestedMaxAttempts <= 0) {
            throw new IllegalArgumentException("Maximum retry attempts must be positive");
        }
        if (maxAttempts != 0 && maxAttempts != requestedMaxAttempts) {
            throw new IllegalArgumentException("Maximum retry attempts cannot change");
        }
        int nextAttempt = Math.addExact(attemptCount, 1);
        if (nextAttempt > requestedMaxAttempts) {
            throw new IllegalStateException("Escrow retry attempts exhausted");
        }
        return new EscrowRetryMetadata(
                nextAttempt,
                requestedMaxAttempts,
                Optional.of(scheduledAt),
                Optional.of(targetState)
        );
    }

    public EscrowRetryMetadata clearSchedule() {
        return new EscrowRetryMetadata(attemptCount, maxAttempts, Optional.empty(), Optional.empty());
    }

    public boolean isScheduled() {
        return nextAttemptAt.isPresent();
    }

    private static void validateResumeState(EscrowState state) {
        if (state == EscrowState.CREATED
                || state == EscrowState.VALIDATED
                || state == EscrowState.ABORTING
                || state == EscrowState.RECOVERY_REQUIRED
                || state == EscrowState.MANUAL_REVIEW
                || state.isTerminal()) {
            throw new IllegalArgumentException("Invalid escrow retry resume state");
        }
    }
}
