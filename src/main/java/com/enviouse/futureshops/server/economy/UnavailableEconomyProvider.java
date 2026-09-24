package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.server.shop.ShopResultCode;

import java.util.List;
import java.util.UUID;

/** Fail closed legacy boundary for an unavailable external provider selection. */
final class UnavailableEconomyProvider implements EconomyProvider {
    private final String providerId;
    private final String lifecycle;
    private final String diagnostic;

    UnavailableEconomyProvider(String providerId, String lifecycle, String diagnostic) {
        this.providerId = providerId;
        this.lifecycle = lifecycle;
        this.diagnostic = diagnostic;
    }

    @Override
    public long getBalance(UUID playerUUID) {
        throw new EconomyUnavailableException(providerId, lifecycle, diagnostic);
    }

    @Override
    public TransactionResult withdraw(UUID playerUUID, long amountMinorUnits) {
        return unavailable();
    }

    @Override
    public TransactionResult deposit(UUID playerUUID, long amountMinorUnits) {
        return unavailable();
    }

    @Override
    public List<BalanceEntry> getTopBalances(int page, int pageSize) {
        return List.of();
    }

    @Override
    public String getCurrencyName() {
        return "Unavailable";
    }

    @Override
    public int getDecimalPlaces() {
        return 0;
    }

    private TransactionResult unavailable() {
        return TransactionResult.error(ShopResultCode.SERVER_ERROR, 0L);
    }
}
