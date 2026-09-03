package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.BalanceSnapshot;
import com.enviouse.futureshopsp.api.economy.CurrencyMetadata;
import com.enviouse.futureshopsp.api.economy.EconomyApi;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderCapabilities;
import com.enviouse.futureshopsp.api.economy.ProviderError;
import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;
import com.enviouse.futureshopsp.api.economy.ProviderReadiness;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RequestId;

import java.util.OptionalLong;
import java.util.UUID;

/** Public API adapter for the existing internal provider. */
final class PublicInternalEconomyProvider implements com.enviouse.futureshopsp.api.economy.EconomyProvider {
    private final com.enviouse.futureshopsp.server.economy.EconomyProvider oldProvider;
    private final InternalEconomyReceiptStore receipts;
    private final ProviderCapabilities capabilities = ProviderCapabilities.all();

    PublicInternalEconomyProvider(com.enviouse.futureshopsp.server.economy.EconomyProvider oldProvider,
                                  InternalEconomyReceiptStore receipts) {
        this.oldProvider = oldProvider;
        this.receipts = receipts;
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
        return new CurrencyMetadata(oldProvider.getCurrencyName(), oldProvider.getCurrencyName(),
                oldProvider.getDecimalPlaces());
    }

    @Override
    public ProviderCapabilities capabilities() {
        return capabilities;
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
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION, "internal balance query failed");
        }
    }

    @Override
    public ProviderResult<BalanceSnapshot> precheck(MutationRequest request) {
        try {
            long balance = oldProvider.getBalance(request.actor());
            if (request.kind() == MutationKind.WITHDRAW || request.kind() == MutationKind.TRANSFER_DEBIT) {
                if (balance < request.amountMinorUnits()) {
                    return ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS, "insufficient funds");
                }
            }
            return ProviderResult.confirmed(new BalanceSnapshot(request.actor(), balance));
        } catch (RuntimeException exception) {
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION, "internal precheck failed");
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
        return receipts.find(requestId).map(ProviderResult::confirmed)
                .orElseGet(() -> ProviderResult.rejected(ProviderError.RECEIPT_NOT_FOUND, "receipt not found"));
    }

    @Override
    public ProviderResult<MutationReceipt> retry(MutationRequest request) {
        return receipts.find(request.requestId()).map(ProviderResult::confirmed)
                .orElseGet(() -> mutate(request, request.kind() == MutationKind.DEPOSIT
                        || request.kind() == MutationKind.TRANSFER_CREDIT));
    }

    private ProviderResult<MutationReceipt> mutate(MutationRequest request, boolean deposit) {
        ProviderResult<MutationReceipt> existing = receipts.find(request.requestId()).map(ProviderResult::confirmed)
                .orElse(null);
        if (existing != null) return existing;
        String reason = switch (request.kind()) {
            case WITHDRAW -> "WITHDRAW";
            case DEPOSIT -> "DEPOSIT";
            case TRANSFER_DEBIT, TRANSFER_CREDIT -> "TRANSFER";
            case FEE -> "FEE";
            case REFUND -> "REFUND";
            case COMPENSATION -> "COMPENSATION";
        };
        TransactionResult result = deposit
                ? oldProvider.deposit(request.actor(), request.amountMinorUnits(), reason)
                : oldProvider.withdraw(request.actor(), request.amountMinorUnits(), reason);
        if (!result.success()) {
            return ProviderResult.rejected(mapError(result), result.errorCode().name());
        }
        MutationReceipt receipt = new MutationReceipt(request.requestId(), request.kind(),
                request.amountMinorUnits(), request.requestId().value().toString(),
                OptionalLong.of(result.resultingBalance()));
        receipts.put(receipt);
        return ProviderResult.confirmed(receipt);
    }

    private static ProviderError mapError(TransactionResult result) {
        return switch (result.errorCode()) {
            case INSUFFICIENT_FUNDS -> ProviderError.INSUFFICIENT_FUNDS;
            case INVALID_AMOUNT -> ProviderError.INVALID_REQUEST;
            default -> ProviderError.PROVIDER_EXCEPTION;
        };
    }
}
