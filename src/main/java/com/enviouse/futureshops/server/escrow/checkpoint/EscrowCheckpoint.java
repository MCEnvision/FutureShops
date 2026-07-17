package com.enviouse.futureshops.server.escrow.checkpoint;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class EscrowCheckpoint {
    public static final int MAX_STORE_BYTES = 67_108_864;
    public static final long MAX_AGGREGATE_STORE_BYTES = 268_435_456L;

    private final UUID checkpointId;
    private final UUID sourceJournalLineageId;
    private final UUID replacementJournalLineageId;
    private final long baseJournalSequence;
    private final Instant createdAt;
    private final Map<EscrowCheckpointStore, byte[]> snapshots;
    private final long aggregateSnapshotBytes;

    public EscrowCheckpoint(UUID checkpointId, UUID sourceJournalLineageId,
                            UUID replacementJournalLineageId, long baseJournalSequence,
                            Instant createdAt, Map<EscrowCheckpointStore, byte[]> snapshots) {
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
        Objects.requireNonNull(snapshots, "snapshots");

        long aggregate = 0L;
        for (Map.Entry<EscrowCheckpointStore, byte[]> entry : snapshots.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "snapshot store");
            byte[] bytes = Objects.requireNonNull(entry.getValue(), "snapshot bytes");
            if (bytes.length > MAX_STORE_BYTES) {
                throw new IllegalArgumentException("Escrow checkpoint store exceeds byte limit");
            }
            aggregate = Math.addExact(aggregate, bytes.length);
            if (aggregate > MAX_AGGREGATE_STORE_BYTES) {
                throw new IllegalArgumentException("Escrow checkpoint exceeds aggregate byte limit");
            }
        }
        if (snapshots.size() != EscrowCheckpointStore.values().length) {
            throw new IllegalArgumentException("Escrow checkpoint must contain every store");
        }
        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
            if (!snapshots.containsKey(store)) {
                throw new IllegalArgumentException("Escrow checkpoint store is missing");
            }
        }
        EnumMap<EscrowCheckpointStore, byte[]> copied = new EnumMap<>(EscrowCheckpointStore.class);
        snapshots.forEach((store, bytes) -> copied.put(store, bytes.clone()));
        this.snapshots = Collections.unmodifiableMap(copied);
        this.aggregateSnapshotBytes = aggregate;
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

    public byte[] snapshot(EscrowCheckpointStore store) {
        return snapshots.get(Objects.requireNonNull(store, "store")).clone();
    }

    public Map<EscrowCheckpointStore, byte[]> snapshots() {
        EnumMap<EscrowCheckpointStore, byte[]> copied = new EnumMap<>(EscrowCheckpointStore.class);
        snapshots.forEach((store, bytes) -> copied.put(store, bytes.clone()));
        return Collections.unmodifiableMap(copied);
    }

    public long aggregateSnapshotBytes() {
        return aggregateSnapshotBytes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EscrowCheckpoint checkpoint)
                || !checkpointId.equals(checkpoint.checkpointId)
                || !sourceJournalLineageId.equals(checkpoint.sourceJournalLineageId)
                || !replacementJournalLineageId.equals(checkpoint.replacementJournalLineageId)
                || baseJournalSequence != checkpoint.baseJournalSequence
                || !createdAt.equals(checkpoint.createdAt)) {
            return false;
        }
        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
            if (!Arrays.equals(snapshots.get(store), checkpoint.snapshots.get(store))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(checkpointId, sourceJournalLineageId,
                replacementJournalLineageId, baseJournalSequence, createdAt);
        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
            result = 31 * result + Arrays.hashCode(snapshots.get(store));
        }
        return result;
    }
}
