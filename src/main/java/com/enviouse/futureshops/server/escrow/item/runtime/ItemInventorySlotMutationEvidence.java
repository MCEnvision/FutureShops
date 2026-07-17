package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import com.enviouse.futureshops.server.escrow.item.ItemInventorySlot;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryState;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Objects;

public final class ItemInventorySlotMutationEvidence
        implements Comparable<ItemInventorySlotMutationEvidence> {
    private final ItemInventorySlot slot;
    private final byte[] beforeSnapshot;
    private final byte[] afterSnapshot;

    ItemInventorySlotMutationEvidence(
            ItemInventorySlot slot,
            byte[] beforeSnapshot,
            byte[] afterSnapshot
    ) {
        this.slot = Objects.requireNonNull(slot, "slot");
        this.beforeSnapshot = requireSnapshot(beforeSnapshot);
        this.afterSnapshot = requireSnapshot(afterSnapshot);
        ItemStack before = decode(this.beforeSnapshot);
        ItemStack after = decode(this.afterSnapshot);
        if (sameStack(before, after)) {
            throw new IllegalArgumentException(
                    "Item inventory slot evidence contains no change");
        }
    }

    static ItemInventorySlotMutationEvidence capture(
            ItemInventorySlot slot,
            ItemStack before,
            ItemStack after
    ) {
        return new ItemInventorySlotMutationEvidence(slot,
                encode(before), encode(after));
    }

    public ItemInventorySlot slot() {
        return slot;
    }

    public byte[] beforeSnapshot() {
        return beforeSnapshot.clone();
    }

    public byte[] afterSnapshot() {
        return afterSnapshot.clone();
    }

    public ItemStack beforeStack() {
        return decode(beforeSnapshot);
    }

    public ItemStack afterStack() {
        return decode(afterSnapshot);
    }

    public boolean matchesBefore(ItemStack stack) {
        return matches(stack, beforeSnapshot);
    }

    public boolean matchesAfter(ItemStack stack) {
        return matches(stack, afterSnapshot);
    }

    public boolean hashesMatch(
            byte[] expectedBeforeHash,
            byte[] expectedAfterHash
    ) {
        return ItemInventoryState.stackMatchesHash(
                beforeStack(), expectedBeforeHash)
                && ItemInventoryState.stackMatchesHash(
                afterStack(), expectedAfterHash);
    }

    @Override
    public int compareTo(ItemInventorySlotMutationEvidence other) {
        return slot.compareTo(other.slot);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ItemInventorySlotMutationEvidence other
                && slot.equals(other.slot)
                && Arrays.equals(beforeSnapshot, other.beforeSnapshot)
                && Arrays.equals(afterSnapshot, other.afterSnapshot);
    }

    @Override
    public int hashCode() {
        int result = slot.hashCode();
        result = 31 * result + Arrays.hashCode(beforeSnapshot);
        return 31 * result + Arrays.hashCode(afterSnapshot);
    }

    private static byte[] encode(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return stack.isEmpty() ? new byte[0]
                : ItemStackSnapshotCodec.encode(stack);
    }

    private static ItemStack decode(byte[] snapshot) {
        return snapshot.length == 0 ? ItemStack.EMPTY
                : ItemStackSnapshotCodec.decode(snapshot);
    }

    private static byte[] requireSnapshot(byte[] snapshot) {
        byte[] copied = Objects.requireNonNull(
                snapshot, "snapshot").clone();
        if (copied.length > ItemStackSnapshotCodec.MAXIMUM_BYTES) {
            throw new IllegalArgumentException(
                    "Item inventory slot snapshot exceeds its limit");
        }
        if (copied.length != 0) {
            ItemStack decoded = ItemStackSnapshotCodec.decode(copied);
            if (decoded.isEmpty()
                    || !Arrays.equals(copied,
                    ItemStackSnapshotCodec.encode(decoded))) {
                throw new IllegalArgumentException(
                        "Item inventory slot snapshot is not canonical");
            }
        }
        return copied;
    }

    private static boolean matches(ItemStack stack, byte[] snapshot) {
        Objects.requireNonNull(stack, "stack");
        if (snapshot.length == 0) {
            return stack.isEmpty();
        }
        if (stack.isEmpty()) {
            return false;
        }
        try {
            return Arrays.equals(snapshot,
                    ItemStackSnapshotCodec.encode(stack));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.isEmpty() && second.isEmpty()
                || !first.isEmpty() && !second.isEmpty()
                && Arrays.equals(ItemStackSnapshotCodec.encode(first),
                ItemStackSnapshotCodec.encode(second));
    }
}
