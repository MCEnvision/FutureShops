package com.enviouse.futureshops.server.escrow.runtime;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

final class EscrowEvidenceDiscoveryQueue {
    private EscrowEvidenceDiscoveryQueue() {
    }

    static <T> StepResult processOne(
            ArrayDeque<T> pending,
            Consumer<T> processor
    ) {
        Objects.requireNonNull(pending, "pending");
        Objects.requireNonNull(processor, "processor");
        T next = pending.pollFirst();
        if (next == null) {
            return new StepResult(0, true, Optional.empty());
        }
        try {
            processor.accept(next);
            return new StepResult(1, pending.isEmpty(), Optional.empty());
        } catch (RuntimeException exception) {
            return new StepResult(1, pending.isEmpty(),
                    Optional.of(exception));
        }
    }

    record StepResult(
            int examined,
            boolean complete,
            Optional<RuntimeException> failure
    ) {
        StepResult {
            if (examined < 0 || examined > 1) {
                throw new IllegalArgumentException(
                        "Discovery work count is invalid");
            }
            failure = Objects.requireNonNull(failure, "failure");
            if (examined == 0 && (!complete || failure.isPresent())) {
                throw new IllegalArgumentException(
                        "Empty discovery work result is invalid");
            }
        }
    }
}
