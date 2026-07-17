package com.enviouse.futureshops.server.economy.migration;

public enum WalletInitializationDisposition {
    APPLIED,
    REPLAYED,
    ALREADY_INITIALIZED,
    RETRY_LATER,
    CONFLICT
}
