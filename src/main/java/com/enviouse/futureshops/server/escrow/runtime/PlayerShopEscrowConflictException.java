package com.enviouse.futureshops.server.escrow.runtime;

public final class PlayerShopEscrowConflictException
        extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PlayerShopEscrowConflictException() {
        super("Player shop escrow lifecycle conflicts with durable state");
    }
}
