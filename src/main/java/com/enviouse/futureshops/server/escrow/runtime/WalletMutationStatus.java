package com.enviouse.futureshops.server.escrow.runtime;

public enum WalletMutationStatus {
    APPLIED,
    REPLAYED,
    ALREADY_INITIALIZED,
    INVALID_AMOUNT,
    INVALID_TARGET,
    INSUFFICIENT_FUNDS,
    MAX_BALANCE_EXCEEDED,
    NEGATIVE_NOT_ALLOWED,
    ARITHMETIC_OVERFLOW,
    CONFLICT
}
