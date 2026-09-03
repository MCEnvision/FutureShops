package com.enviouse.futureshopsp.server.economy;

import java.util.Objects;

/** Immutable server selection state. */
public record ProviderSelectionSnapshot(
        String activeProviderId,
        String stagedProviderId,
        boolean resolved,
        boolean restartRequired,
        String diagnostic) {
    public ProviderSelectionSnapshot {
        Objects.requireNonNull(activeProviderId, "activeProviderId");
        Objects.requireNonNull(stagedProviderId, "stagedProviderId");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (diagnostic.length() > 256 || diagnostic.indexOf('\n') >= 0 || diagnostic.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("diagnostic must be a single line of at most 256 characters");
        }
    }
}
