package com.enviouse.futureshops.server.escrow.runtime;

public final class EscrowRuntimeException extends RuntimeException {
    public EscrowRuntimeException(String message) {
        super(message);
    }

    public EscrowRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
