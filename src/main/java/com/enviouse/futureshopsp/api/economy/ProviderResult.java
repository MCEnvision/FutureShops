package com.enviouse.futureshopsp.api.economy;

import java.util.Objects;
import java.util.Optional;

/** Typed provider result that preserves unavailable and ambiguous outcomes. */
public record ProviderResult<T>(
        ProviderResultStatus status,
        ProviderError error,
        Optional<T> value,
        Optional<MutationReceipt> receipt,
        String diagnostic) {

    public ProviderResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (diagnostic.length() > 256 || diagnostic.indexOf('\n') >= 0 || diagnostic.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("diagnostic must be a single line of at most 256 characters");
        }
        if (status == ProviderResultStatus.CONFIRMED && value.isEmpty()) {
            throw new IllegalArgumentException("confirmed result must contain a value");
        }
        if (status == ProviderResultStatus.CONFIRMED && error != ProviderError.NONE) {
            throw new IllegalArgumentException("confirmed result must use NONE error");
        }
        if (status != ProviderResultStatus.CONFIRMED && error == ProviderError.NONE) {
            throw new IllegalArgumentException("non-confirmed result must contain an error");
        }
    }

    public static <T> ProviderResult<T> confirmed(T value) {
        return new ProviderResult<>(ProviderResultStatus.CONFIRMED, ProviderError.NONE,
                Optional.of(Objects.requireNonNull(value, "value")), Optional.empty(), "");
    }

    public static ProviderResult<MutationReceipt> confirmed(MutationReceipt receipt) {
        return new ProviderResult<>(ProviderResultStatus.CONFIRMED, ProviderError.NONE,
                Optional.of(Objects.requireNonNull(receipt, "receipt")), Optional.of(receipt), "");
    }

    public static <T> ProviderResult<T> rejected(ProviderError error, String diagnostic) {
        return nonConfirmed(ProviderResultStatus.REJECTED, error, diagnostic);
    }

    public static <T> ProviderResult<T> unavailable(ProviderError error, String diagnostic) {
        return nonConfirmed(ProviderResultStatus.UNAVAILABLE, error, diagnostic);
    }

    public static <T> ProviderResult<T> ambiguous(String diagnostic) {
        return nonConfirmed(ProviderResultStatus.AMBIGUOUS, ProviderError.UNKNOWN, diagnostic);
    }

    public static <T> ProviderResult<T> recoveryRequired(String diagnostic) {
        return nonConfirmed(ProviderResultStatus.RECOVERY_REQUIRED, ProviderError.UNKNOWN, diagnostic);
    }

    private static <T> ProviderResult<T> nonConfirmed(
            ProviderResultStatus status, ProviderError error, String diagnostic) {
        return new ProviderResult<>(status, error, Optional.empty(), Optional.empty(), diagnostic);
    }

    public boolean confirmed() {
        return status == ProviderResultStatus.CONFIRMED;
    }

    public boolean safeToRetry() {
        return status == ProviderResultStatus.UNAVAILABLE || status == ProviderResultStatus.REJECTED;
    }
}
