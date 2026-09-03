package com.enviouse.futureshopsp.api.economy;

/** Independent capabilities that a provider must prove before use. */
public enum EconomyCapability {
    BALANCE_QUERY,
    PRECHECK,
    WITHDRAW,
    DEPOSIT,
    RECEIPT_LOOKUP,
    IDEMPOTENT_RETRY
}
