package com.enviouse.futureshops.server.escrow.item;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ItemInventoryMutationPlan {
    private final ItemInventoryPlanStatus status;
    private final ItemInventoryMutationDirection direction;
    private final ItemInventoryState before;
    private final ItemInventoryState after;
    private final List<ItemInventoryBatchEntry> entries;
    private final List<ItemInventoryAllocation> allocations;
    private final List<ItemInventorySlotChange> changes;
    private final Map<UUID, Integer> fulfilledCounts;
    private final byte[] batchFingerprint;

    ItemInventoryMutationPlan(
            ItemInventoryPlanStatus status,
            ItemInventoryMutationDirection direction,
            ItemInventoryState before,
            ItemInventoryState after,
            List<ItemInventoryBatchEntry> entries,
            List<ItemInventoryAllocation> allocations,
            List<ItemInventorySlotChange> changes,
            Map<UUID, Integer> fulfilledCounts,
            byte[] batchFingerprint
    ) {
        this.status = Objects.requireNonNull(status, "status");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.before = Objects.requireNonNull(before, "before");
        this.after = Objects.requireNonNull(after, "after");
        this.entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        this.allocations = List.copyOf(Objects.requireNonNull(
                allocations, "allocations"));
        this.changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        this.fulfilledCounts = Map.copyOf(new LinkedHashMap<>(
                Objects.requireNonNull(fulfilledCounts, "fulfilledCounts")));
        this.batchFingerprint = Objects.requireNonNull(
                batchFingerprint, "batchFingerprint").clone();
        ItemInventoryHashes.requireHash(this.batchFingerprint,
                "Item batch fingerprint");
        if (this.entries.isEmpty() || this.entries.size() > 64
                || this.fulfilledCounts.size() != this.entries.size()) {
            throw new IllegalArgumentException(
                    "Item inventory plan entries are invalid");
        }
        if (status == ItemInventoryPlanStatus.APPLICABLE) {
            if (this.allocations.isEmpty() || this.changes.isEmpty()
                    || before.equals(after)
                    || this.allocations.size()
                    > ItemInventoryMutationReceipt.MAX_ALLOCATIONS) {
                throw new IllegalArgumentException(
                        "Applicable item inventory plan has no mutation");
            }
            ItemInventoryMutationReceiptCodec.requirePlanFits(
                    this.allocations, this.changes.size());
            for (ItemInventoryBatchEntry entry : this.entries) {
                if (this.fulfilledCounts.getOrDefault(entry.entryId(), -1)
                        != entry.count()) {
                    throw new IllegalArgumentException(
                            "Applicable item inventory plan is incomplete");
                }
            }
        } else if (!this.allocations.isEmpty() || !this.changes.isEmpty()
                || !before.equals(after)) {
            throw new IllegalArgumentException(
                    "Rejected item inventory plan contains a mutation");
        }
    }

    public ItemInventoryPlanStatus status() {
        return status;
    }

    public boolean applicable() {
        return status == ItemInventoryPlanStatus.APPLICABLE;
    }

    public ItemInventoryMutationDirection direction() {
        return direction;
    }

    public ItemInventoryState before() {
        return before;
    }

    public ItemInventoryState after() {
        return after;
    }

    public List<ItemInventoryBatchEntry> entries() {
        return entries;
    }

    public List<ItemInventoryAllocation> allocations() {
        return allocations;
    }

    public List<ItemInventorySlotChange> changes() {
        return changes;
    }

    public Map<UUID, Integer> fulfilledCounts() {
        return fulfilledCounts;
    }

    public byte[] batchFingerprint() {
        return batchFingerprint.clone();
    }

    public byte[] beforeInventoryHash() {
        return before.inventoryHash();
    }

    public byte[] afterInventoryHash() {
        return after.inventoryHash();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ItemInventoryMutationPlan other
                && status == other.status
                && direction == other.direction
                && before.equals(other.before)
                && after.equals(other.after)
                && entries.equals(other.entries)
                && allocations.equals(other.allocations)
                && changes.equals(other.changes)
                && fulfilledCounts.equals(other.fulfilledCounts)
                && Arrays.equals(batchFingerprint,
                other.batchFingerprint);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(status, direction, before, after, entries,
                allocations, changes, fulfilledCounts);
        return 31 * result + Arrays.hashCode(batchFingerprint);
    }
}
