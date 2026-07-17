package com.enviouse.futureshops.server.escrow.item.runtime;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ItemInventoryJournalCompaction {
    public static final int MAX_TOMBSTONES_PER_COMPACTION = 256;

    private final UUID commandId;
    private final UUID checkpointId;
    private final UUID sourceJournalLineageId;
    private final UUID replacementJournalLineageId;
    private final long baseJournalSequence;
    private final byte[] checkpointDigest;
    private final List<ItemInventoryTerminalTombstone> tombstones;

    public ItemInventoryJournalCompaction(
            UUID commandId,
            UUID checkpointId,
            UUID sourceJournalLineageId,
            UUID replacementJournalLineageId,
            long baseJournalSequence,
            byte[] checkpointDigest,
            List<ItemInventoryTerminalTombstone> tombstones
    ) {
        this.commandId = requireUuid(commandId, "commandId");
        this.checkpointId = requireUuid(checkpointId, "checkpointId");
        this.sourceJournalLineageId = requireUuid(
                sourceJournalLineageId, "sourceJournalLineageId");
        this.replacementJournalLineageId = requireUuid(
                replacementJournalLineageId,
                "replacementJournalLineageId");
        if (this.sourceJournalLineageId.equals(
                this.replacementJournalLineageId)
                || baseJournalSequence < 1L) {
            throw new IllegalArgumentException(
                    "Item inventory compaction checkpoint is invalid");
        }
        this.baseJournalSequence = baseJournalSequence;
        this.checkpointDigest = Objects.requireNonNull(
                checkpointDigest, "checkpointDigest").clone();
        this.tombstones = List.copyOf(Objects.requireNonNull(
                tombstones, "tombstones"));
        if (this.checkpointDigest.length != 32
                || this.tombstones.isEmpty()
                || this.tombstones.size()
                > MAX_TOMBSTONES_PER_COMPACTION) {
            throw new IllegalArgumentException(
                    "Item inventory journal compaction is invalid");
        }
        Set<UUID> requests = new HashSet<>();
        for (ItemInventoryTerminalTombstone tombstone : this.tombstones) {
            ItemInventoryTerminalTombstone value = Objects.requireNonNull(
                    tombstone, "tombstone");
            if (!value.compactionCommandId().equals(this.commandId)
                    || !value.checkpointId().equals(this.checkpointId)
                    || !requests.add(value.requestId())) {
                throw new IllegalArgumentException(
                        "Item inventory compaction tombstone conflicts");
            }
        }
    }

    public UUID commandId() {
        return commandId;
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

    public byte[] checkpointDigest() {
        return checkpointDigest.clone();
    }

    public List<ItemInventoryTerminalTombstone> tombstones() {
        return tombstones;
    }

    public boolean matchesCheckpoint(
            UUID checkpointId,
            UUID sourceJournalLineageId,
            UUID replacementJournalLineageId,
            long baseJournalSequence,
            byte[] checkpointDigest
    ) {
        return this.checkpointId.equals(checkpointId)
                && this.sourceJournalLineageId.equals(sourceJournalLineageId)
                && this.replacementJournalLineageId.equals(
                replacementJournalLineageId)
                && this.baseJournalSequence == baseJournalSequence
                && MessageDigest.isEqual(this.checkpointDigest,
                Objects.requireNonNull(checkpointDigest,
                        "checkpointDigest"));
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ItemInventoryJournalCompaction other
                && commandId.equals(other.commandId)
                && checkpointId.equals(other.checkpointId)
                && sourceJournalLineageId.equals(
                other.sourceJournalLineageId)
                && replacementJournalLineageId.equals(
                other.replacementJournalLineageId)
                && baseJournalSequence == other.baseJournalSequence
                && Arrays.equals(checkpointDigest,
                other.checkpointDigest)
                && tombstones.equals(other.tombstones);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(commandId, checkpointId,
                sourceJournalLineageId, replacementJournalLineageId,
                baseJournalSequence, tombstones);
        return 31 * result + Arrays.hashCode(checkpointDigest);
    }

    private static UUID requireUuid(UUID value, String name) {
        UUID result = Objects.requireNonNull(value, name);
        if (result.getMostSignificantBits() == 0L
                && result.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException(name + " cannot be zero");
        }
        return result;
    }
}
