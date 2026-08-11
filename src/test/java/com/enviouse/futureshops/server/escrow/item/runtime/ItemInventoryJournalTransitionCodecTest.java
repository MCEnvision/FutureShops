package com.enviouse.futureshops.server.escrow.item.runtime;

import com.enviouse.futureshops.MinecraftTestBootstrap;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemInventoryJournalTransitionCodecTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void everyTypedTransitionRoundTrips() {
        assertEquals(EscrowJournalEventCodec.MAX_BODY_BYTES,
                ItemInventoryJournalTransitionCodec.MAX_ENCODED_BYTES);
        ItemInventoryMutationIntent intent = intent();
        List<ItemInventoryJournalTransition> transitions = List.of(
                ItemInventoryJournalTransition.prepare(intent),
                ItemInventoryJournalTransition.commit(
                        intent.plannedReceipt()),
                ItemInventoryJournalTransition.abort(
                        new ItemInventoryMutationAbort(intent.token(),
                                ItemInventoryAbortReason.CALLER_CANCELLED,
                                ItemInventoryJournalTestFixtures.NOW)),
                ItemInventoryJournalTransition.quarantine(
                        new ItemInventoryMutationQuarantine(intent.token(),
                                ItemInventoryQuarantineReason
                                        .UNKNOWN_SLOT_IMAGE,
                                ItemInventoryJournalTestFixtures.NOW)));

        for (ItemInventoryJournalTransition transition : transitions) {
            assertEquals(transition,
                    ItemInventoryJournalTransitionCodec.decode(
                            ItemInventoryJournalTransitionCodec.encode(
                                    transition)));
        }
    }

    @Test
    void decoderRejectsTamperingTruncationTrailingAndOversize() {
        byte[] encoded = ItemInventoryJournalTransitionCodec.encode(
                ItemInventoryJournalTransition.prepare(intent()));
        byte[] tampered = encoded.clone();
        tampered[tampered.length / 2] ^= 1;

        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryJournalTransitionCodec.decode(tampered));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryJournalTransitionCodec.decode(
                        Arrays.copyOf(encoded, encoded.length - 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryJournalTransitionCodec.decode(
                        Arrays.copyOf(encoded, encoded.length + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryJournalTransitionCodec.decode(
                        new byte[ItemInventoryJournalTransitionCodec
                                .MAX_ENCODED_BYTES + 1]));
    }

    @Test
    void decoderRejectsUnknownTransitionTypeAndInvalidBodyLength() {
        byte[] unknown = ItemInventoryJournalTransitionCodec.encode(
                ItemInventoryJournalTransition.prepare(intent()));
        unknown[Integer.BYTES + Short.BYTES] = 99;
        refreshDigest(unknown);

        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryJournalTransitionCodec.decode(unknown));

        byte[] invalidLength = ItemInventoryJournalTransitionCodec.encode(
                ItemInventoryJournalTransition.prepare(intent()));
        int lengthOffset = Integer.BYTES + Short.BYTES + Byte.BYTES;
        Arrays.fill(invalidLength, lengthOffset,
                lengthOffset + Integer.BYTES, (byte) 0);
        refreshDigest(invalidLength);
        assertThrows(IllegalArgumentException.class,
                () -> ItemInventoryJournalTransitionCodec.decode(
                        invalidLength));
    }

    private static ItemInventoryMutationIntent intent() {
        return ItemInventoryJournalTestFixtures.intent(
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000001"),
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000002"),
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000003"));
    }

    private static void refreshDigest(byte[] encoded) {
        int payloadLength = encoded.length - 32;
        byte[] digest = sha256(Arrays.copyOf(encoded, payloadLength));
        System.arraycopy(digest, 0, encoded, payloadLength, digest.length);
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
