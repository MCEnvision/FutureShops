package com.enviouse.futureshops.server.escrow.coordinator;

/** Lifecycle of the durable coordinator, independent from provider readiness. */
public enum CoordinatorLifecycle {
    READY,
    DRAINING,
    RECOVERING,
    FROZEN;

    public boolean canAcceptAdmissions() {
        return this == READY;
    }

    public boolean canTransitionTo(CoordinatorLifecycle next) {
        if (next == null || next == this) {
            return next == this;
        }
        return switch (this) {
            case READY -> next == DRAINING || next == RECOVERING || next == FROZEN;
            case DRAINING -> next == READY || next == RECOVERING || next == FROZEN;
            case RECOVERING -> next == READY || next == FROZEN;
            case FROZEN -> next == RECOVERING;
        };
    }
}
