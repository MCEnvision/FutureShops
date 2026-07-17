package com.enviouse.futureshops.server.escrow.stock;

public final class StockConflictException extends RuntimeException {
    public StockConflictException(String message) {
        super(message);
    }

    public StockConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
