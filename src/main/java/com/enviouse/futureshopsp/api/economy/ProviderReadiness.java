package com.enviouse.futureshopsp.api.economy;

import java.util.Objects;

/** Immutable lifecycle snapshot returned by a provider. */
public record ProviderReadiness(ProviderLifecycle lifecycle, String diagnostic) {
    public ProviderReadiness {
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (diagnostic.length() > 256 || diagnostic.indexOf('\n') >= 0 || diagnostic.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("diagnostic must be a single line of at most 256 characters");
        }
    }

    public boolean ready() {
        return lifecycle == ProviderLifecycle.READY;
    }
}
