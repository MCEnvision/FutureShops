package com.enviouse.futureshops.server.escrow.item;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

final class ItemInventoryHashes {
    static final int HASH_BYTES = 32;

    private ItemInventoryHashes() {
    }

    static byte[] hashInventory(List<ItemStack> slots) {
        requireSlots(slots);
        return hashInventorySnapshots(slots.stream()
                .map(ItemInventoryHashes::snapshot).toList());
    }

    static byte[] hashInventorySnapshots(List<byte[]> snapshots) {
        requireSnapshots(snapshots);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(1);
            output.writeInt(snapshots.size());
            for (int index = 0; index < snapshots.size(); index++) {
                byte[] snapshot = slotBytes(snapshots.get(index));
                output.writeInt(ItemInventorySlot.fromLogicalIndex(index)
                        .serializedSlot());
                output.writeInt(snapshot.length);
                output.write(snapshot);
            }
            output.flush();
            return sha256(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to hash item inventory", exception);
        }
    }

    static byte[] hashSlot(ItemStack stack) {
        return hashSlotSnapshot(snapshot(stack));
    }

    static byte[] hashSlotSnapshot(byte[] snapshot) {
        return sha256(slotBytes(snapshot));
    }

    static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                    Objects.requireNonNull(value, "value"));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static byte[] hashText(String value) {
        return sha256(Objects.requireNonNull(value, "value")
                .getBytes(StandardCharsets.UTF_8));
    }

    static String hex(byte[] value) {
        requireHash(value, "Item hash");
        return HexFormat.of().formatHex(value);
    }

    static byte[] fromHex(String value) {
        Objects.requireNonNull(value, "value");
        if (value.length() != HASH_BYTES * 2) {
            throw new IllegalArgumentException("Item hash text is invalid");
        }
        try {
            byte[] decoded = HexFormat.of().parseHex(value);
            requireHash(decoded, "Item hash");
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Item hash text is invalid",
                    exception);
        }
    }

    static void requireHash(byte[] value, String name) {
        if (Objects.requireNonNull(value, "value").length != HASH_BYTES) {
            throw new IllegalArgumentException(
                    Objects.requireNonNull(name, "name")
                            + " must be a SHA-256 hash");
        }
    }

    static boolean equal(byte[] first, byte[] second) {
        return first != null && second != null
                && MessageDigest.isEqual(first, second);
    }

    static List<ItemStack> copySlots(List<ItemStack> slots) {
        requireSlots(slots);
        return slots.stream().map(ItemInventoryHashes::copyStack).toList();
    }

    private static ItemStack copyStack(ItemStack stack) {
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    static List<byte[]> snapshots(List<ItemStack> slots) {
        requireSlots(slots);
        return slots.stream().map(ItemInventoryHashes::snapshot).toList();
    }

    private static byte[] snapshot(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return new byte[0];
        }
        return ItemStackSnapshotCodec.encode(stack);
    }

    private static byte[] slotBytes(byte[] snapshot) {
        byte[] encoded = Objects.requireNonNull(snapshot, "snapshot");
        if (encoded.length == 0) {
            return new byte[]{0};
        }
        byte[] value = new byte[Math.addExact(encoded.length, 1)];
        value[0] = 1;
        System.arraycopy(encoded, 0, value, 1, encoded.length);
        return value;
    }

    private static void requireSlots(List<ItemStack> slots) {
        Objects.requireNonNull(slots, "slots");
        if (slots.size() != ItemInventorySlot.ACCESSIBLE_SLOT_COUNT
                || slots.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Item inventory must contain main inventory and offhand");
        }
    }

    private static void requireSnapshots(List<byte[]> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots");
        if (snapshots.size() != ItemInventorySlot.ACCESSIBLE_SLOT_COUNT
                || snapshots.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Item inventory snapshots must contain main inventory and offhand");
        }
    }
}
