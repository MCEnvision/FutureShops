package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.journal.JournalRecord;

import java.util.Objects;
import java.util.Optional;

public record EscrowCommitResult(Optional<JournalRecord> record, boolean replayed) {
    public EscrowCommitResult {
        record = Objects.requireNonNull(record, "record");
        if (replayed == record.isPresent()) {
            throw new IllegalArgumentException("Escrow commit result is inconsistent");
        }
    }

    public static EscrowCommitResult applied(JournalRecord record) {
        return new EscrowCommitResult(Optional.of(
                Objects.requireNonNull(record, "record")), false);
    }

    public static EscrowCommitResult replay() {
        return new EscrowCommitResult(Optional.empty(), true);
    }
}
