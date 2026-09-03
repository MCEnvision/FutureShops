package com.enviouse.futureshopsp.api.economy;

/** Server owned lifecycle states exposed by the provider contract. */
public enum ProviderLifecycle {
    UNRESOLVED,
    READY,
    DRAINING,
    MISSING,
    INCOMPATIBLE,
    FAILED,
    RECOVERING,
    FROZEN,
    STOPPED
}
