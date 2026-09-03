package com.enviouse.futureshopsp.api.economy;

/** Deterministic registration outcome. */
public enum RegistrationStatus {
    ACCEPTED,
    INVALID_IDENTIFIER,
    INVALID_ARGUMENT,
    INCOMPATIBLE,
    RESERVED,
    DUPLICATE,
    LATE
}
