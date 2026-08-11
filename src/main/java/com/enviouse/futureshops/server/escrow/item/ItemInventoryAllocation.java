package com.enviouse.futureshops.server.escrow.item;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public record ItemInventoryAllocation(
        UUID entryId,
        ItemInventorySlot slot,
        int count,
        byte[] actualStackSnapshot
) {
    public ItemInventoryAllocation {
        entryId = ItemInventoryBatchEntry.requireUuid(entryId, "entryId");
        Objects.requireNonNull(slot, "slot");
        actualStackSnapshot = Objects.requireNonNull(
                actualStackSnapshot, "actualStackSnapshot").clone();
        ItemStackSnapshotEvidence.SnapshotMetadata metadata =
                ItemStackSnapshotEvidence.inspect(actualStackSnapshot);
        if (count <= 0 || metadata.count() != count) {
            throw new IllegalArgumentException(
                    "Item inventory allocation count is invalid");
        }
    }

    @Override
    public byte[] actualStackSnapshot() {
        return actualStackSnapshot.clone();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ItemInventoryAllocation other
                && entryId.equals(other.entryId)
                && slot.equals(other.slot)
                && count == other.count
                && Arrays.equals(actualStackSnapshot,
                other.actualStackSnapshot);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(entryId, slot, count);
        return 31 * result + Arrays.hashCode(actualStackSnapshot);
    }
}
