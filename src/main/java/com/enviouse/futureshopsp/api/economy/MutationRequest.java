package com.enviouse.futureshopsp.api.economy;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable value request that providers must bind to their durable operation identity. */
public record MutationRequest(
        RequestId requestId,
        UUID actor,
        Optional<UUID> counterparty,
        long amountMinorUnits,
        MutationKind kind) {

    public MutationRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(counterparty, "counterparty");
        Objects.requireNonNull(kind, "kind");
        if (amountMinorUnits <= 0L) {
            throw new IllegalArgumentException("amountMinorUnits must be positive");
        }
    }

    public static MutationRequest forPlayer(RequestId requestId, UUID actor, long amountMinorUnits, MutationKind kind) {
        return new MutationRequest(requestId, actor, Optional.empty(), amountMinorUnits, kind);
    }
}
