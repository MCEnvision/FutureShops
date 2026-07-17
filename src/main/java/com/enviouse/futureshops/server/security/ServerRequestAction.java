package com.enviouse.futureshops.server.security;

public enum ServerRequestAction {
    ATM_DATA("atm.data"),
    ATM_WITHDRAWAL("atm.withdrawal"),
    ATM_CASH_COLLECTION("atm.cash_collection"),
    ATM_DEPOSIT("atm.deposit");

    private final String code;

    ServerRequestAction(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
