package com.enviouse.futureshops.server.escrow.checkpoint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EscrowCheckpointReference(EscrowCheckpointManifest manifest) {
    public EscrowCheckpointReference {
        Objects.requireNonNull(manifest, "manifest");
    }

    public UUID checkpointId() {
        return manifest.checkpointId();
    }

    public UUID sourceJournalLineageId() {
        return manifest.sourceJournalLineageId();
    }

    public UUID replacementJournalLineageId() {
        return manifest.replacementJournalLineageId();
    }

    public long baseJournalSequence() {
        return manifest.baseJournalSequence();
    }

    public Instant createdAt() {
        return manifest.createdAt();
    }

    public long checkpointFileBytes() {
        return manifest.fileBytes();
    }

    public byte[] checkpointSha256() {
        return manifest.sha256();
    }
}
