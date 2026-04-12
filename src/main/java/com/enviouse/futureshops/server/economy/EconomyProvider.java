package com.enviouse.futureshops.server.economy;

import java.util.UUID;

public interface EconomyProvider {
    long getBalance(UUID playerUUID);

    TransactionResult withdraw(UUID playerUUID, long amountMinorUnits);

    TransactionResult deposit(UUID playerUUID, long amountMinorUnits);

    String getCurrencyName();

    int getDecimalPlaces();
}

