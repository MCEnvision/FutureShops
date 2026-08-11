package com.enviouse.futureshops.server.escrow.claim;

public enum ClaimKind {
    MONEY,
    ITEM,
    PROTECTED_CASH,
    FOREIGN_CASH,
    BARTER_ITEM,
    REFUND,
    INTERNAL_ESCROW_MONEY;

    public boolean publiclyVisible() {
        return this != INTERNAL_ESCROW_MONEY;
    }
}
