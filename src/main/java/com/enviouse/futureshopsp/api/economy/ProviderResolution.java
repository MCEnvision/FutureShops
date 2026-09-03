package com.enviouse.futureshopsp.api.economy;

import java.util.Objects;
import java.util.Optional;

/** Immutable result of one frozen provider resolution. */
public record ProviderResolution(
        String requestedProviderId,
        ProviderLifecycle lifecycle,
        Optional<EconomyProvider> provider,
        Optional<CurrencyMetadata> currency,
        Optional<ProviderCapabilities> capabilities,
        String diagnostic) {
    public ProviderResolution {
        Objects.requireNonNull(requestedProviderId, "requestedProviderId");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (diagnostic.length() > 256 || diagnostic.indexOf('\n') >= 0 || diagnostic.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("diagnostic must be a single line of at most 256 characters");
        }
    }

    public boolean ready() {
        return lifecycle == ProviderLifecycle.READY && provider.isPresent();
    }
}
