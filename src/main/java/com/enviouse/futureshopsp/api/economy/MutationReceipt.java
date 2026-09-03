package com.enviouse.futureshopsp.api.economy;

import java.util.Objects;
import java.util.OptionalLong;

/** Durable provider evidence for one confirmed mutation. */
public record MutationReceipt(
        RequestId requestId,
        MutationKind kind,
        long amountMinorUnits,
        String externalOperationId,
        OptionalLong resultingBalanceMinorUnits) {

    public MutationReceipt {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(externalOperationId, "externalOperationId");
        Objects.requireNonNull(resultingBalanceMinorUnits, "resultingBalanceMinorUnits");
        if (amountMinorUnits <= 0L) {
            throw new IllegalArgumentException("amountMinorUnits must be positive");
        }
        if (externalOperationId.isBlank() || externalOperationId.length() > 256
                || externalOperationId.indexOf('\n') >= 0 || externalOperationId.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("externalOperationId must be a nonempty single line of at most 256 characters");
        }
    }
}
