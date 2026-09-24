package com.enviouse.futureshops.server.escrow.coordinator;

/** Checked failure at the coordinator boundary. */
public final class CoordinatorException extends Exception {
    private final CoordinatorError error;

    public CoordinatorException(CoordinatorError error, String message) {
        super(message);
        this.error = error;
    }

    public CoordinatorException(CoordinatorError error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }

    public CoordinatorError error() {
        return error;
    }
}
