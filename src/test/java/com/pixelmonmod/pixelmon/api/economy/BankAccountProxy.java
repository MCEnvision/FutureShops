package com.pixelmonmod.pixelmon.api.economy;

import java.util.UUID;

public final class BankAccountProxy {
    private static boolean implementation = true;
    private static BankAccount account;

    private BankAccountProxy() {
    }

    public static boolean hasImplementation() {
        return implementation;
    }

    public static BankAccount getBankAccountNow(UUID playerId) {
        return account;
    }

    public static void setImplementation(boolean value) {
        implementation = value;
    }

    public static void setAccount(BankAccount value) {
        account = value;
    }
}
