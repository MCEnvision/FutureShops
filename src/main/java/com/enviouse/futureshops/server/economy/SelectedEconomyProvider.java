package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.api.economy.BalanceSnapshot;
import com.enviouse.futureshops.api.economy.CurrencyMetadata;
import com.enviouse.futureshops.api.economy.EconomyApi;
import com.enviouse.futureshops.api.economy.MutationKind;
import com.enviouse.futureshops.api.economy.MutationRequest;
import com.enviouse.futureshops.api.economy.ProviderError;
import com.enviouse.futureshops.api.economy.ProviderResult;
import com.enviouse.futureshops.api.economy.RequestId;
import com.enviouse.futureshops.server.shop.ShopResultCode;

import java.util.List;
import java.util.UUID;

/** Adapts the resolved public provider to the legacy shared economy surface. */
final class SelectedEconomyProvider implements EconomyProvider {
    private final com.enviouse.futureshops.api.economy.EconomyProvider delegate;
    private final CurrencyMetadata currency;

    SelectedEconomyProvider(
            com.enviouse.futureshops.api.economy.EconomyProvider delegate,
            CurrencyMetadata currency
    ) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
        this.currency = java.util.Objects.requireNonNull(currency, "currency");
    }

    @Override
    public long getBalance(UUID playerUUID) {
        ProviderResult<BalanceSnapshot> result = delegate.balance(playerUUID);
        if (!result.confirmed()) {
            throw new EconomyUnavailableException(delegate.providerId(),
                    result.status().name(), result.diagnostic());
        }
        return result.value().orElseThrow().balanceMinorUnits();
    }

    @Override
    public TransactionResult withdraw(UUID playerUUID, long amountMinorUnits) {
        return mutate(RequestId.random(), playerUUID, amountMinorUnits, MutationKind.WITHDRAW);
    }

    @Override
    public TransactionResult deposit(UUID playerUUID, long amountMinorUnits) {
        return mutate(RequestId.random(), playerUUID, amountMinorUnits, MutationKind.DEPOSIT);
    }

    TransactionResult mutate(
            RequestId requestId,
            UUID playerUUID,
            long amountMinorUnits,
            MutationKind kind
    ) {
        if (amountMinorUnits <= 0L) {
            return TransactionResult.error(ShopResultCode.INVALID_AMOUNT, safeBalance(playerUUID));
        }
        MutationRequest request = MutationRequest.forPlayer(
                requestId, playerUUID, amountMinorUnits, kind);
        ProviderResult<?> result = switch (kind) {
            case WITHDRAW, TRANSFER_DEBIT, FEE -> delegate.withdraw(request);
            case DEPOSIT, TRANSFER_CREDIT, REFUND, COMPENSATION -> delegate.deposit(request);
        };
        long resultingBalance = result.value()
                .filter(BalanceSnapshot.class::isInstance)
                .map(BalanceSnapshot.class::cast)
                .map(BalanceSnapshot::balanceMinorUnits)
                .orElseGet(() -> safeBalance(playerUUID));
        if (result.confirmed()) {
            return TransactionResult.ok(resultingBalance);
        }
        return TransactionResult.error(mapError(result.error()), resultingBalance);
    }

    @Override
    public TransactionResult transfer(UUID fromPlayerUUID, UUID toPlayerUUID,
                                      long amountMinorUnits) {
        // The v1 provider contract exposes single account legs only. Refuse a
        // cross account operation rather than debit one account and shadow credit
        // the other in the internal wallet.
        return TransactionResult.error(ShopResultCode.SERVER_ERROR,
                safeBalance(fromPlayerUUID));
    }

    @Override
    public List<BalanceEntry> getTopBalances(int page, int pageSize) {
        var result = delegate.leaderboard(page, pageSize);
        if (!result.confirmed()) {
            throw new EconomyUnavailableException(delegate.providerId(),
                    result.error().name(), result.diagnostic());
        }
        return result.value().orElseThrow().stream()
                .map(entry -> new BalanceEntry(entry.playerId(), entry.balanceMinorUnits()))
                .toList();
    }

    @Override
    public String getCurrencyName() {
        return currency.pluralName();
    }

    @Override
    public int getDecimalPlaces() {
        return currency.decimalPlaces();
    }

    @Override
    public String getProviderId() {
        return delegate.providerId();
    }

    private long safeBalance(UUID playerUUID) {
        try {
            ProviderResult<BalanceSnapshot> result = delegate.balance(playerUUID);
            return result.confirmed() ? result.value().orElseThrow().balanceMinorUnits() : 0L;
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private static ShopResultCode mapError(ProviderError error) {
        return switch (error) {
            case INVALID_AMOUNT, INVALID_PRECISION -> ShopResultCode.INVALID_AMOUNT;
            case INSUFFICIENT_FUNDS -> ShopResultCode.INSUFFICIENT_FUNDS;
            case PERMISSION_DENIED -> ShopResultCode.NOT_OWNER;
            default -> ShopResultCode.SERVER_ERROR;
        };
    }
}
