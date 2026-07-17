package com.enviouse.futureshops.server.escrow.custody;

public enum CustodyAssetType {
    WALLET_RESERVE,
    ITEM_STACK,
    PROTECTED_PHYSICAL_CURRENCY,
    FOREIGN_PHYSICAL_CURRENCY;

    public boolean requiresItemSnapshots() {
        return this != WALLET_RESERVE;
    }

    public boolean isCurrency() {
        return this == WALLET_RESERVE
                || this == PROTECTED_PHYSICAL_CURRENCY
                || this == FOREIGN_PHYSICAL_CURRENCY;
    }
}
