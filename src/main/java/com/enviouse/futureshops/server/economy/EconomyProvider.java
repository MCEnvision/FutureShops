package com.enviouse.futureshops.server.economy;

import java.util.List;
import java.util.UUID;

public interface EconomyProvider {
    long getBalance(UUID playerUUID);

    TransactionResult withdraw(UUID playerUUID, long amountMinorUnits);

    TransactionResult deposit(UUID playerUUID, long amountMinorUnits);

    default TransactionResult transfer(UUID fromPlayerUUID, UUID toPlayerUUID, long amountMinorUnits) {
        TransactionResult withdrawal = withdraw(fromPlayerUUID, amountMinorUnits);
        if (!withdrawal.success()) {
            return withdrawal;
        }

        TransactionResult deposit = deposit(toPlayerUUID, amountMinorUnits);
        if (!deposit.success()) {
            deposit(fromPlayerUUID, amountMinorUnits);
            return TransactionResult.error(deposit.errorCode(), withdrawal.resultingBalance());
        }

        return TransactionResult.ok(withdrawal.resultingBalance());
    }

    default List<BalanceEntry> getTopBalances(int page, int pageSize) {
        return List.of();
    }

    String getCurrencyName();

    int getDecimalPlaces();
}
