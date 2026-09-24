package com.enviouse.futureshops.api.economy;

import java.util.Objects;
import java.util.Optional;

/** Typed query projection that never represents an unavailable balance as zero. */
public record QueryResult<T>(
        ProviderResultStatus status,
        ProviderError error,
        Optional<T> value,
        String diagnostic) {
    public QueryResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (diagnostic.length() > 256 || diagnostic.indexOf('\n') >= 0
                || diagnostic.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("diagnostic must be a bounded single line");
        }
        if (status == ProviderResultStatus.CONFIRMED && value.isEmpty()) {
            throw new IllegalArgumentException("confirmed query must contain a value");
        }
        if (status != ProviderResultStatus.CONFIRMED && error == ProviderError.NONE) {
            throw new IllegalArgumentException("non confirmed query must contain an error");
        }
    }

    public static <T> QueryResult<T> confirmed(T value) {
        return new QueryResult<>(ProviderResultStatus.CONFIRMED, ProviderError.NONE,
                Optional.of(Objects.requireNonNull(value, "value")), "");
    }

    public static <T> QueryResult<T> unavailable(ProviderError error, String diagnostic) {
        return new QueryResult<>(ProviderResultStatus.UNAVAILABLE, error,
                Optional.empty(), diagnostic);
    }

    public boolean confirmed() {
        return status == ProviderResultStatus.CONFIRMED;
    }
}
