package com.enviouse.futureshops.server.market.control;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MarketControlTransitionCommand(
        UUID requestId,
        MarketControlModule module,
        long expectedModuleRevision,
        MarketModuleStatus targetStatus,
        MarketControlActor actor,
        String reason,
        long requestedAtMillis,
        long appliedAtMillis,
        Optional<UUID> cancellationBatchId,
        Optional<MarketControlSafetyEvidence> safetyEvidence
) {
    public MarketControlTransitionCommand {
        requestId = Objects.requireNonNull(requestId, "requestId");
        module = Objects.requireNonNull(module, "module");
        targetStatus = Objects.requireNonNull(targetStatus,
                "targetStatus");
        actor = Objects.requireNonNull(actor, "actor");
        reason = MarketControlText.require(reason, "control reason",
                MarketModuleControl.MAX_REASON_BYTES);
        cancellationBatchId = Objects.requireNonNull(
                cancellationBatchId, "cancellationBatchId");
        safetyEvidence = Objects.requireNonNull(
                safetyEvidence, "safetyEvidence");
        if (expectedModuleRevision < 0L || requestedAtMillis < 0L
                || appliedAtMillis < requestedAtMillis) {
            throw new IllegalArgumentException(
                    "Market control transition timing is invalid");
        }
        if (targetStatus.requiresCancellationAndRefund()
                != cancellationBatchId.isPresent()) {
            throw new IllegalArgumentException(
                    "Cancel and refund requires a cancellation batch");
        }
        if (targetStatus != MarketModuleStatus.ENABLED
                && safetyEvidence.isPresent()) {
            throw new IllegalArgumentException(
                    "Resume safety evidence is only valid when enabling");
        }
    }
}
