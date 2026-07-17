package com.enviouse.futureshops.server.escrow.claim;

public enum ClaimLiabilityCategory {
    MONEY(true),
    MONEY_REFUND(true),
    ITEM(false),
    PROTECTED_CASH(false),
    FOREIGN_CASH(false),
    BARTER_ITEM(false),
    ITEM_REFUND(false);

    private final boolean monetary;

    ClaimLiabilityCategory(boolean monetary) {
        this.monetary = monetary;
    }

    public boolean monetary() {
        return monetary;
    }
}
