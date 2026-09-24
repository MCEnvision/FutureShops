package com.enviouse.futureshops.api.economy;

import java.util.Objects;

/** Typed readiness projection for API v1 admission. */
public record Readiness(ProviderLifecycle lifecycle, String diagnostic) {
    public Readiness {
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (diagnostic.length() > 256 || diagnostic.indexOf('\n') >= 0
                || diagnostic.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("diagnostic must be a bounded single line");
        }
    }

    public boolean ready() {
        return lifecycle == ProviderLifecycle.READY;
    }
}
