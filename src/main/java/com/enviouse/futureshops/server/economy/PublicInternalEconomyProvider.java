package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.api.economy.BalanceSnapshot;
import com.enviouse.futureshops.api.economy.CurrencyMetadata;
import com.enviouse.futureshops.api.economy.EconomyApi;
import com.enviouse.futureshops.api.economy.MutationKind;
import com.enviouse.futureshops.api.economy.MutationReceipt;
import com.enviouse.futureshops.api.economy.MutationRequest;
import com.enviouse.futureshops.api.economy.ProviderCapabilities;
import com.enviouse.futureshops.api.economy.ProviderError;
import com.enviouse.futureshops.api.economy.ProviderLifecycle;
import com.enviouse.futureshops.api.economy.ProviderReadiness;
import com.enviouse.futureshops.api.economy.ProviderResult;
import com.enviouse.futureshops.api.economy.RequestId;

import java.util.Map;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** API projection for the existing internal provider. */
final class PublicInternalEconomyProvider implements com.enviouse.futureshops.api.economy.EconomyProvider {
    private final EconomyProvider oldProvider;
    private final Map<RequestId, MutationReceipt> receipts = new ConcurrentHashMap<>();

    PublicInternalEconomyProvider(EconomyProvider oldProvider) {
        this.oldProvider = oldProvider;
    }

    @Override
    public String providerId() {
        return EconomyApi.INTERNAL_PROVIDER_ID;
    }

    @Override
    public int compatibilityVersion() {
        return EconomyApi.COMPATIBILITY_VERSION;
    }

    @Override
    public CurrencyMetadata currency() {
        return new CurrencyMetadata(oldProvider.getCurrencyName(),
                oldProvider.getCurrencyName(), oldProvider.getDecimalPlaces());
    }

    @Override
    public ProviderCapabilities capabilities() {
        return ProviderCapabilities.all();
    }

    @Override
    public ProviderReadiness readiness() {
        return new ProviderReadiness(ProviderLifecycle.READY, "");
    }

    @Override
    public ProviderResult<BalanceSnapshot> balance(UUID playerId) {
        try {
            return ProviderResult.confirmed(new BalanceSnapshot(playerId, oldProvider.getBalance(playerId)));
        } catch (RuntimeException exception) {
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                    "internal balance query failed");
        }
    }

    @Override
    public com.enviouse.futureshops.api.economy.QueryResult<List<BalanceSnapshot>> leaderboard(
            int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            return com.enviouse.futureshops.api.economy.QueryResult.unavailable(
                    ProviderError.INVALID_REQUEST, "leaderboard page bounds are invalid");
        }
        try {
            List<BalanceSnapshot> entries = oldProvider.getTopBalances(page, pageSize).stream()
                    .map(entry -> new BalanceSnapshot(entry.playerUUID(), entry.balanceMinorUnits()))
                    .toList();
            return com.enviouse.futureshops.api.economy.QueryResult.confirmed(entries);
        } catch (RuntimeException exception) {
            return com.enviouse.futureshops.api.economy.QueryResult.unavailable(
                    ProviderError.PROVIDER_EXCEPTION, "internal leaderboard query failed");
        }
    }

    @Override
    public ProviderResult<BalanceSnapshot> precheck(MutationRequest request) {
        try {
            long balance = oldProvider.getBalance(request.actor());
            if ((request.kind() == MutationKind.WITHDRAW
                    || request.kind() == MutationKind.TRANSFER_DEBIT)
                    && balance < request.amountMinorUnits()) {
                return ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS,
                        "insufficient funds");
            }
            return ProviderResult.confirmed(new BalanceSnapshot(request.actor(), balance));
        } catch (RuntimeException exception) {
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                    "internal precheck failed");
        }
    }

    @Override
    public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
        return mutate(request, false);
    }

    @Override
    public ProviderResult<MutationReceipt> deposit(MutationRequest request) {
        return mutate(request, true);
    }

    @Override
    public ProviderResult<MutationReceipt> lookup(RequestId requestId) {
        MutationReceipt receipt = receipts.get(requestId);
        return receipt == null
                ? ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND, "receipt not found")
                : ProviderResult.confirmed(receipt);
    }

    @Override
    public ProviderResult<MutationReceipt> retry(MutationRequest request) {
        ProviderResult<MutationReceipt> existing = lookup(request.requestId());
        return existing.confirmed() ? existing : mutate(request,
                request.kind() == MutationKind.DEPOSIT
                        || request.kind() == MutationKind.TRANSFER_CREDIT
                        || request.kind() == MutationKind.REFUND
                        || request.kind() == MutationKind.COMPENSATION);
    }

    private ProviderResult<MutationReceipt> mutate(MutationRequest request, boolean deposit) {
        ProviderResult<MutationReceipt> existing = lookup(request.requestId());
        if (existing.confirmed()) {
            return existing;
        }
        String reason = request.kind().name();
        TransactionResult result = deposit
                ? oldProvider.deposit(request.actor(), request.amountMinorUnits(), reason)
                : oldProvider.withdraw(request.actor(), request.amountMinorUnits(), reason);
        if (!result.success()) {
            ProviderError error = result.errorCode()
                    == com.enviouse.futureshops.server.shop.ShopResultCode.INSUFFICIENT_FUNDS
                    ? ProviderError.INSUFFICIENT_FUNDS : ProviderError.PROVIDER_EXCEPTION;
            return ProviderResult.rejected(error, result.errorCode().name());
        }
        MutationReceipt receipt = new MutationReceipt(request.requestId(), request.kind(),
                request.amountMinorUnits(), request.requestId().value().toString(),
                OptionalLong.of(result.resultingBalance()));
        receipts.put(request.requestId(), receipt);
        return ProviderResult.confirmed(receipt);
    }
}
