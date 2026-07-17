package com.enviouse.futureshops.server.escrow.item;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public final class ItemInventoryBatchEntry {
    private final UUID entryId;
    private final ItemInventoryMutationDirection direction;
    private final ItemInputMatcher matcher;
    private final int count;
    private final byte[] insertionSnapshot;

    private ItemInventoryBatchEntry(
            UUID entryId,
            ItemInventoryMutationDirection direction,
            ItemInputMatcher matcher,
            int count,
            byte[] insertionSnapshot
    ) {
        this.entryId = requireUuid(entryId, "entryId");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.count = count;
        this.insertionSnapshot = Objects.requireNonNull(
                insertionSnapshot, "insertionSnapshot").clone();
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Item inventory batch count must be positive");
        }
        if (direction == ItemInventoryMutationDirection.INSERT) {
            if (matcher.mode() != ItemMatchMode.EXACT
                    || this.insertionSnapshot.length == 0) {
                throw new IllegalArgumentException(
                        "Item insertion requires an exact stack snapshot");
            }
            ItemStack stack = ItemStackSnapshotCodec.decode(
                    this.insertionSnapshot);
            if (stack.getCount() != count || !matcher.matches(stack)) {
                throw new IllegalArgumentException(
                        "Item insertion snapshot does not match its request");
            }
        } else if (this.insertionSnapshot.length != 0) {
            throw new IllegalArgumentException(
                    "Item extraction cannot contain an insertion snapshot");
        }
    }

    public static ItemInventoryBatchEntry insert(
            UUID entryId,
            ItemStack stack
    ) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty() || stack.getCount() <= 0) {
            throw new IllegalArgumentException(
                    "Item insertion stack is empty");
        }
        return new ItemInventoryBatchEntry(entryId,
                ItemInventoryMutationDirection.INSERT,
                ItemInputMatcher.exact(stack), stack.getCount(),
                ItemStackSnapshotCodec.encode(stack));
    }

    public static ItemInventoryBatchEntry extract(
            UUID entryId,
            ItemInputMatcher matcher,
            int count
    ) {
        return new ItemInventoryBatchEntry(entryId,
                ItemInventoryMutationDirection.EXTRACT, matcher, count,
                new byte[0]);
    }

    public UUID entryId() {
        return entryId;
    }

    public ItemInventoryMutationDirection direction() {
        return direction;
    }

    public ItemInputMatcher matcher() {
        return matcher;
    }

    public int count() {
        return count;
    }

    public byte[] insertionSnapshot() {
        return insertionSnapshot.clone();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ItemInventoryBatchEntry other
                && entryId.equals(other.entryId)
                && direction == other.direction
                && matcher.equals(other.matcher)
                && count == other.count
                && Arrays.equals(insertionSnapshot,
                other.insertionSnapshot);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(entryId, direction, matcher, count);
        return 31 * result + Arrays.hashCode(insertionSnapshot);
    }

    static UUID requireUuid(UUID value, String name) {
        UUID result = Objects.requireNonNull(value, name);
        if (result.getMostSignificantBits() == 0L
                && result.getLeastSignificantBits() == 0L) {
            throw new IllegalArgumentException(name + " cannot be zero");
        }
        return result;
    }
}
