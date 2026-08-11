package com.enviouse.futureshops.server.escrow.item.runtime;

public enum ItemInventoryExecutionStatus {
    APPLIED,
    REPLAYED,
    INSUFFICIENT_CAPACITY,
    INSUFFICIENT_ITEMS,
    UNSUPPORTED_STACK,
    ABORTED,
    RECOVERY_REQUIRED,
    MANUAL_REVIEW
}
