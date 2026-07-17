package com.enviouse.futureshops.server.escrow.audit;

import java.util.Objects;
import java.util.UUID;

public record ProtectedReservationKey(UUID mintBatchId, UUID transactionId) {
    public ProtectedReservationKey {
        Objects.requireNonNull(mintBatchId, "mintBatchId");
        Objects.requireNonNull(transactionId, "transactionId");
    }
}
