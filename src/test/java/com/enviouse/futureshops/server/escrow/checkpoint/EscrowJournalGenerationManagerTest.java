package com.enviouse.futureshops.server.escrow.checkpoint;

import com.enviouse.futureshops.server.escrow.journal.JournalReplayBatch;
import com.enviouse.futureshops.server.escrow.journal.WriteAheadJournal;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEvent;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventCodec;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventType;
import com.enviouse.futureshops.server.escrow.runtime.JournalLineage;
import com.enviouse.futureshops.server.escrow.runtime.JournalLineageCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowJournalGenerationManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void stagedWalBeginsWithLineageAndExactCheckpointReference() throws Exception {
        Harness harness = new Harness(temporaryDirectory);
        EscrowCheckpointManifest manifest = harness.checkpoints.writeGeneration(
                EscrowCheckpointTestFixtures.firstCheckpoint());
        EscrowJournalGeneration generation = harness.journals.stage(
                manifest, Instant.ofEpochSecond(1_900_000_001L, 4));

        try (WriteAheadJournal journal = WriteAheadJournal.open(generation.journalPath())) {
            assertEquals(2L, journal.recovery().recordCount());
            JournalReplayBatch records = journal.replayBatch(
                    0L, 1L, 2, WriteAheadJournal.MAX_REPLAY_BATCH_BYTES);
            EscrowJournalEvent first = EscrowJournalEventCodec.decode(
                    records.records().get(0).payload());
            assertEquals(EscrowJournalEventType.JOURNAL_LINEAGE, first.type());
            JournalLineage lineage = JournalLineageCodec.decode(first.body());
            assertEquals(manifest.replacementJournalLineageId(), lineage.lineageId());

            EscrowCheckpointReference second = EscrowCheckpointReferenceCodec.decode(
                    records.records().get(1).payload());
            assertEquals(new EscrowCheckpointReference(manifest), second);
            assertEquals(manifest.checkpointId(), records.records().get(1).transactionId());
            assertEquals(EscrowCheckpointStepIds.forReference(second),
                    records.records().get(1).stepId());
        }

        ActiveEscrowJournal active = harness.journals.activate(generation);
        assertEquals(manifest.replacementJournalLineageId(), active.lineage().lineageId());
        assertEquals(EscrowCheckpointTestFixtures.firstCheckpoint(),
                active.trustedCheckpoint().checkpoint());
        assertEquals(active, harness.journals.loadActive().orElseThrow());
    }

    @Test
    void orphanCheckpointAndInterruptedGenerationMoveCannotReplaceActiveWal() throws Exception {
        Harness harness = new Harness(temporaryDirectory);
        ActiveEscrowJournal first = harness.activate(
                EscrowCheckpointTestFixtures.firstCheckpoint());
        EscrowCheckpoint second = EscrowCheckpointTestFixtures.secondCheckpoint();
        EscrowCheckpointManifest secondManifest = harness.checkpoints.writeGeneration(second);

        assertEquals(first.lineage(), harness.journals.loadActive().orElseThrow().lineage());

        EscrowJournalGenerationManager interrupted = new EscrowJournalGenerationManager(
                harness.journalDirectory, harness.checkpoints,
                failAt(EscrowPersistencePhase.JOURNAL_AFTER_GENERATION_MOVE));
        assertThrows(IOException.class,
                () -> interrupted.stage(secondManifest, EscrowCheckpointTestFixtures.CREATED_AT));

        ActiveEscrowJournal stillActive = harness.journals.loadActive().orElseThrow();
        assertEquals(first.lineage(), stillActive.lineage());
        assertTrue(Files.exists(harness.journals.generationPath(
                second.replacementJournalLineageId())));
        assertTrue(Files.exists(first.journalPath()));
    }

    @Test
    void pointerTempInterruptionKeepsOldPointerAndBothWalGenerations() throws Exception {
        Harness harness = new Harness(temporaryDirectory);
        ActiveEscrowJournal first = harness.activate(
                EscrowCheckpointTestFixtures.firstCheckpoint());
        EscrowCheckpointManifest secondManifest = harness.checkpoints.writeGeneration(
                EscrowCheckpointTestFixtures.secondCheckpoint());
        EscrowJournalGeneration second = harness.journals.stage(
                secondManifest, EscrowCheckpointTestFixtures.CREATED_AT.plusSeconds(1));
        EscrowJournalGenerationManager interrupted = new EscrowJournalGenerationManager(
                harness.journalDirectory, harness.checkpoints,
                failAt(EscrowPersistencePhase.JOURNAL_AFTER_POINTER_TEMP_FORCE));

        assertThrows(IOException.class, () -> interrupted.activate(second));

        assertEquals(first.lineage(), harness.journals.loadActive().orElseThrow().lineage());
        assertTrue(Files.exists(first.journalPath()));
        assertTrue(Files.exists(second.journalPath()));
    }

    @Test
    void activatedReferenceIsValidatedBeforeOldWalCleanup() throws Exception {
        Harness harness = new Harness(temporaryDirectory);
        ActiveEscrowJournal first = harness.activate(
                EscrowCheckpointTestFixtures.firstCheckpoint());
        EscrowCheckpointManifest secondManifest = harness.checkpoints.writeGeneration(
                EscrowCheckpointTestFixtures.secondCheckpoint());
        EscrowJournalGeneration second = harness.journals.stage(
                secondManifest, EscrowCheckpointTestFixtures.CREATED_AT.plusSeconds(2));
        EscrowJournalGenerationManager interrupted = new EscrowJournalGenerationManager(
                harness.journalDirectory, harness.checkpoints,
                failAt(EscrowPersistencePhase.JOURNAL_BEFORE_CLEANUP));

        assertThrows(IOException.class, () -> interrupted.activate(second));

        ActiveEscrowJournal active = harness.journals.loadActive().orElseThrow();
        assertEquals(second.lineage(), active.lineage());
        assertEquals(second.checkpointReference(), active.checkpointReference());
        assertTrue(Files.exists(first.journalPath()));
        assertTrue(Files.exists(second.journalPath()));

        ActiveEscrowJournal recovered = harness.journals.activate(second);
        assertEquals(second.lineage(), recovered.lineage());
        assertFalse(Files.exists(first.journalPath()));
        assertTrue(Files.exists(second.journalPath()));
    }

    @Test
    void interruptionAfterPointerMoveSelectsOnlyTheNewValidPairAndRetainsOldWal() throws Exception {
        Harness harness = new Harness(temporaryDirectory);
        ActiveEscrowJournal first = harness.activate(
                EscrowCheckpointTestFixtures.firstCheckpoint());
        EscrowCheckpointManifest secondManifest = harness.checkpoints.writeGeneration(
                EscrowCheckpointTestFixtures.secondCheckpoint());
        EscrowJournalGeneration second = harness.journals.stage(
                secondManifest, EscrowCheckpointTestFixtures.CREATED_AT.plusSeconds(3));
        EscrowJournalGenerationManager interrupted = new EscrowJournalGenerationManager(
                harness.journalDirectory, harness.checkpoints,
                failAt(EscrowPersistencePhase.JOURNAL_AFTER_POINTER_MOVE));

        assertThrows(IOException.class, () -> interrupted.activate(second));

        ActiveEscrowJournal active = harness.journals.loadActive().orElseThrow();
        assertEquals(second.lineage(), active.lineage());
        assertEquals(second.checkpointReference(), active.checkpointReference());
        assertTrue(Files.exists(first.journalPath()));
        assertTrue(Files.exists(second.journalPath()));
    }

    @Test
    void corruptedPointerAndMissingReferencedCheckpointFailClosed() throws Exception {
        Harness harness = new Harness(temporaryDirectory);
        ActiveEscrowJournal active = harness.activate(
                EscrowCheckpointTestFixtures.firstCheckpoint());
        byte[] pointer = Files.readAllBytes(harness.journals.activePointerPath());
        pointer[pointer.length - 1] ^= 1;
        Files.write(harness.journals.activePointerPath(), pointer);
        assertThrows(EscrowCheckpointException.class, harness.journals::loadActive);

        Files.write(harness.journals.activePointerPath(),
                EscrowJournalActivePointerCodec.encode(new EscrowJournalActivePointer(
                        active.lineage().lineageId(), active.checkpointReference())));
        Files.delete(active.trustedCheckpoint().generationPath());
        assertThrows(EscrowCheckpointException.class, harness.journals::loadActive);
    }

    @Test
    void pointerCodecRejectsChecksumCorruptionAndNewerSchemaWithValidChecksum() throws Exception {
        Harness harness = new Harness(temporaryDirectory);
        ActiveEscrowJournal active = harness.activate(
                EscrowCheckpointTestFixtures.firstCheckpoint());
        EscrowJournalActivePointer pointer = new EscrowJournalActivePointer(
                active.lineage().lineageId(), active.checkpointReference());
        byte[] encoded = EscrowJournalActivePointerCodec.encode(pointer);
        encoded[8] ^= 1;
        assertThrows(IllegalArgumentException.class,
                () -> EscrowJournalActivePointerCodec.decode(encoded));

        byte[] newerReference = EscrowCheckpointReferenceCodec.encode(
                active.checkpointReference());
        newerReference[4] = 0;
        newerReference[5] = 2;
        assertThrows(IllegalArgumentException.class,
                () -> EscrowCheckpointReferenceCodec.decode(newerReference));
    }

    private static EscrowPersistenceFaultInjector failAt(EscrowPersistencePhase target) {
        return phase -> {
            if (phase == target) {
                throw new IOException("Injected persistence interruption");
            }
        };
    }

    private static final class Harness {
        private final Path journalDirectory;
        private final EscrowCheckpointManager checkpoints;
        private final EscrowJournalGenerationManager journals;

        private Harness(Path root) throws IOException {
            Path checkpointDirectory = root.resolve("checkpoints");
            journalDirectory = root.resolve("journals");
            checkpoints = new EscrowCheckpointManager(checkpointDirectory);
            journals = new EscrowJournalGenerationManager(journalDirectory, checkpoints);
        }

        private ActiveEscrowJournal activate(EscrowCheckpoint checkpoint) throws IOException {
            EscrowCheckpointManifest manifest = checkpoints.writeGeneration(checkpoint);
            EscrowJournalGeneration generation = journals.stage(
                    manifest, checkpoint.createdAt().plusSeconds(1));
            return journals.activate(generation);
        }
    }
}
