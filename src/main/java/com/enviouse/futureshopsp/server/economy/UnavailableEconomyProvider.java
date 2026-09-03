package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;
import com.enviouse.futureshopsp.server.shop.ShopResultCode;

import java.util.List;
import java.util.UUID;

/** Fail closed legacy boundary used until the strict coordinator owns every surface. */
final class UnavailableEconomyProvider implements EconomyProvider {
    private final String providerId;
    private final ProviderLifecycle lifecycle;
    private final String diagnostic;

    UnavailableEconomyProvider(String providerId, ProviderLifecycle lifecycle, String diagnostic) {
        this.providerId = providerId;
        this.lifecycle = lifecycle;
        this.diagnostic = diagnostic;
    }

    @Override
    public long getBalance(UUID playerUUID) {
        throw new EconomyUnavailableException(providerId, lifecycle.name(), diagnostic);
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
