package com.enviouse.futureshops.server.escrow.checkpoint;

import com.enviouse.futureshops.server.escrow.stock.StockSavedData;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowCheckpointCodecTest {
    @Test
    void roundTripPreservesEveryOpaqueStoreExactly() {
        EscrowCheckpoint checkpoint = EscrowCheckpointTestFixtures.firstCheckpoint();
        byte[] encoded = EscrowCheckpointCodec.encode(checkpoint);
        EscrowCheckpoint decoded = EscrowCheckpointCodec.decode(encoded);

        assertEquals(checkpoint, decoded);
        assertEquals(EscrowCheckpointStore.values().length, decoded.snapshots().size());
        assertEquals(EscrowCheckpointCodec.encodedSize(checkpoint), encoded.length);
        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
            assertArrayEquals(checkpoint.snapshot(store), decoded.snapshot(store));
        }
    }

    @Test
    void snapshotsAreDefensivelyCopied() {
        Map<EscrowCheckpointStore, byte[]> snapshots =
                EscrowCheckpointTestFixtures.snapshots("copy");
        EscrowCheckpoint checkpoint = new EscrowCheckpoint(
                EscrowCheckpointTestFixtures.FIRST_CHECKPOINT,
                EscrowCheckpointTestFixtures.SOURCE_LINEAGE,
                EscrowCheckpointTestFixtures.FIRST_LINEAGE, 1L,
                EscrowCheckpointTestFixtures.CREATED_AT, snapshots);
        byte original = checkpoint.snapshot(EscrowCheckpointStore.TRANSACTIONS)[0];

        snapshots.get(EscrowCheckpointStore.TRANSACTIONS)[0] ^= 1;
        byte[] returned = checkpoint.snapshot(EscrowCheckpointStore.TRANSACTIONS);
        returned[0] ^= 1;

        assertEquals(original, checkpoint.snapshot(EscrowCheckpointStore.TRANSACTIONS)[0]);
    }

    @Test
    void missingDuplicateAndUnknownStoresAreRejected() {
        EnumMap<EscrowCheckpointStore, byte[]> missing = new EnumMap<>(EscrowCheckpointStore.class);
        missing.putAll(EscrowCheckpointTestFixtures.snapshots("missing"));
        missing.remove(EscrowCheckpointStore.RUNTIME_METADATA);
        assertThrows(IllegalArgumentException.class, () -> new EscrowCheckpoint(
                EscrowCheckpointTestFixtures.FIRST_CHECKPOINT,
                EscrowCheckpointTestFixtures.SOURCE_LINEAGE,
                EscrowCheckpointTestFixtures.FIRST_LINEAGE, 1L,
                EscrowCheckpointTestFixtures.CREATED_AT, missing));

        byte[] duplicate = EscrowCheckpointCodec.encode(
                EscrowCheckpointTestFixtures.firstCheckpoint());
        ByteBuffer duplicateBuffer = ByteBuffer.wrap(duplicate);
        int firstStore = duplicateBuffer.getInt(80);
        int firstLength = duplicateBuffer.getInt(84);
        duplicateBuffer.putInt(80 + 8 + firstLength, firstStore);
        assertThrows(IllegalArgumentException.class,
                () -> EscrowCheckpointCodec.decode(duplicate));

        byte[] unknown = EscrowCheckpointCodec.encode(
                EscrowCheckpointTestFixtures.firstCheckpoint());
        ByteBuffer.wrap(unknown).putInt(80, 999);
        assertThrows(IllegalArgumentException.class,
                () -> EscrowCheckpointCodec.decode(unknown));
    }

    @Test
    void perStoreAndAggregateByteLimitsFailBeforeAllocationOrCopy() {
        byte[] invalidLength = EscrowCheckpointCodec.encode(
                EscrowCheckpointTestFixtures.firstCheckpoint());
        ByteBuffer.wrap(invalidLength).putInt(84, EscrowCheckpoint.MAX_STORE_BYTES + 1);
        assertThrows(IllegalArgumentException.class,
                () -> EscrowCheckpointCodec.decode(invalidLength));

        byte[] shared = new byte[40_000_000];
        EnumMap<EscrowCheckpointStore, byte[]> oversized =
                new EnumMap<>(EscrowCheckpointStore.class);
        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
            oversized.put(store, shared);
        }
        assertThrows(IllegalArgumentException.class, () -> new EscrowCheckpoint(
                EscrowCheckpointTestFixtures.FIRST_CHECKPOINT,
                EscrowCheckpointTestFixtures.SOURCE_LINEAGE,
                EscrowCheckpointTestFixtures.FIRST_LINEAGE, 1L,
                EscrowCheckpointTestFixtures.CREATED_AT, oversized));
    }

    @Test
    void corruptionTrailingBytesAndNewerSchemaAreRejected() {
        byte[] corrupted = EscrowCheckpointCodec.encode(
                EscrowCheckpointTestFixtures.firstCheckpoint());
        corrupted[0] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> EscrowCheckpointCodec.decode(corrupted));

        byte[] valid = EscrowCheckpointCodec.encode(
                EscrowCheckpointTestFixtures.firstCheckpoint());
        byte[] trailing = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class,
                () -> EscrowCheckpointCodec.decode(trailing));

        byte[] newer = EscrowCheckpointCodec.encode(
                EscrowCheckpointTestFixtures.firstCheckpoint());
        newer[4] = 0;
        newer[5] = (byte) (EscrowCheckpointCodec.FORMAT_VERSION + 1);
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> EscrowCheckpointCodec.decode(newer));
        assertTrue(failure.getCause().getMessage().contains("newer"));
    }

    @Test
    void legacySevenStoreCheckpointRestoresWithEmptyConservedStock()
            throws IOException {
        Map<EscrowCheckpointStore, byte[]> snapshots =
                EscrowCheckpointTestFixtures.snapshots("legacy");
        byte[] encoded = encodeLegacyCheckpoint(snapshots);

        EscrowCheckpoint decoded = EscrowCheckpointCodec.decode(encoded);

        assertEquals(EscrowCheckpointTestFixtures.FIRST_CHECKPOINT,
                decoded.checkpointId());
        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
            if (store != EscrowCheckpointStore.STOCK) {
                assertArrayEquals(snapshots.get(store),
                        decoded.snapshot(store));
            }
        }
        StockSavedData stock = StockSavedData.load(
                EscrowCheckpointComponentCodec.decode(
                        EscrowCheckpointStore.STOCK,
                        decoded.snapshot(EscrowCheckpointStore.STOCK),
                        EscrowCheckpoint.MAX_STORE_BYTES));
        assertTrue(stock.snapshot().listings().isEmpty());
        assertTrue(stock.conservation().conserved());
    }

    @Test
    void referenceCodecPreservesManifestAndRejectsNewerSchema() {
        byte[] digest = new byte[EscrowCheckpointManifest.SHA256_BYTES];
        digest[3] = 7;
        EscrowCheckpointManifest manifest = new EscrowCheckpointManifest(
                EscrowCheckpointTestFixtures.FIRST_CHECKPOINT,
                EscrowCheckpointTestFixtures.SOURCE_LINEAGE,
                EscrowCheckpointTestFixtures.FIRST_LINEAGE, 7L,
                EscrowCheckpointTestFixtures.CREATED_AT, 500L, digest);
        EscrowCheckpointReference reference = new EscrowCheckpointReference(manifest);
        byte[] encoded = EscrowCheckpointReferenceCodec.encode(reference);

        assertEquals(reference, EscrowCheckpointReferenceCodec.decode(encoded));
        assertEquals(EscrowCheckpointReferenceCodec.ENCODED_BYTES, encoded.length);
        byte[] returnedDigest = reference.checkpointSha256();
        returnedDigest[3] = 9;
        assertNotEquals(returnedDigest[3], reference.checkpointSha256()[3]);

        encoded[4] = 0;
        encoded[5] = 2;
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> EscrowCheckpointReferenceCodec.decode(encoded));
        assertTrue(failure.getMessage().contains("newer"));
    }

    private static byte[] encodeLegacyCheckpoint(
            Map<EscrowCheckpointStore, byte[]> snapshots
    ) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0x46534350);
        output.writeShort(1);
        output.writeShort(0);
        writeUuid(output, EscrowCheckpointTestFixtures.FIRST_CHECKPOINT);
        writeUuid(output, EscrowCheckpointTestFixtures.SOURCE_LINEAGE);
        writeUuid(output, EscrowCheckpointTestFixtures.FIRST_LINEAGE);
        output.writeLong(41L);
        output.writeLong(
                EscrowCheckpointTestFixtures.CREATED_AT.getEpochSecond());
        output.writeInt(EscrowCheckpointTestFixtures.CREATED_AT.getNano());
        output.writeInt(7);
        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
            if (store == EscrowCheckpointStore.STOCK) {
                continue;
            }
            byte[] snapshot = snapshots.get(store);
            output.writeInt(store.wireId());
            output.writeInt(snapshot.length);
            output.write(snapshot);
        }
        output.flush();
        return bytes.toByteArray();
    }

    private static void writeUuid(DataOutputStream output, java.util.UUID value)
            throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }
}
