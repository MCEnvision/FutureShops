package com.enviouse.futureshops.api.economy;

import java.util.Objects;

/** Immutable validated provider identifier. */
public record ProviderId(String value) {
    public ProviderId {
        Objects.requireNonNull(value, "value");
        if (!EconomyApi.isValidProviderId(value)) {
            throw new IllegalArgumentException("provider identifier is invalid");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
