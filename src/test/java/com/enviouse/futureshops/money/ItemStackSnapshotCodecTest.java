package com.enviouse.futureshops.money;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemStackSnapshotCodecTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void exactItemCountAndNbtRoundTrip() {
        ItemStack stack = new ItemStack(Items.DIAMOND, 37);
        stack.getOrCreateTag().putString("escrow_test", "exact snapshot");

        byte[] encoded = ItemStackSnapshotCodec.encode(stack);
        ItemStack decoded = ItemStackSnapshotCodec.decode(encoded);

        assertEquals(stack.getItem(), decoded.getItem());
        assertEquals(37, decoded.getCount());
        assertEquals(stack.getTag(), decoded.getTag());
        assertEquals(Arrays.toString(encoded),
                Arrays.toString(ItemStackSnapshotCodec.encode(decoded)));
    }

    @Test
    void emptyOversizedTruncatedAndTrailingSnapshotsFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemStackSnapshotCodec.encode(ItemStack.EMPTY));
        ItemStack invalidCount = new ItemStack(Items.STONE, 1);
        invalidCount.setCount(128);
        assertThrows(IllegalArgumentException.class,
                () -> ItemStackSnapshotCodec.encode(invalidCount));
        ItemStack oversizedTag = new ItemStack(Items.STONE, 1);
        for (int index = 0; index < 20; index++) {
            oversizedTag.getOrCreateTag().putString(
                    "large" + index, "x".repeat(60000));
        }
        assertThrows(IllegalArgumentException.class,
                () -> ItemStackSnapshotCodec.encode(oversizedTag));
        assertThrows(IllegalArgumentException.class,
                () -> ItemStackSnapshotCodec.decode(new byte[0]));
        assertThrows(IllegalArgumentException.class,
                () -> ItemStackSnapshotCodec.decode(
                        new byte[ItemStackSnapshotCodec.MAXIMUM_BYTES + 1]));

        byte[] encoded = ItemStackSnapshotCodec.encode(
                new ItemStack(Items.EMERALD, 2));
        assertThrows(IllegalArgumentException.class,
                () -> ItemStackSnapshotCodec.decode(
                        Arrays.copyOf(encoded, encoded.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ItemStackSnapshotCodec.decode(
                        Arrays.copyOf(encoded, encoded.length + 1)));
    }

    @Test
    void semanticIdentityAcceptsCompoundOrderButPreservesExactValues() {
        byte[] first = orderedSnapshot(false, "second");
        byte[] reordered = orderedSnapshot(true, "second");
        byte[] changed = orderedSnapshot(true, "different");
        ItemStack stack = ItemStackSnapshotCodec.decode(first);

        assertFalse(Arrays.equals(first, reordered));
        assertTrue(ItemStackSnapshotCodec.snapshotMatchesIdentity(
                reordered, stack));
        assertTrue(ItemStackSnapshotCodec.snapshotsSameIdentity(
                first, reordered));
        assertFalse(ItemStackSnapshotCodec.snapshotsSameIdentity(
                first, changed));
        assertFalse(ItemStackSnapshotCodec.snapshotMatchesIdentity(
                changed, stack));
    }

    private static byte[] orderedSnapshot(
            boolean reverse,
            String secondValue
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeUTF("");
                output.writeByte(Tag.TAG_STRING);
                output.writeUTF("id");
                output.writeUTF("minecraft:diamond");
                output.writeByte(Tag.TAG_BYTE);
                output.writeUTF("Count");
                output.writeByte(1);
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeUTF("tag");
                writeOrderedValue(output, reverse ? "second" : "first",
                        reverse ? secondValue : "first");
                writeOrderedValue(output, reverse ? "first" : "second",
                        reverse ? "first" : secondValue);
                output.writeByte(Tag.TAG_END);
                output.writeByte(Tag.TAG_END);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void writeOrderedValue(
            DataOutputStream output,
            String key,
            String value
    ) throws IOException {
        output.writeByte(Tag.TAG_STRING);
        output.writeUTF(key);
        output.writeUTF(value);
    }
}
