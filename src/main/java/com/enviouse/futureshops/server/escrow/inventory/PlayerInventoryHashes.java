package com.enviouse.futureshops.server.escrow.inventory;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class PlayerInventoryHashes {
    static final int MAIN_SLOT_COUNT = 36;
    static final int HASH_BYTES = 32;

    private PlayerInventoryHashes() {
    }

    static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static byte[] hashText(String value) {
        return sha256(Objects.requireNonNull(value, "value")
                .getBytes(StandardCharsets.UTF_8));
    }

    static byte[] hashSlot(ItemStack stack) {
        return sha256(slotBytes(stack));
    }

    static byte[] hashInventory(List<ItemStack> slots) {
        requireSlots(slots);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(slots.size());
                for (int index = 0; index < slots.size(); index++) {
                    byte[] value = slotBytes(slots.get(index));
                    output.writeInt(index);
                    output.writeInt(value.length);
                    output.write(value);
                }
            }
            return sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to hash player inventory", exception);
        }
    }

    static List<ItemStack> copySlots(List<ItemStack> slots) {
        requireSlots(slots);
        List<ItemStack> copy = new ArrayList<>(slots.size());
        for (ItemStack stack : slots) {
            copy.add(stack.copy());
        }
        return List.copyOf(copy);
    }

    static List<ItemStack> readMainInventory(ListTag inventory) {
        Objects.requireNonNull(inventory, "inventory");
        List<ItemStack> slots = new ArrayList<>(MAIN_SLOT_COUNT);
        for (int index = 0; index < MAIN_SLOT_COUNT; index++) {
            slots.add(ItemStack.EMPTY);
        }
        boolean[] seen = new boolean[MAIN_SLOT_COUNT];
        for (int index = 0; index < inventory.size(); index++) {
            CompoundTag entry = inventory.getCompound(index);
            int slot = entry.getByte("Slot") & 255;
            if (slot >= MAIN_SLOT_COUNT) {
                continue;
            }
            if (seen[slot]) {
                throw new IllegalArgumentException(
                        "Player inventory contains a duplicate main slot");
            }
            seen[slot] = true;
            ItemStack stack = ItemStack.of(entry);
            slots.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        }
        return List.copyOf(slots);
    }

    static boolean equal(byte[] first, byte[] second) {
        return MessageDigest.isEqual(first, second);
    }

    static void requireHash(byte[] value, String label) {
        if (Objects.requireNonNull(value, label).length != HASH_BYTES) {
            throw new IllegalArgumentException(label + " must be a SHA-256 hash");
        }
    }

    private static byte[] slotBytes(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return new byte[]{0};
        }
        byte[] snapshot = ItemStackSnapshotCodec.encode(stack);
        byte[] value = new byte[Math.addExact(snapshot.length, 1)];
        value[0] = 1;
        System.arraycopy(snapshot, 0, value, 1, snapshot.length);
        return value;
    }

    private static void requireSlots(List<ItemStack> slots) {
        Objects.requireNonNull(slots, "slots");
        if (slots.size() != MAIN_SLOT_COUNT) {
            throw new IllegalArgumentException(
                    "Player main inventory must contain exactly 36 slots");
        }
        if (slots.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Player inventory slot is null");
        }
    }
}
