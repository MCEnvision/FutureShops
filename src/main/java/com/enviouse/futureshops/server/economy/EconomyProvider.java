package com.enviouse.futureshops.server.economy;

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

    default TransactionResult transfer(UUID fromPlayerUUID, UUID toPlayerUUID,
                                       long amountMinorUnits) {
        return TransactionResult.error(
                com.enviouse.futureshops.server.shop.ShopResultCode.SERVER_ERROR,
                getBalance(fromPlayerUUID));
    }

    default List<BalanceEntry> getTopBalances(int page, int pageSize) {
        return List.of();
    }

    String getCurrencyName();

    int getDecimalPlaces();
}
