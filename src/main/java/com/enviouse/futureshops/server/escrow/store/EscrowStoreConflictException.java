package com.enviouse.futureshops.server.escrow.store;

public final class EscrowStoreConflictException extends IllegalStateException {
    public EscrowStoreConflictException(String message) {
        super(message);
    }

    public EscrowStoreConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
