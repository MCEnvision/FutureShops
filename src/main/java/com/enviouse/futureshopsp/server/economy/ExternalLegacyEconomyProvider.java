package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.BalanceSnapshot;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderError;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RequestId;

import java.util.List;
import java.util.UUID;

/** Legacy view for a public provider selected by the server. */
final class ExternalLegacyEconomyProvider implements com.enviouse.futureshopsp.server.economy.EconomyProvider {
    private final com.enviouse.futureshopsp.api.economy.EconomyProvider provider;
    private final EconomyTransactionCoordinator coordinator;

    ExternalLegacyEconomyProvider(com.enviouse.futureshopsp.api.economy.EconomyProvider provider,
                                  EconomyTransactionCoordinator coordinator) {
        this.provider = provider;
        this.coordinator = coordinator;
    }

    @Override
    public long getBalance(UUID playerUUID) {
        ProviderResult<BalanceSnapshot> result = coordinator.balance(playerUUID);
        if (!result.confirmed()) {
            throw new EconomyUnavailableException(provider.providerId(),
                    coordinator.lifecycle().lifecycle().name(), result.diagnostic());
        }
        return result.value().orElseThrow().balanceMinorUnits();
    }

    @Override
    public TransactionResult withdraw(UUID playerUUID, long amountMinorUnits) {
        return mutate(playerUUID, amountMinorUnits, MutationKind.WITHDRAW, false);
    }

    @Override
    public TransactionResult deposit(UUID playerUUID, long amountMinorUnits) {
        return mutate(playerUUID, amountMinorUnits, MutationKind.DEPOSIT, true);
    }

    @Override
    public TransactionResult transfer(UUID fromPlayerUUID, UUID toPlayerUUID, long amountMinorUnits) {
        return map(coordinator.transfer(fromPlayerUUID, toPlayerUUID, amountMinorUnits));
    }

    @Override
    public List<BalanceEntry> getTopBalances(int page, int pageSize) {
        return List.of();
    }

    @Override
    public String getCurrencyName() {
        return provider.currency().singularName();
    }

    @Override
    public int getDecimalPlaces() {
        return provider.currency().decimalPlaces();
    }

    private TransactionResult mutate(UUID playerUUID, long amountMinorUnits, MutationKind kind, boolean deposit) {
        try {
            MutationRequest request = MutationRequest.forPlayer(RequestId.random(), playerUUID,
                    amountMinorUnits, kind);
            return map(deposit ? coordinator.deposit(request) : coordinator.withdraw(request));
        } catch (IllegalArgumentException exception) {
            return TransactionResult.error(com.enviouse.futureshopsp.server.shop.ShopResultCode.INVALID_AMOUNT, 0L);
        }
    }

    private static TransactionResult map(ProviderResult<?> result) {
        long balance = result.receipt().flatMap(receipt -> receipt.resultingBalanceMinorUnits().isPresent()
                ? java.util.Optional.of(receipt.resultingBalanceMinorUnits().getAsLong()) : java.util.Optional.empty())
                .orElse(0L);
        if (result.confirmed()) {
            return TransactionResult.ok(balance);
        }
        com.enviouse.futureshopsp.server.shop.ShopResultCode code = result.error() == ProviderError.INSUFFICIENT_FUNDS
                ? com.enviouse.futureshopsp.server.shop.ShopResultCode.INSUFFICIENT_FUNDS
                : com.enviouse.futureshopsp.server.shop.ShopResultCode.SERVER_ERROR;
        return TransactionResult.error(code, balance);
    }
}
