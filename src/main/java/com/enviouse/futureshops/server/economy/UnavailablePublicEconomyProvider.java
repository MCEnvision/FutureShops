package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.api.economy.BalanceSnapshot;
import com.enviouse.futureshops.api.economy.CurrencyMetadata;
import com.enviouse.futureshops.api.economy.EconomyApi;
import com.enviouse.futureshops.api.economy.MutationReceipt;
import com.enviouse.futureshops.api.economy.MutationRequest;
import com.enviouse.futureshops.api.economy.ProviderCapabilities;
import com.enviouse.futureshops.api.economy.ProviderError;
import com.enviouse.futureshops.api.economy.ProviderLifecycle;
import com.enviouse.futureshops.api.economy.ProviderReadiness;
import com.enviouse.futureshops.api.economy.ProviderResult;
import com.enviouse.futureshops.api.economy.RequestId;

import java.util.UUID;

/** Public API projection that refuses value operations without fallback. */
final class UnavailablePublicEconomyProvider implements com.enviouse.futureshops.api.economy.EconomyProvider {
    private final String providerId;
    private final ProviderLifecycle lifecycle;
    private final String diagnostic;

    UnavailablePublicEconomyProvider(String providerId, ProviderLifecycle lifecycle, String diagnostic) {
        this.providerId = providerId;
        this.lifecycle = lifecycle;
        this.diagnostic = diagnostic;
    }

    @Override
    public String providerId() {
        return providerId;
    }

    @Override
    public int compatibilityVersion() {
        return EconomyApi.COMPATIBILITY_VERSION;
    }

    @Override
    public CurrencyMetadata currency() {
        return new CurrencyMetadata("Unavailable", "Unavailable", 0);
    }

    @Override
    public ProviderCapabilities capabilities() {
        return ProviderCapabilities.none();
    }

    @Override
    public ProviderReadiness readiness() {
        return new ProviderReadiness(lifecycle, diagnostic);
    }

    @Override
    public ProviderResult<BalanceSnapshot> balance(UUID playerId) {
        return ProviderResult.unavailable(ProviderError.NOT_READY, diagnostic);
    }

    @Override
    public ProviderResult<BalanceSnapshot> precheck(MutationRequest request) {
        return ProviderResult.unavailable(ProviderError.NOT_READY, diagnostic);
    }

    @Override
    public ProviderResult<MutationReceipt> withdraw(MutationRequest request) {
        return ProviderResult.unavailable(ProviderError.NOT_READY, diagnostic);
    }

    @Override
    public ProviderResult<MutationReceipt> deposit(MutationRequest request) {
        return ProviderResult.unavailable(ProviderError.NOT_READY, diagnostic);
    }

    @Override
    public ProviderResult<MutationReceipt> lookup(RequestId requestId) {
        return ProviderResult.unavailable(ProviderError.NOT_READY, diagnostic);
    }

    @Override
    public ProviderResult<MutationReceipt> retry(MutationRequest request) {
        return ProviderResult.unavailable(ProviderError.NOT_READY, diagnostic);
    }
}
