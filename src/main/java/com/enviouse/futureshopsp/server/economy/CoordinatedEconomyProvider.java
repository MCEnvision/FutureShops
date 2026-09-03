package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.BalanceSnapshot;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderError;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RequestId;
import com.enviouse.futureshopsp.server.shop.ShopResultCode;

import java.util.List;
import java.util.UUID;

/** Legacy provider view that routes all balance mutations through the strict coordinator. */
final class CoordinatedEconomyProvider implements EconomyProvider {
    private final com.enviouse.futureshopsp.api.economy.EconomyProvider publicProvider;
    private final EconomyTransactionCoordinator coordinator;

    CoordinatedEconomyProvider(com.enviouse.futureshopsp.api.economy.EconomyProvider publicProvider,
                               EconomyTransactionCoordinator coordinator) {
        this.publicProvider = publicProvider;
        this.coordinator = coordinator;
    }

    @Override
    public long getBalance(UUID playerUUID) {
        ProviderResult<BalanceSnapshot> result = coordinator.balance(playerUUID);
        if (result.confirmed()) {
            return result.value().orElseThrow().balanceMinorUnits();
        }
        throw new EconomyUnavailableException(publicProvider.providerId(),
                coordinator.lifecycle().lifecycle().name(), result.diagnostic());
    }

    @Override
    public TransactionResult withdraw(UUID playerUUID, long amountMinorUnits) {
        return mutate(playerUUID, null, amountMinorUnits, MutationKind.WITHDRAW);
    }

    @Override
    public TransactionResult deposit(UUID playerUUID, long amountMinorUnits) {
        return mutate(playerUUID, null, amountMinorUnits, MutationKind.DEPOSIT);
    }

    @Override
    public TransactionResult withdraw(UUID playerUUID, long amountMinorUnits, String reason) {
        return mutate(playerUUID, null, amountMinorUnits,
                "TRANSFER".equals(reason) ? MutationKind.TRANSFER_DEBIT : MutationKind.WITHDRAW);
    }

    @Override
    public TransactionResult deposit(UUID playerUUID, long amountMinorUnits, String reason) {
        return mutate(playerUUID, null, amountMinorUnits,
                "TRANSFER".equals(reason) ? MutationKind.TRANSFER_CREDIT : MutationKind.DEPOSIT);
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
        return publicProvider.currency().singularName();
    }

    @Override
    public int getDecimalPlaces() {
        return publicProvider.currency().decimalPlaces();
    }

    private TransactionResult mutate(UUID playerUUID, UUID counterparty, long amountMinorUnits,
                                     MutationKind kind) {
        try {
            MutationRequest request = new MutationRequest(RequestId.random(), playerUUID,
                    counterparty == null ? java.util.Optional.empty() : java.util.Optional.of(counterparty),
                    amountMinorUnits, kind);
            return map(kind == MutationKind.DEPOSIT || kind == MutationKind.TRANSFER_CREDIT
                    ? coordinator.deposit(request) : coordinator.withdraw(request));
        } catch (IllegalArgumentException exception) {
            return TransactionResult.error(ShopResultCode.INVALID_AMOUNT, 0L);
        }
    }

    private static TransactionResult map(ProviderResult<?> result) {
        long balance = result.receipt().flatMap(receipt -> receipt.resultingBalanceMinorUnits().isPresent()
                ? java.util.Optional.of(receipt.resultingBalanceMinorUnits().getAsLong()) : java.util.Optional.empty())
                .orElse(0L);
        if (result.confirmed()) {
            return TransactionResult.ok(balance);
        }
        return TransactionResult.error(mapError(result.error()), balance);
    }

    private static ShopResultCode mapError(ProviderError error) {
        return switch (error) {
            case INSUFFICIENT_FUNDS -> ShopResultCode.INSUFFICIENT_FUNDS;
            case INVALID_REQUEST -> ShopResultCode.INVALID_AMOUNT;
            default -> ShopResultCode.SERVER_ERROR;
        };
    }
}
