package com.enviouse.futureshops.server.escrow.checkpoint;

import com.enviouse.futureshops.server.escrow.runtime.JournalLineage;

import java.nio.file.Path;
import java.util.Objects;

public record EscrowJournalGeneration(Path journalPath, JournalLineage lineage,
                                      EscrowCheckpointReference checkpointReference) {
    public EscrowJournalGeneration {
        journalPath = Objects.requireNonNull(journalPath, "journalPath")
                .toAbsolutePath().normalize();
        Objects.requireNonNull(lineage, "lineage");
        Objects.requireNonNull(checkpointReference, "checkpointReference");
        if (!lineage.lineageId().equals(checkpointReference.replacementJournalLineageId())) {
            throw new IllegalArgumentException("Journal generation lineage does not match checkpoint");
        }
    }
}
