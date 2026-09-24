package com.enviouse.futureshops.server.escrow.coordinator;

/** Durable state for one account bound monetary leg. */
public enum OperationState {
    PREPARED,
    SUBMITTED,
    CONFIRMED,
    REJECTED,
    UNKNOWN,
    REPLAYED,
    COMPENSATING,
    FROZEN,
    RESOLVED;

    public boolean canTransitionTo(OperationState next) {
        if (next == null || next == this) {
            return next == this;
        }
        return switch (this) {
            case PREPARED -> next == SUBMITTED || next == REJECTED
                    || next == FROZEN || next == RESOLVED;
            case SUBMITTED -> next == CONFIRMED || next == REJECTED
                    || next == UNKNOWN || next == FROZEN;
            case CONFIRMED, REJECTED -> next == RESOLVED;
            case UNKNOWN -> next == CONFIRMED || next == REJECTED
                    || next == COMPENSATING || next == FROZEN;
            case REPLAYED -> next == RESOLVED;
            case COMPENSATING -> next == SUBMITTED || next == FROZEN;
            case FROZEN -> next == RESOLVED;
            case RESOLVED -> false;
        };
    }
}
