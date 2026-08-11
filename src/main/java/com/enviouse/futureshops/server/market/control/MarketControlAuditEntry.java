package com.enviouse.futureshops.server.market.control;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MarketControlAuditEntry(
        UUID requestId,
        String requestFingerprint,
        MarketControlModule module,
        MarketModuleStatus previousStatus,
        MarketModuleStatus nextStatus,
        long moduleRevision,
        long globalRevision,
        MarketControlActor actor,
        String reason,
        long requestedAtMillis,
        long appliedAtMillis,
        Optional<UUID> cancellationBatchId,
        Optional<MarketControlSafetyEvidence> safetyEvidence,
        Optional<MarketPauseTimingEvidence> pauseTimingEvidence
) {
    public MarketControlAuditEntry {
        requestId = Objects.requireNonNull(requestId, "requestId");
        requestFingerprint = MarketControlText.requireFingerprint(
                requestFingerprint, "request fingerprint");
        module = Objects.requireNonNull(module, "module");
        previousStatus = Objects.requireNonNull(previousStatus,
                "previousStatus");
        nextStatus = Objects.requireNonNull(nextStatus, "nextStatus");
        actor = Objects.requireNonNull(actor, "actor");
        reason = MarketControlText.require(reason, "control reason",
                MarketModuleControl.MAX_REASON_BYTES);
        cancellationBatchId = Objects.requireNonNull(
                cancellationBatchId, "cancellationBatchId");
        safetyEvidence = Objects.requireNonNull(
                safetyEvidence, "safetyEvidence");
        pauseTimingEvidence = Objects.requireNonNull(
                pauseTimingEvidence, "pauseTimingEvidence");
        if (previousStatus == nextStatus || moduleRevision <= 0L
                || globalRevision <= 0L || requestedAtMillis < 0L
                || appliedAtMillis < requestedAtMillis) {
            throw new IllegalArgumentException(
                    "Market control audit entry is invalid");
        }
        if (nextStatus.requiresCancellationAndRefund()
                != cancellationBatchId.isPresent()) {
            throw new IllegalArgumentException(
                    "Market control audit cancellation batch is invalid");
        }
        boolean openingPause = !previousStatus.timersPaused()
                && nextStatus.timersPaused();
        boolean closingPause = previousStatus.timersPaused()
                && !nextStatus.timersPaused();
        if ((openingPause || closingPause)
                != pauseTimingEvidence.isPresent()) {
            throw new IllegalArgumentException(
                    "Market control audit pause evidence is invalid");
        }
        if (openingPause
                && !pauseTimingEvidence.orElseThrow().open()) {
            throw new IllegalArgumentException(
                    "Market control pause opening evidence is invalid");
        }
        if (closingPause
                && pauseTimingEvidence.orElseThrow().open()) {
            throw new IllegalArgumentException(
                    "Market control pause closing evidence is invalid");
        }
        if (previousStatus == MarketModuleStatus.CANCEL_AND_REFUND
                && nextStatus != MarketModuleStatus.ENABLED) {
            throw new IllegalArgumentException(
                    "Cancel and refund can only resume as enabled");
        }
        if (previousStatus == MarketModuleStatus.CANCEL_AND_REFUND) {
            if (safetyEvidence.isEmpty()
                    || !safetyEvidence.orElseThrow().provesSafeResume()) {
                throw new IllegalArgumentException(
                        "Market control resume evidence is invalid");
            }
        } else if (safetyEvidence.isPresent()) {
            throw new IllegalArgumentException(
                    "Market control safety evidence is unexpected");
        }
    }
}
