package com.enviouse.futureshops.server.escrow.custody;

public final class CustodyConflictException extends IllegalStateException {
    public CustodyConflictException(String message) {
        super(message);
    }

    public CustodyConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
