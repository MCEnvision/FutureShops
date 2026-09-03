package com.enviouse.futureshopsp.api.economy;

import java.util.Objects;

/** Immutable result of a provider registration attempt. */
public record RegistrationResult(RegistrationStatus status, String providerId, String diagnostic) {
    public RegistrationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (diagnostic.length() > 256 || diagnostic.indexOf('\n') >= 0 || diagnostic.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("diagnostic must be a single line of at most 256 characters");
        }
    }

    public boolean accepted() {
        return status == RegistrationStatus.ACCEPTED;
    }
}
