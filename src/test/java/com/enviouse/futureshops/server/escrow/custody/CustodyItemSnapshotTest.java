package com.enviouse.futureshops.server.escrow.custody;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustodyItemSnapshotTest {
    @Test
    void exactNbtPayloadAndHashAreDefensivelyCopied() {
        byte[] nbt = new byte[]{10, 0, 3, 4, 5};
        CustodyItemSnapshot snapshot = CustodyItemSnapshot.capture("minecraft:diamond", 7, nbt);
        byte[] expected = snapshot.serializedNbt();

        nbt[2] = 99;
        snapshot.serializedNbt()[1] = 88;
        snapshot.contentHash()[0] = 77;

        assertArrayEquals(expected, snapshot.serializedNbt());
        assertEquals(64, snapshot.fingerprint().length());
    }

    @Test
    void tamperedNbtIsRejectedAgainstPersistedHash() {
        CustodyItemSnapshot snapshot = CustodyItemSnapshot.capture("minecraft:diamond", 1,
                new byte[]{10, 0, 1});
        byte[] tampered = snapshot.serializedNbt();
        tampered[2] = 2;

        assertThrows(IllegalArgumentException.class, () -> new CustodyItemSnapshot(
                snapshot.registryId(), snapshot.count(), tampered, snapshot.contentHash()));
    }

    @Test
    void oversizedNbtIsRejectedBeforeCustody() {
        byte[] oversized = new byte[CustodyItemSnapshot.MAX_NBT_BYTES + 1];
        Arrays.fill(oversized, (byte) 1);

        assertThrows(IllegalArgumentException.class,
                () -> CustodyItemSnapshot.capture("minecraft:chest", 1, oversized));
    }

    @Test
    void malformedUnicodeCannotEnterCustodyIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> CustodyItemSnapshot.capture("minecraft:\uD800", 1, new byte[]{1}));
        assertThrows(IllegalArgumentException.class,
                () -> CustodyTestFixtures.itemLot("bad \uD800 request", 1));
    }
}
