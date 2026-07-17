package com.enviouse.futureshops.server.market.control;

import java.util.Objects;
import java.util.UUID;

public record MarketControlSafetyEvidence(
        UUID evidenceId,
        UUID cancellationBatchId,
        long observedAtMillis,
        long activeValueOperations,
        long uncommittedRefundActions,
        boolean reconciliationComplete
) {
    public MarketControlSafetyEvidence {
        evidenceId = Objects.requireNonNull(evidenceId, "evidenceId");
        cancellationBatchId = Objects.requireNonNull(
                cancellationBatchId, "cancellationBatchId");
        if (observedAtMillis < 0L || activeValueOperations < 0L
                || uncommittedRefundActions < 0L) {
            throw new IllegalArgumentException(
                    "Market control safety evidence is invalid");
        }
    }

    public boolean provesSafeResume() {
        return reconciliationComplete
                && activeValueOperations == 0L
                && uncommittedRefundActions == 0L;
    }
}
