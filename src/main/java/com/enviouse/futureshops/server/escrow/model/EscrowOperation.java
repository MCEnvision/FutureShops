package com.enviouse.futureshops.server.escrow.model;

public enum EscrowOperation {
    SERVER_SHOP_BUY(true),
    SERVER_SHOP_SELL(true),
    SERVER_SHOP_BARTER(true),
    SERVER_SHOP_CART(true),
    PLAYER_SHOP_BUY(true),
    PLAYER_SHOP_BARTER(true),
    PLAYER_SHOP_COMPOUND(true),
    PLAYER_SHOP_BUYBACK(true),
    PLAYER_SHOP_SETTLEMENT_CLAIM(true),
    ATM_WITHDRAWAL(false),
    CURRENCY_WITHDRAWAL(false),
    CURRENCY_DEPOSIT(false),
    PLAYER_PAYMENT(false),
    AUCTION_LISTING(false),
    AUCTION_BID(false),
    AUCTION_BUY_NOW(false),
    AUCTION_SETTLEMENT(false),
    BAZAAR_BUY_ORDER(false),
    BAZAAR_SELL_ORDER(false),
    BAZAAR_FILL(false),
    BAZAAR_CANCEL(false),
    CLAIM(false),
    ADMIN_BALANCE_MUTATION(false),
    ADMIN_RECOVERY(false),
    SERVER_SHOP_FUNDING_RELEASE(false);

    private final boolean shopReferenceRequired;

    EscrowOperation(boolean shopReferenceRequired) {
        this.shopReferenceRequired = shopReferenceRequired;
    }

    public boolean requiresShopReference() {
        return shopReferenceRequired;
    }
}
