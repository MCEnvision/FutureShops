package com.enviouse.futureshopsp.api.economy;

/** Explicit outcome class. No non-confirmed state carries an implicit zero or success. */
public enum ProviderResultStatus {
    CONFIRMED,
    REJECTED,
    UNAVAILABLE,
    AMBIGUOUS,
    RECOVERY_REQUIRED
}
