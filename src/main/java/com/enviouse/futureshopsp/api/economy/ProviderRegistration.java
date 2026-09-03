package com.enviouse.futureshopsp.api.economy;

import java.util.Objects;

/** Immutable registration data retained until the server resolves a provider. */
public record ProviderRegistration(
        String providerId,
        int compatibilityVersion,
        EconomyProviderFactory factory) {
    public ProviderRegistration {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(factory, "factory");
    }
}
