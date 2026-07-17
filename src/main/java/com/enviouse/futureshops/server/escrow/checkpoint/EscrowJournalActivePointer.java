package com.enviouse.futureshops.server.escrow.checkpoint;

import java.util.Objects;
import java.util.UUID;

public record EscrowJournalActivePointer(UUID journalLineageId,
                                         EscrowCheckpointReference checkpointReference) {
    public EscrowJournalActivePointer {
        Objects.requireNonNull(journalLineageId, "journalLineageId");
        Objects.requireNonNull(checkpointReference, "checkpointReference");
        if (!journalLineageId.equals(checkpointReference.replacementJournalLineageId())) {
            throw new IllegalArgumentException("Active journal lineage does not match checkpoint");
        }
    }

    public String journalFileName() {
        return "journal-" + journalLineageId + ".wal";
    }
}
