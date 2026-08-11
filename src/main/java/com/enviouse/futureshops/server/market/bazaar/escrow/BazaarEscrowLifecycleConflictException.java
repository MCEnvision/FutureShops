package com.enviouse.futureshops.server.market.bazaar.escrow;

public final class BazaarEscrowLifecycleConflictException
        extends IllegalStateException {
    public BazaarEscrowLifecycleConflictException(String message) {
        super(message);
    }

    public BazaarEscrowLifecycleConflictException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
