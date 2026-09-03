package com.enviouse.futureshopsp.server.economy;

import com.enviouse.futureshopsp.api.economy.ProviderLifecycle;

/** Immutable server owned economy lifecycle snapshot. */
public record EconomyLifecycleSnapshot(
        String providerId,
        ProviderLifecycle lifecycle,
        String diagnostic,
        boolean acceptsQueries,
        boolean acceptsMutations) {
    public EconomyLifecycleSnapshot {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (lifecycle == null) {
            throw new NullPointerException("lifecycle");
        }
        diagnostic = diagnostic == null ? "" : diagnostic;
        if (diagnostic.length() > 256 || diagnostic.indexOf('\n') >= 0 || diagnostic.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("diagnostic must be a single line of at most 256 characters");
        }
    }

    public static EconomyLifecycleSnapshot of(String providerId, ProviderLifecycle lifecycle, String diagnostic) {
        boolean ready = lifecycle == ProviderLifecycle.READY;
        return new EconomyLifecycleSnapshot(providerId, lifecycle, diagnostic, ready, ready);
    }
}
