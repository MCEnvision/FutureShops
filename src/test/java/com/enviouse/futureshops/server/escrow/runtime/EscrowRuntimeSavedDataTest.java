package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointManifest;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointReference;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EscrowRuntimeSavedDataTest {
    @Test
    void lineageAndCursorRoundTrip() {
        EscrowRuntimeSavedData data = new EscrowRuntimeSavedData();
        UUID lineage = UUID.randomUUID();
        data.establishLineage(lineage, 1L);
        data.advance(lineage, 2L);

        EscrowRuntimeSavedData loaded = EscrowRuntimeSavedData.load(data.save(new CompoundTag()));

        assertEquals(lineage, loaded.journalLineage().orElseThrow());
        assertEquals(2L, loaded.lastAppliedSequence());
    }

    @Test
    void cursorCannotSkip() {
        EscrowRuntimeSavedData data = new EscrowRuntimeSavedData();
        UUID lineage = UUID.randomUUID();
        data.establishLineage(lineage, 1L);

        assertThrows(IllegalStateException.class, () -> data.advance(lineage, 3L));
    }

    @Test
    void newerSchemaFailsClosed() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", Integer.MAX_VALUE);

        assertThrows(IllegalStateException.class, () -> EscrowRuntimeSavedData.load(tag));
    }

    @Test
    void malformedCurrentSchemaFailsClosed() {
        CompoundTag missingCursor = new CompoundTag();
        missingCursor.putInt("schemaVersion", 1);
        assertThrows(IllegalStateException.class,
                () -> EscrowRuntimeSavedData.load(missingCursor));

        CompoundTag malformedLineage = new CompoundTag();
        malformedLineage.putInt("schemaVersion", 1);
        malformedLineage.putLong("lastAppliedSequence", 1L);
        malformedLineage.putString("journalLineage", "not a uuid");
        assertThrows(IllegalStateException.class,
                () -> EscrowRuntimeSavedData.load(malformedLineage));

        CompoundTag negativeSchema = new CompoundTag();
        negativeSchema.putInt("schemaVersion", -1);
        assertThrows(IllegalStateException.class,
                () -> EscrowRuntimeSavedData.load(negativeSchema));
    }

    @Test
    void lineageCanOnlyBeEstablishedAtSequenceOne() {
        EscrowRuntimeSavedData data = new EscrowRuntimeSavedData();

        assertThrows(IllegalArgumentException.class,
                () -> data.establishLineage(UUID.randomUUID(), 2L));
    }

    @Test
    void trustedCheckpointCursorRoundTripsAndAdoptionIsIdempotent() {
        UUID sourceLineage = UUID.randomUUID();
        UUID replacementLineage = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        EscrowCheckpointReference reference = new EscrowCheckpointReference(
                new EscrowCheckpointManifest(
                        checkpointId,
                        sourceLineage,
                        replacementLineage,
                        17L,
                        Instant.parse("2026-07-17T12:00:00Z"),
                        128L,
                        new byte[EscrowCheckpointManifest.SHA256_BYTES]));
        EscrowRuntimeSavedData data = new EscrowRuntimeSavedData();
        data.establishLineage(sourceLineage, 1L);

        data.adoptTrustedCheckpoint(reference);
        data.adoptTrustedCheckpoint(reference);
        EscrowRuntimeSavedData loaded = EscrowRuntimeSavedData.load(
                data.save(new CompoundTag()));

        assertEquals(replacementLineage, loaded.journalLineage().orElseThrow());
        assertEquals(2L, loaded.lastAppliedSequence());
        assertEquals(checkpointId, loaded.checkpointId().orElseThrow());
        assertEquals(sourceLineage, loaded.checkpointSourceLineage().orElseThrow());
        assertEquals(17L, loaded.checkpointBaseSequence().orElseThrow());
    }

    @Test
    void incompleteCheckpointCursorMetadataFailsClosed() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", 2);
        tag.putUUID("journalLineage", UUID.randomUUID());
        tag.putLong("lastAppliedSequence", 2L);
        tag.putUUID("checkpointId", UUID.randomUUID());

        assertThrows(IllegalStateException.class,
                () -> EscrowRuntimeSavedData.load(tag));
    }
}
