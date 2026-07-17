package com.enviouse.futureshops.server.escrow.model;

public enum EscrowAssetLotType {
    WALLET_MONEY,
    PROTECTED_PHYSICAL_CURRENCY,
    FOREIGN_PHYSICAL_CURRENCY,
    ITEM_STACK,
    BARTER_BUNDLE,
    STOCK_RESERVATION,
    STORAGE_RESERVATION,
    FEE,
    TAX;

    public boolean isMoneyBacked() {
        return this == WALLET_MONEY
                || this == PROTECTED_PHYSICAL_CURRENCY
                || this == FOREIGN_PHYSICAL_CURRENCY
                || this == FEE
                || this == TAX;
    }

    public boolean requiresSerializedPayload() {
        return this == PROTECTED_PHYSICAL_CURRENCY
                || this == FOREIGN_PHYSICAL_CURRENCY
                || this == ITEM_STACK
                || this == BARTER_BUNDLE;
    }

    public boolean isReservation() {
        return this == STOCK_RESERVATION || this == STORAGE_RESERVATION;
    }
}
