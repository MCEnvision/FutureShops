package com.enviouse.futureshops.server.escrow.checkpoint;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class EscrowCheckpointManifest {
    public static final int SHA256_BYTES = 32;

    private final UUID checkpointId;
    private final UUID sourceJournalLineageId;
    private final UUID replacementJournalLineageId;
    private final long baseJournalSequence;
    private final Instant createdAt;
    private final long fileBytes;
    private final byte[] sha256;

    public EscrowCheckpointManifest(UUID checkpointId, UUID sourceJournalLineageId,
                                    UUID replacementJournalLineageId, long baseJournalSequence,
                                    Instant createdAt, long fileBytes, byte[] sha256) {
        this.checkpointId = Objects.requireNonNull(checkpointId, "checkpointId");
        this.sourceJournalLineageId = Objects.requireNonNull(
                sourceJournalLineageId, "sourceJournalLineageId");
        this.replacementJournalLineageId = Objects.requireNonNull(
                replacementJournalLineageId, "replacementJournalLineageId");
        if (sourceJournalLineageId.equals(replacementJournalLineageId)) {
            throw new IllegalArgumentException("Checkpoint replacement lineage must be new");
        }
        if (baseJournalSequence < 1L) {
            throw new IllegalArgumentException("Checkpoint base sequence must be positive");
        }
        this.baseJournalSequence = baseJournalSequence;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (fileBytes <= 0L || fileBytes > EscrowCheckpointCodec.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Checkpoint file size is invalid");
        }
        this.fileBytes = fileBytes;
        Objects.requireNonNull(sha256, "sha256");
        if (sha256.length != SHA256_BYTES) {
            throw new IllegalArgumentException("Checkpoint SHA256 length is invalid");
        }
        this.sha256 = sha256.clone();
    }

    public UUID checkpointId() {
        return checkpointId;
    }

    public UUID sourceJournalLineageId() {
        return sourceJournalLineageId;
    }

    public UUID replacementJournalLineageId() {
        return replacementJournalLineageId;
    }

    public long baseJournalSequence() {
        return baseJournalSequence;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public long fileBytes() {
        return fileBytes;
    }

    public byte[] sha256() {
        return sha256.clone();
    }

    public boolean describes(EscrowCheckpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        return checkpointId.equals(checkpoint.checkpointId())
                && sourceJournalLineageId.equals(checkpoint.sourceJournalLineageId())
                && replacementJournalLineageId.equals(checkpoint.replacementJournalLineageId())
                && baseJournalSequence == checkpoint.baseJournalSequence()
                && createdAt.equals(checkpoint.createdAt());
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof EscrowCheckpointManifest manifest
                && checkpointId.equals(manifest.checkpointId)
                && sourceJournalLineageId.equals(manifest.sourceJournalLineageId)
                && replacementJournalLineageId.equals(manifest.replacementJournalLineageId)
                && baseJournalSequence == manifest.baseJournalSequence
                && createdAt.equals(manifest.createdAt)
                && fileBytes == manifest.fileBytes
                && Arrays.equals(sha256, manifest.sha256);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(checkpointId, sourceJournalLineageId,
                replacementJournalLineageId, baseJournalSequence, createdAt, fileBytes);
        return 31 * result + Arrays.hashCode(sha256);
    }
}
