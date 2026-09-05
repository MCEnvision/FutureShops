package com.enviouse.futureshopsp.vaultproof;

import com.enviouse.futureshopsp.api.economy.BalanceSnapshot;
import com.enviouse.futureshopsp.api.economy.CurrencyMetadata;
import com.enviouse.futureshopsp.api.economy.EconomyApi;
import com.enviouse.futureshopsp.api.economy.EconomyProvider;
import com.enviouse.futureshopsp.api.economy.MutationKind;
import com.enviouse.futureshopsp.api.economy.MutationReceipt;
import com.enviouse.futureshopsp.api.economy.MutationRequest;
import com.enviouse.futureshopsp.api.economy.ProviderCapabilities;
import com.enviouse.futureshopsp.api.economy.ProviderError;
import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;
import com.enviouse.futureshopsp.api.economy.ProviderReadiness;
import com.enviouse.futureshopsp.api.economy.ProviderResult;
import com.enviouse.futureshopsp.api.economy.RequestId;

import java.util.UUID;

/** Public API adapter used only by the separately installed Vault proof registrant. */
public final class SqliteVaultProofProvider implements EconomyProvider {
    private final SqliteVaultProofBackend backend;

    public SqliteVaultProofProvider(SqliteVaultProofBackend backend) {
        this.backend = backend;
    }

    @Override
    public String providerId() {
        return EconomyApi.VAULT_PROVIDER_ID;
    }

    @Override
    public int compatibilityVersion() {
        return EconomyApi.COMPATIBILITY_VERSION;
    }

    @Override
    public CurrencyMetadata currency() {
        return new CurrencyMetadata("PokeDollar", "PokeDollars", 0);
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
        if (playerId == null) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "player id is required");
        }
        try {
            return ProviderResult.confirmed(new BalanceSnapshot(playerId, backend.balance(playerId)));
        } catch (RuntimeException exception) {
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                    "vault balance query failed");
        }
    }

    @Override
    public ProviderResult<BalanceSnapshot> precheck(MutationRequest request) {
        if (request == null || request.amountMinorUnits() <= 0L) {
            return ProviderResult.rejected(ProviderError.INVALID_REQUEST, "request is invalid");
        }
        try {
            long balance = backend.balance(request.actor());
            return !requiresFunds(request.kind()) || balance >= request.amountMinorUnits()
                    ? ProviderResult.confirmed(new BalanceSnapshot(request.actor(), balance))
                    : ProviderResult.rejected(ProviderError.INSUFFICIENT_FUNDS, "balance is insufficient");
        } catch (RuntimeException exception) {
            return ProviderResult.unavailable(ProviderError.PROVIDER_EXCEPTION,
                    "vault precheck failed");
        }
    }

    @Override
    public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
        return backend.mutate(request, false);
    }

    @Override
    public ProviderResult<MutationReceipt> deposit(MutationRequest request) {
        return backend.mutate(request, true);
    }

    @Override
    public ProviderResult<MutationReceipt> lookup(RequestId requestId) {
        return backend.lookup(requestId);
    }

    @Override
    public ProviderResult<MutationReceipt> retry(MutationRequest request) {
        return backend.mutate(request, request != null && isCredit(request.kind()));
    }

    private static boolean isCredit(MutationKind kind) {
        return kind == MutationKind.DEPOSIT || kind == MutationKind.REFUND
                || kind == MutationKind.TRANSFER_CREDIT || kind == MutationKind.COMPENSATION;
    }

    private static boolean requiresFunds(MutationKind kind) {
        return kind == MutationKind.WITHDRAW || kind == MutationKind.TRANSFER_DEBIT
                || kind == MutationKind.FEE;
    }
}
