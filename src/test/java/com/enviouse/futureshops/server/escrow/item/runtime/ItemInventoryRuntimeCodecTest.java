package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.item.ItemInputMatcher;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchEntry;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryBatchPlanner;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationPlan;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationReceipt;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryMutationToken;
import com.enviouse.futureshops.server.escrow.item.ItemInventorySlot;
import com.enviouse.futureshops.server.escrow.item.ItemInventoryState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemInventoryRuntimeCodecTest {
    private static final Instant NOW = Instant.parse(
            "2026-07-17T12:34:56.789Z");

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void intentRoundTripsWithExactPreimagesAndPostimages() {
        ItemInventoryMutationIntent intent = intent();

        ItemInventoryMutationIntent decoded =
                ItemInventoryMutationIntentCodec.decode(
                        ItemInventoryMutationIntentCodec.encode(intent));

        assertEquals(intent, decoded);
    }

    @Test
    void intentRejectsTamperingAndTrailingData() {
        byte[] encoded = ItemInventoryMutationIntentCodec.encode(intent());
        byte[] tampered = encoded.clone();
        tampered[tampered.length / 2] ^= 1;

        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryMutationIntentCodec.decode(tampered));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryMutationIntentCodec.decode(
                        Arrays.copyOf(encoded, encoded.length + 1)));
    }

    @Test
    void abortRoundTripsAndRejectsNoncanonicalTime() {
        ItemInventoryMutationAbort abort = new ItemInventoryMutationAbort(
                intent().token(),
                ItemInventoryAbortReason.RECOVERY_FAILED_ROLLED_BACK, NOW);

        assertEquals(abort, ItemInventoryMutationAbortCodec.decode(
                ItemInventoryMutationAbortCodec.encode(abort)));
        byte[] encoded = ItemInventoryMutationAbortCodec.encode(abort);
        encoded[encoded.length / 2] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryMutationAbortCodec.decode(encoded));
        assertThrows(IllegalArgumentException.class,
                () -> new ItemInventoryMutationAbort(intent().token(),
                        ItemInventoryAbortReason.CALLER_CANCELLED,
                        Instant.parse("2026-07-17T12:34:56.789123Z")));
    }

    @Test
    void abortDecoderRejectsNormalizedNanoseconds() {
        ItemInventoryMutationAbort abort = new ItemInventoryMutationAbort(
                intent().token(), ItemInventoryAbortReason.CALLER_CANCELLED,
                NOW);
        byte[] encoded = ItemInventoryMutationAbortCodec.encode(abort);
        int payloadLength = encoded.length - 32;
        ByteBuffer.wrap(encoded, payloadLength - Integer.BYTES,
                Integer.BYTES).putInt(1_000_000_000);
        byte[] digest = sha256(Arrays.copyOf(encoded, payloadLength));
        System.arraycopy(digest, 0, encoded, payloadLength, digest.length);

        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryMutationAbortCodec.decode(encoded));
    }

    @Test
    void quarantineRoundTripsAndRejectsTampering() {
        ItemInventoryMutationQuarantine quarantine =
                new ItemInventoryMutationQuarantine(intent().token(),
                        ItemInventoryQuarantineReason.UNKNOWN_SLOT_IMAGE,
                        NOW);
        byte[] encoded = ItemInventoryMutationQuarantineCodec.encode(
                quarantine);

        assertEquals(quarantine,
                ItemInventoryMutationQuarantineCodec.decode(encoded));
        encoded[encoded.length / 2] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryMutationQuarantineCodec.decode(encoded));
    }

    private static ItemInventoryMutationIntent intent() {
        List<ItemStack> main = emptyMain();
        main.set(0, new ItemStack(Items.EMERALD, 6));
        ItemInventoryState before = ItemInventoryState.of(main,
                ItemStack.EMPTY);
        ItemInventoryMutationPlan plan = ItemInventoryBatchPlanner.plan(
                before, List.of(ItemInventoryBatchEntry.extract(
                        UUID.fromString(
                                "00000000-0000-0000-0000-000000000004"),
                        ItemInputMatcher.itemOnly("minecraft:emerald"), 2)));
        ItemInventoryMutationToken token = ItemInventoryMutationToken.create(
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000001"),
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000002"),
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000003"), plan);
        ItemInventoryMutationReceipt receipt =
                ItemInventoryMutationReceipt.create(token, plan, NOW);
        return ItemInventoryMutationIntent.create(token, plan, receipt);
    }

    private static List<ItemStack> emptyMain() {
        List<ItemStack> slots = new ArrayList<>();
        for (int index = 0;
             index < ItemInventorySlot.MAIN_SLOT_COUNT;
             index++) {
            slots.add(ItemStack.EMPTY);
        }
        return slots;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
