package com.enviouse.futureshops.money;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

public final class ItemStackSnapshotCodec {
    public static final int MAXIMUM_BYTES = 1_048_576;

    private ItemStackSnapshotCodec() {
    }

    public static byte[] encode(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException(
                    "Item stack snapshot cannot be empty");
        }
        try {
            CompoundTag tag = stack.save(new CompoundTag());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            NbtIo.write(tag, output);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0 || encoded.length > MAXIMUM_BYTES) {
                throw new IllegalArgumentException(
                        "Item stack snapshot exceeds its limit");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode item stack snapshot", exception);
        }
    }

    public static ItemStack decode(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length == 0 || encoded.length > MAXIMUM_BYTES) {
            throw new IllegalArgumentException(
                    "Item stack snapshot size is invalid");
        }
        ByteArrayInputStream bytes = new ByteArrayInputStream(encoded);
        try (DataInputStream input = new DataInputStream(bytes)) {
            CompoundTag tag = NbtIo.read(input,
                    new NbtAccounter(Math.multiplyExact(
                            (long) MAXIMUM_BYTES, 4L)));
            if (tag == null || bytes.available() != 0) {
                throw new IllegalArgumentException(
                        "Item stack snapshot NBT is invalid");
            }
            ItemStack stack = ItemStack.of(tag);
            if (stack.isEmpty()) {
                throw new IllegalArgumentException(
                        "Item stack snapshot resolved empty");
            }
            return stack;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Item stack snapshot is malformed", exception);
        }
    }
}
