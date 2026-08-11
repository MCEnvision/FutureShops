package com.enviouse.futureshops.server.market.control;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public record MarketModuleControl(
        MarketControlModule module,
        MarketModuleStatus status,
        long revision,
        MarketControlActor actor,
        String reason,
        long changedAtMillis,
        OptionalLong pauseStartedAtMillis,
        long accumulatedPausedMillis,
        Optional<MarketPauseTimingEvidence> lastPauseTimingEvidence,
        Optional<UUID> cancellationBatchId
) {
    public static final int MAX_REASON_BYTES = 512;

    public MarketModuleControl {
        module = Objects.requireNonNull(module, "module");
        status = Objects.requireNonNull(status, "status");
        actor = Objects.requireNonNull(actor, "actor");
        reason = MarketControlText.require(reason, "control reason",
                MAX_REASON_BYTES);
        pauseStartedAtMillis = Objects.requireNonNull(
                pauseStartedAtMillis, "pauseStartedAtMillis");
        lastPauseTimingEvidence = Objects.requireNonNull(
                lastPauseTimingEvidence, "lastPauseTimingEvidence");
        cancellationBatchId = Objects.requireNonNull(
                cancellationBatchId, "cancellationBatchId");
        if (revision < 0L || changedAtMillis < 0L
                || accumulatedPausedMillis < 0L) {
            throw new IllegalArgumentException(
                    "Market module control counters are invalid");
        }
        if (status.timersPaused() != pauseStartedAtMillis.isPresent()) {
            throw new IllegalArgumentException(
                    "Market module pause state is inconsistent");
        }
        if (status.requiresCancellationAndRefund()
                != cancellationBatchId.isPresent()) {
            throw new IllegalArgumentException(
                    "Market module cancellation state is inconsistent");
        }
        if (pauseStartedAtMillis.isPresent()) {
            long startedAt = pauseStartedAtMillis.getAsLong();
            if (startedAt != changedAtMillis
                    || lastPauseTimingEvidence.isEmpty()
                    || !lastPauseTimingEvidence.orElseThrow().open()
                    || lastPauseTimingEvidence.orElseThrow()
                    .pausedAtMillis() != startedAt) {
                throw new IllegalArgumentException(
                        "Market module open pause evidence is invalid");
            }
        }
        if (lastPauseTimingEvidence.isPresent()
                && lastPauseTimingEvidence.orElseThrow()
                .accumulatedPausedMillisAfter()
                != accumulatedPausedMillis) {
            throw new IllegalArgumentException(
                    "Market module accumulated pause time is invalid");
        }
    }

    public static MarketModuleControl initial(
            MarketControlModule module,
            long initializedAtMillis
    ) {
        return new MarketModuleControl(module,
                MarketModuleStatus.ENABLED, 0L,
                MarketControlActor.system(), "Initial state",
                initializedAtMillis, OptionalLong.empty(), 0L,
                Optional.empty(), Optional.empty());
    }

    public boolean acceptsNewValueOperations() {
        return status.acceptsNewValueOperations();
    }

    public boolean allowsExistingValueOperations() {
        return status.allowsExistingValueOperations();
    }

    public long accumulatedPausedMillisThrough(long nowMillis) {
        if (nowMillis < changedAtMillis) {
            throw new IllegalArgumentException(
                    "Market control observation time is stale");
        }
        if (!status.timersPaused()) {
            return accumulatedPausedMillis;
        }
        return Math.addExact(accumulatedPausedMillis,
                Math.subtractExact(nowMillis,
                        pauseStartedAtMillis.orElseThrow()));
    }
}
