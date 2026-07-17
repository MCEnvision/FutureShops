package com.enviouse.futureshops.server.escrow.model;

import java.util.Objects;

public enum EscrowState {
    CREATED,
    VALIDATED,
    HOLDING,
    HELD,
    COMMIT_DECIDED,
    COMMITTED,
    CLAIMS_CREATED,
    COMPLETED,
    ABORTING,
    REFUND_PENDING,
    REFUNDED,
    RECOVERY_REQUIRED,
    MANUAL_REVIEW;

    public boolean canTransitionTo(EscrowState target) {
        Objects.requireNonNull(target, "target");
        return switch (this) {
            case CREATED -> target == VALIDATED || target == ABORTING;
            case VALIDATED -> target == HOLDING || target == ABORTING;
            case HOLDING -> target == HELD || target == ABORTING || target == RECOVERY_REQUIRED;
            case HELD -> target == COMMIT_DECIDED || target == ABORTING || target == RECOVERY_REQUIRED;
            case COMMIT_DECIDED -> target == COMMITTED || target == RECOVERY_REQUIRED;
            case COMMITTED -> target == CLAIMS_CREATED || target == RECOVERY_REQUIRED;
            case CLAIMS_CREATED -> target == COMPLETED || target == RECOVERY_REQUIRED;
            case ABORTING -> target == REFUND_PENDING || target == RECOVERY_REQUIRED;
            case REFUND_PENDING -> target == REFUNDED || target == RECOVERY_REQUIRED;
            case RECOVERY_REQUIRED -> target == HOLDING
                    || target == HELD
                    || target == COMMIT_DECIDED
                    || target == COMMITTED
                    || target == CLAIMS_CREATED
                    || target == REFUND_PENDING
                    || target == MANUAL_REVIEW;
            case MANUAL_REVIEW -> target == RECOVERY_REQUIRED
                    || target == REFUND_PENDING
                    || target == CLAIMS_CREATED;
            case COMPLETED, REFUNDED -> false;
        };
    }

    public void requireTransitionTo(EscrowState target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException("Illegal escrow transition from " + this + " to " + target);
        }
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == REFUNDED;
    }

    public boolean requiresCommitDecision() {
        return this == COMMIT_DECIDED || this == COMMITTED || this == CLAIMS_CREATED || this == COMPLETED;
    }
}
