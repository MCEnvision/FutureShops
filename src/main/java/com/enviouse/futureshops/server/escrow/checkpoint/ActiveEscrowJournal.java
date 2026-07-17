package com.enviouse.futureshops.server.escrow.checkpoint;

import com.enviouse.futureshops.server.escrow.runtime.JournalLineage;

import java.nio.file.Path;
import java.util.Objects;

public record ActiveEscrowJournal(Path journalPath, JournalLineage lineage,
                                  EscrowCheckpointReference checkpointReference,
                                  TrustedEscrowCheckpoint trustedCheckpoint,
                                  long recordCount, long lastSequence) {
    public ActiveEscrowJournal {
        journalPath = Objects.requireNonNull(journalPath, "journalPath")
                .toAbsolutePath().normalize();
        Objects.requireNonNull(lineage, "lineage");
        Objects.requireNonNull(checkpointReference, "checkpointReference");
        Objects.requireNonNull(trustedCheckpoint, "trustedCheckpoint");
        if (!lineage.lineageId().equals(checkpointReference.replacementJournalLineageId())
                || !checkpointReference.equals(trustedCheckpoint.reference())) {
            throw new IllegalArgumentException("Active journal checkpoint identity does not match");
        }
        if (recordCount < 2L || lastSequence < 2L) {
            throw new IllegalArgumentException("Active journal bootstrap is incomplete");
        }
    }
}
