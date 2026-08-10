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
import java.io.OutputStream;
import java.util.Objects;

public final class ItemStackSnapshotCodec {
    public static final int MAXIMUM_BYTES = 1_048_576;

    private ItemStackSnapshotCodec() {
    }

    public static boolean sameIdentity(ItemStack first, ItemStack second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.isEmpty() || second.isEmpty()) {
            return first.isEmpty() && second.isEmpty();
        }
        return first.save(new CompoundTag()).equals(
                second.save(new CompoundTag()));
    }

    public static boolean snapshotMatchesIdentity(
            byte[] snapshot,
            ItemStack stack
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(stack, "stack");
        if (snapshot.length == 0 || stack.isEmpty()) {
            return snapshot.length == 0 && stack.isEmpty();
        }
        try {
            CompoundTag tag = readTag(snapshot);
            return !ItemStack.of(tag).isEmpty()
                    && tag.equals(stack.save(new CompoundTag()));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static boolean snapshotsSameIdentity(
            byte[] first,
            byte[] second
    ) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (first.length == 0 || second.length == 0) {
            return first.length == 0 && second.length == 0;
        }
        try {
            CompoundTag firstTag = readTag(first);
            CompoundTag secondTag = readTag(second);
            return !ItemStack.of(firstTag).isEmpty()
                    && !ItemStack.of(secondTag).isEmpty()
                    && firstTag.equals(secondTag);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static byte[] encode(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty() || stack.getCount() <= 0
                || stack.getCount() > Byte.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Item stack snapshot count is invalid");
        }
        try {
            CompoundTag tag = stack.save(new CompoundTag());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(
                    new LimitedOutputStream(bytes, MAXIMUM_BYTES));
            NbtIo.write(tag, output);
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length == 0) {
                throw new IllegalArgumentException(
                        "Item stack snapshot is empty");
            }
            return encoded;
        } catch (SnapshotSizeLimitException exception) {
            throw new IllegalArgumentException(
                    "Item stack snapshot exceeds its limit", exception);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to encode item stack snapshot", exception);
        }
    }

    public static ItemStack decode(byte[] encoded) {
        CompoundTag tag = readTag(encoded);
        ItemStack stack = ItemStack.of(tag);
        if (stack.isEmpty()) {
            throw new IllegalArgumentException(
                    "Item stack snapshot resolved empty");
        }
        return stack;
    }

    private static CompoundTag readTag(byte[] encoded) {
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
            return tag;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException(
                    "Item stack snapshot is malformed", exception);
        }
    }

    private static final class LimitedOutputStream extends OutputStream {
        private final ByteArrayOutputStream target;
        private final int maximumBytes;

        private LimitedOutputStream(
                ByteArrayOutputStream target,
                int maximumBytes
        ) {
            this.target = target;
            this.maximumBytes = maximumBytes;
        }

        @Override
        public void write(int value) throws IOException {
            requireCapacity(1);
            target.write(value);
        }

        @Override
        public void write(byte[] value, int offset, int length)
                throws IOException {
            Objects.checkFromIndexSize(offset, length, value.length);
            requireCapacity(length);
            target.write(value, offset, length);
        }

        private void requireCapacity(int additionalBytes)
                throws SnapshotSizeLimitException {
            if (additionalBytes < 0
                    || target.size() > maximumBytes - additionalBytes) {
                throw new SnapshotSizeLimitException();
            }
        }
    }

    private static final class SnapshotSizeLimitException
            extends IOException {
    }
}
