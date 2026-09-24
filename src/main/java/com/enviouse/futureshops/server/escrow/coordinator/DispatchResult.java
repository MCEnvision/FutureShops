package com.enviouse.futureshops.server.escrow.coordinator;

import java.util.Objects;
import java.util.Optional;

/** Explicit provider result. Unknown never implies a retry. */
public record DispatchResult(Status status, Optional<DispatchReceipt> receipt) {
    public enum Status { CONFIRMED, REJECTED, UNKNOWN }

    public DispatchResult {
        status = Objects.requireNonNull(status, "status");
        receipt = Objects.requireNonNull(receipt, "receipt");
        if (status == Status.CONFIRMED && receipt.isEmpty()) {
            throw new IllegalArgumentException("confirmed dispatch requires a receipt");
        }
        if (status != Status.CONFIRMED && receipt.isPresent()) {
            throw new IllegalArgumentException("only confirmed dispatch may carry a receipt");
        }
    }

    public static DispatchResult confirmed(DispatchReceipt receipt) {
        return new DispatchResult(Status.CONFIRMED, Optional.of(receipt));
    }

    public static DispatchResult rejected() {
        return new DispatchResult(Status.REJECTED, Optional.empty());
    }

    public static DispatchResult unknown() {
        return new DispatchResult(Status.UNKNOWN, Optional.empty());
    }
}
