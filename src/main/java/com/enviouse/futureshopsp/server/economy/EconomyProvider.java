package com.enviouse.futureshopsp.server.economy;

import java.util.List;
import java.util.UUID;

public interface EconomyProvider {
    long getBalance(UUID playerUUID);

    TransactionResult withdraw(UUID playerUUID, long amountMinorUnits);

    TransactionResult deposit(UUID playerUUID, long amountMinorUnits);

    /**
     * Withdraw with an explicit reason tag for {@code BalanceChangeEvent}.
     * Reason should be one of: {@code "BUY"}, {@code "SELL"}, {@code "TRANSFER"},
     * {@code "WITHDRAW"}, {@code "DEPOSIT"}, {@code "ADMIN"}.
     */
    default TransactionResult withdraw(UUID playerUUID, long amountMinorUnits, String reason) {
        return withdraw(playerUUID, amountMinorUnits);
    }

    /**
     * Deposit with an explicit reason tag for {@code BalanceChangeEvent}.
     */
    default TransactionResult deposit(UUID playerUUID, long amountMinorUnits, String reason) {
        return deposit(playerUUID, amountMinorUnits);
    }

    default TransactionResult transfer(UUID fromPlayerUUID, UUID toPlayerUUID, long amountMinorUnits) {
        TransactionResult withdrawal = withdraw(fromPlayerUUID, amountMinorUnits, "TRANSFER");
        if (!withdrawal.success()) {
            return withdrawal;
        }

        TransactionResult deposit = deposit(toPlayerUUID, amountMinorUnits, "TRANSFER");
        if (!deposit.success()) {
            deposit(fromPlayerUUID, amountMinorUnits, "TRANSFER");
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
