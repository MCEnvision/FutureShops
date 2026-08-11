package com.enviouse.futureshops.server.escrow.runtime;

public enum ExactItemClaimCollectionStatus {
    DELIVERED,
    PARTIALLY_DELIVERED,
    REPLAYED,
    FULL_INVENTORY,
    OFFLINE_PENDING,
    NOT_PENDING,
    INVALID_PAYLOAD,
    RECOVERY_REQUIRED,
    MANUAL_REVIEW
}
