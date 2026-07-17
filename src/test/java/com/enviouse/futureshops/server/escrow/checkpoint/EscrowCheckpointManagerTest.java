package com.enviouse.futureshops.server.escrow.checkpoint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowCheckpointManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void generationIsImmutableIdempotentAndVerifiedByExactSha256Manifest() throws Exception {
        EscrowCheckpointManager manager = new EscrowCheckpointManager(temporaryDirectory);
        EscrowCheckpoint checkpoint = EscrowCheckpointTestFixtures.firstCheckpoint();
        EscrowCheckpointManifest manifest = manager.writeGeneration(checkpoint);

        assertEquals(manifest, manager.writeGeneration(checkpoint));
        assertEquals(checkpoint, manager.readVerified(manifest));
        assertTrue(Files.exists(manager.generationPath(checkpoint.checkpointId())));

        EscrowCheckpoint conflicting = EscrowCheckpointTestFixtures.checkpoint(
                checkpoint.checkpointId(), checkpoint.sourceJournalLineageId(),
                checkpoint.replacementJournalLineageId(), checkpoint.baseJournalSequence(),
                "conflicting");
        assertThrows(EscrowCheckpointException.class,
                () -> manager.writeGeneration(conflicting));

        byte[] wrongDigest = manifest.sha256();
        wrongDigest[0] ^= 1;
        EscrowCheckpointManifest wrongManifest = new EscrowCheckpointManifest(
                manifest.checkpointId(), manifest.sourceJournalLineageId(),
                manifest.replacementJournalLineageId(), manifest.baseJournalSequence(),
                manifest.createdAt(), manifest.fileBytes(), wrongDigest);
        assertThrows(EscrowCheckpointException.class,
                () -> manager.readVerified(wrongManifest));
        assertArrayEquals(manifest.sha256(), manager.writeGeneration(checkpoint).sha256());
    }

    @Test
    void payloadCorruptionAndLineageMismatchFailClosed() throws Exception {
        EscrowCheckpointManager manager = new EscrowCheckpointManager(temporaryDirectory);
        EscrowCheckpoint checkpoint = EscrowCheckpointTestFixtures.firstCheckpoint();
        EscrowCheckpointManifest manifest = manager.writeGeneration(checkpoint);
        Path generation = manager.generationPath(checkpoint.checkpointId());
        byte[] encoded = Files.readAllBytes(generation);
        encoded[encoded.length - 1] ^= 1;
        Files.write(generation, encoded);

        assertThrows(EscrowCheckpointException.class,
                () -> manager.readVerified(manifest));

        Path otherDirectory = temporaryDirectory.resolve("other");
        EscrowCheckpointManager cleanManager = new EscrowCheckpointManager(otherDirectory);
        EscrowCheckpointManifest cleanManifest = cleanManager.writeGeneration(checkpoint);
        EscrowCheckpointReference reference = new EscrowCheckpointReference(cleanManifest);
        assertThrows(EscrowCheckpointException.class,
                () -> cleanManager.loadTrusted(
                        reference, EscrowCheckpointTestFixtures.SECOND_LINEAGE));
    }

    @Test
    void interruptedWriteLeavesOnlyAnIgnoredTemporaryFile() throws Exception {
        EscrowCheckpoint checkpoint = EscrowCheckpointTestFixtures.firstCheckpoint();
        EscrowCheckpointManager interrupted = new EscrowCheckpointManager(
                temporaryDirectory, failAt(EscrowPersistencePhase.CHECKPOINT_AFTER_TEMP_WRITE));

        assertThrows(IOException.class, () -> interrupted.writeGeneration(checkpoint));
        assertFalse(Files.exists(interrupted.generationPath(checkpoint.checkpointId())));
        assertTrue(hasTemporaryCheckpoint(temporaryDirectory));

        EscrowCheckpointManager recovered = new EscrowCheckpointManager(temporaryDirectory);
        recovered.cleanupOrphans(Set.of());
        assertFalse(hasTemporaryCheckpoint(temporaryDirectory));
        EscrowCheckpointManifest manifest = recovered.writeGeneration(checkpoint);
        assertEquals(checkpoint, recovered.readVerified(manifest));
    }

    @Test
    void interruptionAfterGenerationMoveCanBeRetriedWithoutOverwrite() throws Exception {
        EscrowCheckpoint checkpoint = EscrowCheckpointTestFixtures.firstCheckpoint();
        EscrowCheckpointManager interrupted = new EscrowCheckpointManager(
                temporaryDirectory,
                failAt(EscrowPersistencePhase.CHECKPOINT_AFTER_GENERATION_MOVE));

        assertThrows(IOException.class, () -> interrupted.writeGeneration(checkpoint));
        assertTrue(Files.exists(interrupted.generationPath(checkpoint.checkpointId())));

        EscrowCheckpointManager recovered = new EscrowCheckpointManager(temporaryDirectory);
        EscrowCheckpointManifest manifest = recovered.writeGeneration(checkpoint);
        assertEquals(checkpoint, recovered.readVerified(manifest));
    }

    @Test
    void cleanupDeletesOnlyUnretainedGenerationsAndKnownTemporaryFiles() throws Exception {
        EscrowCheckpointManager manager = new EscrowCheckpointManager(temporaryDirectory);
        EscrowCheckpoint first = EscrowCheckpointTestFixtures.firstCheckpoint();
        EscrowCheckpoint second = EscrowCheckpointTestFixtures.secondCheckpoint();
        manager.writeGeneration(first);
        manager.writeGeneration(second);
        Path unrelated = temporaryDirectory.resolve("checkpoint-not-owned.fscp");
        Files.writeString(unrelated, "keep");

        manager.cleanupOrphans(Set.of(second.checkpointId()));

        assertFalse(Files.exists(manager.generationPath(first.checkpointId())));
        assertTrue(Files.exists(manager.generationPath(second.checkpointId())));
        assertTrue(Files.exists(unrelated));
    }

    private static EscrowPersistenceFaultInjector failAt(EscrowPersistencePhase target) {
        return phase -> {
            if (phase == target) {
                throw new IOException("Injected persistence interruption");
            }
        };
    }

    private static boolean hasTemporaryCheckpoint(Path directory) throws IOException {
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            return files.anyMatch(path -> path.getFileName().toString().startsWith(".checkpoint-"));
        }
    }
}
