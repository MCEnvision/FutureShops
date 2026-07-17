package com.enviouse.futureshops.server.escrow.runtime;

public final class AtmPreparationException extends RuntimeException {
    private final AtmWithdrawalStatus status;

    public AtmPreparationException(AtmWithdrawalStatus status,
                                   String message) {
        super(message);
        this.status = java.util.Objects.requireNonNull(status, "status");
        if (status.success()) {
            throw new IllegalArgumentException(
                    "ATM preparation failure status is invalid");
        }
    }

    public AtmWithdrawalStatus status() {
        return status;
    }
}
