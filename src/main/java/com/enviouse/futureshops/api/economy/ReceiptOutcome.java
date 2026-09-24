package com.enviouse.futureshops.api.economy;

import java.util.Objects;
import java.util.Optional;

/** Typed durable receipt lookup projection. */
public record ReceiptOutcome(
        ProviderResultStatus status,
        ProviderError error,
        Optional<MutationReceipt> receipt,
        String diagnostic) {
    public ReceiptOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (status == ProviderResultStatus.CONFIRMED && receipt.isEmpty()) {
            throw new IllegalArgumentException("confirmed receipt outcome must contain a receipt");
        }
        if (status != ProviderResultStatus.CONFIRMED && error == ProviderError.NONE) {
            throw new IllegalArgumentException("non confirmed receipt outcome must contain an error");
        }
    }

    public static ReceiptOutcome notFound(String diagnostic) {
        return new ReceiptOutcome(ProviderResultStatus.REJECTED,
                ProviderError.RECEIPT_NOT_FOUND, Optional.empty(), diagnostic);
    }

    public boolean confirmed() {
        return status == ProviderResultStatus.CONFIRMED;
    }
}
