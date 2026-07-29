package com.enviouse.futureshops.server.security;

public enum ServerRequestAction {
    ATM_DATA("atm.data"),
    ATM_WITHDRAWAL("atm.withdrawal"),
    ATM_CASH_COLLECTION("atm.cash_collection"),
    ATM_DEPOSIT("atm.deposit"),
    PAY("pay"),
    BULK_SELL("bulk_sell"),
    PLAYER_SHOP_OFFER("player_shop.offer"),
    PLAYER_SHOP_OFFER_ADMIN("player_shop.offer_admin"),
    SERVER_SHOP_OFFER("server_shop.offer"),
    SERVER_SHOP_OFFER_ADMIN("server_shop.offer_admin");

    private final String code;

    ServerRequestAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
