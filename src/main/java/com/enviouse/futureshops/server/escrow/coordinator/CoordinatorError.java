package com.enviouse.futureshops.server.escrow.coordinator;

/** Typed refusal reasons exposed by the durable coordinator boundary. */
public enum CoordinatorError {
    UNAUTHORIZED,
    CAPABILITY_UNAVAILABLE,
    INVALID_AMOUNT,
    INVALID_BINDING,
    PAYLOAD_CONFLICT,
    DUPLICATE_CONFLICT,
    REENTRANT_ADMISSION,
    LIFECYCLE_UNAVAILABLE,
    RECEIPT_MISSING,
    RECEIPT_MISMATCH,
    ORIGINAL_BINDING_UNAVAILABLE,
    CLAIM_ALREADY_COLLECTED,
    CLAIM_NOT_FOUND,
    STORAGE_CORRUPT
}
