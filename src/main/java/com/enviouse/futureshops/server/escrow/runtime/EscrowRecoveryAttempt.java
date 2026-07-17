package com.enviouse.futureshops.server.escrow.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record EscrowRecoveryAttempt(EscrowRecoveryDisposition disposition, String detail,
                                    Optional<Instant> nextAttemptAt) {
    public EscrowRecoveryAttempt {
        Objects.requireNonNull(disposition, "disposition");
        detail = Objects.requireNonNull(detail, "detail").trim();
        nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        if (detail.isEmpty() || detail.length() > 1024) {
            throw new IllegalArgumentException("Invalid escrow recovery detail");
        }
        if ((disposition == EscrowRecoveryDisposition.RETRY_LATER)
                != nextAttemptAt.isPresent()) {
            throw new IllegalArgumentException("Escrow retry disposition requires a retry time");
        }
    }

    public static EscrowRecoveryAttempt progressed(String detail) {
        return new EscrowRecoveryAttempt(
                EscrowRecoveryDisposition.PROGRESSED, detail, Optional.empty());
    }

    public static EscrowRecoveryAttempt retryLater(Instant nextAttemptAt, String detail) {
        return new EscrowRecoveryAttempt(EscrowRecoveryDisposition.RETRY_LATER, detail,
                Optional.of(Objects.requireNonNull(nextAttemptAt, "nextAttemptAt")));
    }

    public static EscrowRecoveryAttempt manualReview(String detail) {
        return new EscrowRecoveryAttempt(
                EscrowRecoveryDisposition.MANUAL_REVIEW, detail, Optional.empty());
    }

    public static EscrowRecoveryAttempt stable(String detail) {
        return new EscrowRecoveryAttempt(
                EscrowRecoveryDisposition.STABLE, detail, Optional.empty());
    }

    public static EscrowRecoveryAttempt resolved(String detail) {
        return new EscrowRecoveryAttempt(
                EscrowRecoveryDisposition.RESOLVED, detail, Optional.empty());
    }
}
