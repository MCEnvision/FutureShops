package com.enviouse.futureshops.server.escrow.item;

import com.enviouse.futureshops.money.ItemStackSnapshotCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

final class ItemStackSnapshotEvidence {
    private static final long MAX_DECODED_BYTES =
            Math.multiplyExact((long) ItemStackSnapshotCodec.MAXIMUM_BYTES, 4L);

    private ItemStackSnapshotEvidence() {
    }

    static String registryId(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty() || stack.getItem() == Items.AIR) {
            throw new IllegalArgumentException("Item stack is empty");
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null || !ForgeRegistries.ITEMS.containsKey(key)) {
            throw new IllegalArgumentException(
                    "Item stack registry entry is unavailable");
        }
        return key.toString();
    }

    static String requireRegistryId(String value) {
        String normalized = Objects.requireNonNull(value, "value");
        ResourceLocation parsed = ResourceLocation.tryParse(normalized);
        if (normalized.isEmpty() || normalized.length() > 256
                || parsed == null || !parsed.toString().equals(normalized)) {
            throw new IllegalArgumentException("Item registry ID is invalid");
        }
        return normalized;
    }

    static byte[] oneCountSnapshot(ItemStack stack) {
        ItemStack template = Objects.requireNonNull(stack, "stack").copy();
        if (template.isEmpty()) {
            throw new IllegalArgumentException("Item template is empty");
        }
        template.setCount(1);
        return ItemStackSnapshotCodec.encode(template);
    }

    static SnapshotMetadata inspect(byte[] snapshot) {
        CompoundTag tag = readTag(snapshot);
        if (!tag.contains("id", Tag.TAG_STRING)
                || !tag.contains("Count", Tag.TAG_BYTE)) {
            throw new IllegalArgumentException(
                    "Item stack snapshot NBT is invalid");
        }
        String registryId = requireRegistryId(tag.getString("id"));
        int count = tag.getByte("Count");
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "Item stack snapshot count is invalid");
        }
        return new SnapshotMetadata(registryId, count);
    }

    static byte[] canonicalOneCountSnapshot(byte[] fullSnapshot) {
        CompoundTag full = readTag(fullSnapshot);
        full.putByte("Count", (byte) 1);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            NbtIo.write(full, output);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            inspect(encoded);
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to create canonical item template", exception);
        }
    }

    private static CompoundTag readTag(byte[] snapshot) {
        byte[] encoded = Objects.requireNonNull(snapshot, "snapshot").clone();
        if (encoded.length == 0
                || encoded.length > ItemStackSnapshotCodec.MAXIMUM_BYTES) {
            throw new IllegalArgumentException(
                    "Item stack snapshot size is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            CompoundTag tag = NbtIo.read(input,
                    new NbtAccounter(MAX_DECODED_BYTES));
            if (tag == null || bytes.available() != 0) {
                throw new IllegalArgumentException(
                        "Item stack snapshot NBT is invalid");
            }
            return tag;
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Item stack snapshot is malformed", exception);
        }
    }

    static ItemStack exactPortion(ItemStack source, int count) {
        if (count <= 0 || count > source.getCount()) {
            throw new IllegalArgumentException("Item portion count is invalid");
        }
        ItemStack portion = source.copy();
        portion.setCount(count);
        return portion;
    }

    static boolean hasPersistentCapabilities(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag saved = stack.save(new CompoundTag());
        return saved.contains("ForgeCaps", Tag.TAG_COMPOUND)
                && !saved.getCompound("ForgeCaps").isEmpty();
    }

    static boolean exactOneCountEquals(ItemStack stack, byte[] template) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return Arrays.equals(oneCountSnapshot(stack), template);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    record SnapshotMetadata(String registryId, int count) {
    }
}
