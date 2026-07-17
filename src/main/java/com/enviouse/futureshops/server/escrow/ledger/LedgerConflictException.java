package com.enviouse.futureshops.server.escrow.ledger;

public final class LedgerConflictException extends RuntimeException {
    public LedgerConflictException(String message) {
        super(message);
    }
}
