package com.enviouse.futureshopsp.server.economy;

/** Durable state for one logical economy leg. */
public enum EconomyTransactionState {
    PREPARED,
    EXTERNAL_PENDING,
    EXTERNAL_CONFIRMED,
    DELIVERED,
    CLAIMED,
    UNCERTAIN,
    RESOLVED
}
