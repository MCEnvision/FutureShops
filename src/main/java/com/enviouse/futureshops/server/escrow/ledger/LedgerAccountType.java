package com.enviouse.futureshops.server.escrow.ledger;

public enum LedgerAccountType {
    PLAYER_WALLET(false),
    PLAYER_RESERVED(false),
    TRANSACTION_ESCROW(false),
    PLAYER_CLAIM(false),
    PROTECTED_CURRENCY_OUTSTANDING(false),
    FOREIGN_CURRENCY_SOURCE(true),
    FOREIGN_CURRENCY_SINK(false),
    SERVER_SHOP_SOURCE(true),
    SERVER_SHOP_SINK(false),
    AUCTION_FEE(false),
    BAZAAR_FEE(false),
    SERVER_TREASURY(false),
    ADMIN_SOURCE(true),
    ADMIN_SINK(false);

    private final boolean negativeAllowed;

    LedgerAccountType(boolean negativeAllowed) {
        this.negativeAllowed = negativeAllowed;
    }

    public boolean negativeAllowed() {
        return negativeAllowed;
    }
}
