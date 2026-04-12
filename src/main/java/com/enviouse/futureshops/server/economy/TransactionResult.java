package com.enviouse.futureshops.server.economy;

public record TransactionResult(boolean success, long resultingBalance, String errorCode) {
    public static TransactionResult ok(long resultingBalance) {
        return new TransactionResult(true, resultingBalance, "");
    }

    public static TransactionResult error(String errorCode, long currentBalance) {
        return new TransactionResult(false, currentBalance, errorCode);
    }
}

