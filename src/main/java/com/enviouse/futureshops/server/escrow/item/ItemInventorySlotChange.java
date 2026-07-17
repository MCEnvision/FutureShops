package com.enviouse.futureshops.server.escrow.item;

import java.util.Arrays;
import java.util.Objects;

public record ItemInventorySlotChange(
        ItemInventorySlot slot,
        byte[] beforeHash,
        byte[] afterHash
) implements Comparable<ItemInventorySlotChange> {
    public ItemInventorySlotChange {
        Objects.requireNonNull(slot, "slot");
        beforeHash = Objects.requireNonNull(beforeHash, "beforeHash").clone();
        afterHash = Objects.requireNonNull(afterHash, "afterHash").clone();
        ItemInventoryHashes.requireHash(beforeHash,
                "Item slot before hash");
        ItemInventoryHashes.requireHash(afterHash,
                "Item slot after hash");
        if (ItemInventoryHashes.equal(beforeHash, afterHash)) {
            throw new IllegalArgumentException(
                    "Item inventory slot did not change");
        }
    }

    @Override
    public byte[] beforeHash() {
        return beforeHash.clone();
    }

    @Override
    public byte[] afterHash() {
        return afterHash.clone();
    }

    @Override
    public int compareTo(ItemInventorySlotChange other) {
        return slot.compareTo(other.slot);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ItemInventorySlotChange other
                && slot.equals(other.slot)
                && Arrays.equals(beforeHash, other.beforeHash)
                && Arrays.equals(afterHash, other.afterHash);
    }

    @Override
    public int hashCode() {
        int result = slot.hashCode();
        result = 31 * result + Arrays.hashCode(beforeHash);
        return 31 * result + Arrays.hashCode(afterHash);
    }
}
