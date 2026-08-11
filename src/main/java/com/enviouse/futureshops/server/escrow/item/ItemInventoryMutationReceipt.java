package com.enviouse.futureshops.server.escrow.item;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ItemInventoryMutationReceipt {
    public static final int MAX_ALLOCATIONS = 256;

    private final ItemInventoryMutationToken token;
    private final List<ItemInventoryAllocation> actualPortions;
    private final Instant appliedAt;
    private final byte[] digest;

    ItemInventoryMutationReceipt(
            ItemInventoryMutationToken token,
            List<ItemInventoryAllocation> actualPortions,
            Instant appliedAt,
            byte[] digest
    ) {
        this.token = Objects.requireNonNull(token, "token");
        this.actualPortions = List.copyOf(Objects.requireNonNull(
                actualPortions, "actualPortions"));
        this.appliedAt = Objects.requireNonNull(appliedAt, "appliedAt");
        this.digest = Objects.requireNonNull(digest, "digest").clone();
        ItemInventoryHashes.requireHash(this.digest,
                "Item mutation receipt digest");
        if (this.actualPortions.isEmpty()
                || this.actualPortions.size() > MAX_ALLOCATIONS) {
            throw new IllegalArgumentException(
                    "Item mutation receipt portion count is invalid");
        }
        Set<ItemInventorySlot> changedSlots = new HashSet<>();
        for (ItemInventorySlotChange change : token.changes()) {
            changedSlots.add(change.slot());
        }
        long totalSnapshotBytes = 0L;
        Set<ItemInventorySlot> portionSlots = new HashSet<>();
        for (ItemInventoryAllocation portion : this.actualPortions) {
            Objects.requireNonNull(portion, "portion");
            if (!changedSlots.contains(portion.slot())) {
                throw new IllegalArgumentException(
                        "Item mutation receipt portion uses an unchanged slot");
            }
            portionSlots.add(portion.slot());
            totalSnapshotBytes = Math.addExact(totalSnapshotBytes,
                    portion.actualStackSnapshot().length);
        }
        if (totalSnapshotBytes
                > ItemInventoryMutationReceiptCodec.MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException(
                    "Item mutation receipt snapshots exceed their limit");
        }
        if (!changedSlots.equals(portionSlots)
                || !ItemInventoryHashes.equal(token.portionFingerprint(),
                ItemInventoryMutationToken.fingerprintPortions(
                        this.actualPortions))) {
            throw new IllegalArgumentException(
                    "Item mutation receipt portions do not match its token");
        }
        byte[] expected = ItemInventoryHashes.sha256(
                ItemInventoryMutationReceiptCodec.payloadBytes(token,
                        this.actualPortions, appliedAt));
        if (!ItemInventoryHashes.equal(this.digest, expected)) {
            throw new IllegalArgumentException(
                    "Item mutation receipt digest is invalid");
        }
    }

    public static ItemInventoryMutationReceipt create(
            ItemInventoryMutationToken token,
            ItemInventoryMutationPlan plan,
            Instant appliedAt
    ) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(appliedAt, "appliedAt");
        if (!token.matches(plan)) {
            throw new IllegalArgumentException(
                    "Item mutation receipt plan does not match its token");
        }
        byte[] digest = ItemInventoryHashes.sha256(
                ItemInventoryMutationReceiptCodec.payloadBytes(token,
                        plan.allocations(), appliedAt));
        return new ItemInventoryMutationReceipt(token, plan.allocations(),
                appliedAt, digest);
    }

    public ItemInventoryMutationToken token() {
        return token;
    }

    public List<ItemInventoryAllocation> actualPortions() {
        return actualPortions;
    }

    public Instant appliedAt() {
        return appliedAt;
    }

    public byte[] digest() {
        return digest.clone();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ItemInventoryMutationReceipt other
                && token.equals(other.token)
                && actualPortions.equals(other.actualPortions)
                && appliedAt.equals(other.appliedAt)
                && Arrays.equals(digest, other.digest);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(token, actualPortions, appliedAt);
        return 31 * result + Arrays.hashCode(digest);
    }
}
