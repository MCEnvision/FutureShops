package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.model.EscrowTransaction;

import java.util.Objects;
import java.time.Instant;
import java.util.Optional;

public record EscrowRecoveryWork(EscrowTransaction transaction,
                                 boolean handlerRegistered,
                                 EscrowRecoveryWorkStatus status,
                                 Optional<Instant> nextAttemptAt,
                                 boolean blocksRuntime,
                                 String detail) {
    public EscrowRecoveryWork {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(status, "status");
        nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        detail = Objects.requireNonNull(detail, "detail").trim();
        if (transaction.state().isTerminal() || detail.isEmpty() || detail.length() > 1024) {
            throw new IllegalArgumentException("Invalid escrow recovery work");
        }
        if ((status == EscrowRecoveryWorkStatus.SCHEDULED) != nextAttemptAt.isPresent()
                || blocksRuntime != (status == EscrowRecoveryWorkStatus.BLOCKED)) {
            throw new IllegalArgumentException("Invalid escrow recovery work status");
        }
    }
}
