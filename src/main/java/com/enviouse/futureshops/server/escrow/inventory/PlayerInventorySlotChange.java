package com.enviouse.futureshops.server.escrow.inventory;

import java.util.Arrays;
import java.util.Objects;

public record PlayerInventorySlotChange(
        int slot,
        byte[] beforeHash,
        byte[] afterHash
) {
    public PlayerInventorySlotChange {
        if (slot < 0 || slot >= PlayerInventoryHashes.MAIN_SLOT_COUNT) {
            throw new IllegalArgumentException("Player inventory slot is invalid");
        }
        beforeHash = Objects.requireNonNull(beforeHash, "beforeHash").clone();
        afterHash = Objects.requireNonNull(afterHash, "afterHash").clone();
        PlayerInventoryHashes.requireHash(beforeHash, "Player inventory before hash");
        PlayerInventoryHashes.requireHash(afterHash, "Player inventory after hash");
        if (PlayerInventoryHashes.equal(beforeHash, afterHash)) {
            throw new IllegalArgumentException("Player inventory slot did not change");
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
    public boolean equals(Object object) {
        return object instanceof PlayerInventorySlotChange other
                && slot == other.slot
                && Arrays.equals(beforeHash, other.beforeHash)
                && Arrays.equals(afterHash, other.afterHash);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(slot);
        result = 31 * result + Arrays.hashCode(beforeHash);
        return 31 * result + Arrays.hashCode(afterHash);
    }
}
