package com.enviouse.futureshops.api.economy;

import java.util.Objects;
import java.util.Optional;

/** Typed mutation projection used before durable coordinator integration. */
public record MutationOutcome(
        ProviderResultStatus status,
        ProviderError error,
        Optional<MutationReceipt> receipt,
        String diagnostic) {
    public MutationOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (status == ProviderResultStatus.CONFIRMED && receipt.isEmpty()) {
            throw new IllegalArgumentException("confirmed mutation must contain a receipt");
        }
        if (status != ProviderResultStatus.CONFIRMED && error == ProviderError.NONE) {
            throw new IllegalArgumentException("non confirmed mutation must contain an error");
        }
    }

    public static MutationOutcome refused(ProviderError error, String diagnostic) {
        return new MutationOutcome(ProviderResultStatus.REJECTED, error,
                Optional.empty(), diagnostic);
    }

    public boolean confirmed() {
        return status == ProviderResultStatus.CONFIRMED;
    }
}
