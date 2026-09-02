package com.enviouse.futureshops.server.escrow.inventory;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
import java.util.TreeSet;

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

    static byte[] hashSlotLegacy(ItemStack stack) {
        return sha256(legacySlotBytes(stack));
    }

    static byte[] hashInventory(List<ItemStack> slots) {
        return hashInventory(slots, false);
    }

    static byte[] hashInventoryLegacy(List<ItemStack> slots) {
        return hashInventory(slots, true);
    }

    private static byte[] hashInventory(
            List<ItemStack> slots,
            boolean legacy
    ) {
        requireSlots(slots);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(slots.size());
                for (int index = 0; index < slots.size(); index++) {
                    byte[] value = legacy
                            ? legacySlotBytes(slots.get(index))
                            : slotBytes(slots.get(index));
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
        if (!inventory.isEmpty()
                && inventory.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException(
                    "Player inventory list has an invalid element type");
        }
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
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(1);
                writeCanonicalTag(output, stack.save(new CompoundTag()));
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to hash player inventory slot", exception);
        }
    }

    private static byte[] legacySlotBytes(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return new byte[]{0};
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(1);
                net.minecraft.nbt.NbtIo.write(
                        stack.save(new CompoundTag()), output);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to hash legacy player inventory slot", exception);
        }
    }

    private static void writeCanonicalTag(
            DataOutputStream output,
            Tag tag
    ) throws IOException {
        output.writeByte(tag.getId());
        switch (tag.getId()) {
            case Tag.TAG_END -> {
            }
            case Tag.TAG_BYTE -> output.writeByte(
                    ((NumericTag) tag).getAsByte());
            case Tag.TAG_SHORT -> output.writeShort(
                    ((NumericTag) tag).getAsShort());
            case Tag.TAG_INT -> output.writeInt(
                    ((NumericTag) tag).getAsInt());
            case Tag.TAG_LONG -> output.writeLong(
                    ((NumericTag) tag).getAsLong());
            case Tag.TAG_FLOAT -> output.writeInt(Float.floatToRawIntBits(
                    ((NumericTag) tag).getAsFloat()));
            case Tag.TAG_DOUBLE -> output.writeLong(Double.doubleToRawLongBits(
                    ((NumericTag) tag).getAsDouble()));
            case Tag.TAG_BYTE_ARRAY -> writeBytes(output,
                    ((ByteArrayTag) tag).getAsByteArray());
            case Tag.TAG_STRING -> writeText(output,
                    ((StringTag) tag).getAsString());
            case Tag.TAG_LIST -> {
                ListTag list = (ListTag) tag;
                output.writeInt(list.size());
                for (Tag value : list) {
                    writeCanonicalTag(output, value);
                }
            }
            case Tag.TAG_COMPOUND -> {
                CompoundTag compound = (CompoundTag) tag;
                TreeSet<String> keys = new TreeSet<>(compound.getAllKeys());
                output.writeInt(keys.size());
                for (String key : keys) {
                    writeText(output, key);
                    Tag value = compound.get(key);
                    if (value == null) {
                        throw new IllegalStateException(
                                "Canonical NBT key has no value");
                    }
                    writeCanonicalTag(output, value);
                }
            }
            case Tag.TAG_INT_ARRAY -> {
                int[] values = ((IntArrayTag) tag).getAsIntArray();
                output.writeInt(values.length);
                for (int value : values) {
                    output.writeInt(value);
                }
            }
            case Tag.TAG_LONG_ARRAY -> {
                long[] values = ((LongArrayTag) tag).getAsLongArray();
                output.writeInt(values.length);
                for (long value : values) {
                    output.writeLong(value);
                }
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported NBT tag type " + tag.getId());
        }
    }

    private static void writeText(
            DataOutputStream output,
            String value
    ) throws IOException {
        writeBytes(output, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(
            DataOutputStream output,
            byte[] value
    ) throws IOException {
        output.writeInt(value.length);
        output.write(value);
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
