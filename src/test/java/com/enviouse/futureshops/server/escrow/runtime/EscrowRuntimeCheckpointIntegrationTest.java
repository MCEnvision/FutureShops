package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.checkpoint.ActiveEscrowJournal;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointStore;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowPersistenceFaultInjector;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowPersistencePhase;
import com.enviouse.futureshops.server.escrow.checkpoint.TrustedEscrowCheckpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowRuntimeCheckpointIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00.123456789Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    Path temporaryDirectory;

    @Test
    void trustedCheckpointRestoresBeforeTailReplayAtSequenceThree() {
        Path journalPath = temporaryDirectory.resolve("journal.wal");
        UUID sourceLineage = UUID.randomUUID();
        UUID checkpointLineage = UUID.randomUUID();
        SnapshotHarness snapshots = new SnapshotHarness("trusted");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        EscrowRuntimeCoordinator first = coordinator(
                journalPath,
                cursor,
                (record, event) -> {
                },
                snapshots,
                ids(sourceLineage, checkpointLineage, UUID.randomUUID()),
                ids(UUID.randomUUID(), UUID.randomUUID()),
                EscrowPersistenceFaultInjector.NONE);

        assertEquals(EscrowRuntimeState.READY, first.start());
        ActiveEscrowJournal active = first.checkpointNow();
        EscrowJournalEvent tail = mutation(7);
        first.commit(UUID.randomUUID(), tail);
        snapshots.failCapture = true;
        assertThrows(EscrowRuntimeException.class, first::checkpointNow);
        assertEquals(EscrowRuntimeState.MAINTENANCE, first.state());
        first.close();
        first.close();
        assertEquals(EscrowRuntimeState.MAINTENANCE, first.state());

        SnapshotHarness restored = new SnapshotHarness("stale");
        List<String> recoveryOrder = restored.restoreEvents;
        EscrowRuntimeCoordinator recovered = new EscrowRuntimeCoordinator(
                journalPath,
                cursor,
                (record, event) -> {
                    assertEquals(3L, record.sequence());
                    assertEquals(tail, event);
                    recoveryOrder.add("tail");
                },
                restored);

        assertEquals(EscrowRuntimeState.READY, recovered.start(10));
        assertEquals(List.of("prepare", "apply", "tail"), recoveryOrder);
        assertEquals(EscrowCheckpointStore.values().length, restored.validatedStores);
        assertEquals(1, restored.appliedRestores);
        assertEquals(active.lineage().lineageId(), cursor.journalLineage().orElseThrow());
        assertEquals(3L, cursor.lastAppliedSequence());
        assertSnapshotsEqual(snapshots.current, restored.current);
        recovered.close();
    }

    @Test
    void cleanShutdownCheckpointsLegacyJournalAndCloseIsIdempotent() throws Exception {
        Path journalPath = temporaryDirectory.resolve("journal.wal");
        SnapshotHarness snapshots = new SnapshotHarness("shutdown");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(
                journalPath, cursor, (record, event) -> {
                }, snapshots);

        assertEquals(EscrowRuntimeState.READY, coordinator.start());
        coordinator.commit(UUID.randomUUID(), mutation(1));
        coordinator.close();

        assertEquals(EscrowRuntimeState.STOPPED, coordinator.state());
        assertFalse(Files.exists(journalPath));
        assertTrue(Files.isRegularFile(
                temporaryDirectory.resolve("journal.legacy.wal")));
        assertTrue(Files.isRegularFile(
                temporaryDirectory.resolve("journals").resolve("journal.active")));
        assertTrue(cursor.checkpointId().isPresent());
        assertEquals(2L, cursor.lastAppliedSequence());
        long checkpointFiles = countFiles(temporaryDirectory.resolve("checkpoints"));
        coordinator.close();
        assertEquals(checkpointFiles,
                countFiles(temporaryDirectory.resolve("checkpoints")));

        SnapshotHarness restored = new SnapshotHarness("stale");
        EscrowRuntimeCoordinator restarted = new EscrowRuntimeCoordinator(
                journalPath, cursor, (record, event) -> {
                }, restored);
        assertEquals(EscrowRuntimeState.READY, restarted.start());
        assertEquals(2L, cursor.lastAppliedSequence());
        assertEquals(1, restored.appliedRestores);
        restarted.close();
    }

    @Test
    void repeatedRotationsRetainOnlyCurrentAndPreviousPairsByDefault() throws Exception {
        Path journalPath = temporaryDirectory.resolve("journal.wal");
        SnapshotHarness snapshots = new SnapshotHarness("rotation.0");
        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(
                journalPath, new EscrowRuntimeSavedData(), (record, event) -> {
                }, snapshots);

        assertEquals(EscrowRuntimeState.READY, coordinator.start());
        for (int rotation = 1; rotation <= 6; rotation++) {
            snapshots.setSnapshots("rotation." + rotation);
            coordinator.checkpointNow();
            assertTrue(countMatching(
                    temporaryDirectory.resolve("checkpoints"),
                    "checkpoint-", ".fscp") <= 2L);
            assertTrue(countMatching(
                    temporaryDirectory.resolve("journals"),
                    "journal-", ".wal") <= 2L);
        }
        assertEquals(2L, countMatching(
                temporaryDirectory.resolve("checkpoints"),
                "checkpoint-", ".fscp"));
        assertEquals(2L, countMatching(
                temporaryDirectory.resolve("journals"),
                "journal-", ".wal"));

        coordinator.close();
        assertEquals(2L, countMatching(
                temporaryDirectory.resolve("checkpoints"),
                "checkpoint-", ".fscp"));
        assertEquals(2L, countMatching(
                temporaryDirectory.resolve("journals"),
                "journal-", ".wal"));
        assertFalse(Files.exists(journalPath));
        assertTrue(Files.isRegularFile(
                temporaryDirectory.resolve("journal.legacy.wal")));
    }

    @Test
    void legacyJournalRemainsBeforeAdoptionBoundaryAndArchivesAfterRestart() {
        Path journalPath = temporaryDirectory.resolve("journal.wal");
        SnapshotHarness snapshots = new SnapshotHarness("legacy.boundary");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        EscrowPersistenceFaultInjector faultInjector = phase -> {
            if (phase == EscrowPersistencePhase.JOURNAL_AFTER_POINTER_MOVE) {
                throw new IOException("Injected interruption before cursor adoption");
            }
        };
        EscrowRuntimeCoordinator interrupted = coordinator(
                journalPath,
                cursor,
                (record, event) -> {
                },
                snapshots,
                ids(UUID.randomUUID(), UUID.randomUUID()),
                ids(UUID.randomUUID()),
                faultInjector);

        assertEquals(EscrowRuntimeState.READY, interrupted.start());
        assertTrue(Files.isRegularFile(journalPath));
        assertThrows(EscrowRuntimeException.class, interrupted::checkpointNow);
        assertEquals(EscrowRuntimeState.MAINTENANCE, interrupted.state());
        assertTrue(Files.isRegularFile(journalPath));
        assertFalse(Files.exists(temporaryDirectory.resolve("journal.legacy.wal")));
        interrupted.close();

        SnapshotHarness restored = new SnapshotHarness("stale");
        EscrowRuntimeCoordinator recovered = new EscrowRuntimeCoordinator(
                journalPath, cursor, (record, event) -> {
                }, restored);
        assertEquals(EscrowRuntimeState.READY, recovered.start());
        assertFalse(Files.exists(journalPath));
        assertTrue(Files.isRegularFile(
                temporaryDirectory.resolve("journal.legacy.wal")));
        assertEquals(1, restored.appliedRestores);
        recovered.close();
    }

    @Test
    void corruptActiveCheckpointFailsClosedBeforeRestore() throws Exception {
        Path journalPath = temporaryDirectory.resolve("journal.wal");
        SnapshotHarness snapshots = new SnapshotHarness("corrupt");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        EscrowRuntimeCoordinator first = new EscrowRuntimeCoordinator(
                journalPath, cursor, (record, event) -> {
                }, snapshots);
        assertEquals(EscrowRuntimeState.READY, first.start());
        ActiveEscrowJournal active = first.checkpointNow();
        snapshots.failCapture = true;
        assertThrows(EscrowRuntimeException.class, first::checkpointNow);

        Path checkpointPath = active.trustedCheckpoint().generationPath();
        byte[] corrupt = Files.readAllBytes(checkpointPath);
        corrupt[corrupt.length - 1] ^= 1;
        Files.write(checkpointPath, corrupt);

        SnapshotHarness restore = new SnapshotHarness("untouched");
        EscrowRuntimeCoordinator restarted = new EscrowRuntimeCoordinator(
                journalPath, cursor, (record, event) -> {
                }, restore);
        assertEquals(EscrowRuntimeState.MAINTENANCE, restarted.start());
        assertEquals(0, restore.validatedStores);
        assertEquals(0, restore.appliedRestores);
        assertTrue(restarted.failure().isPresent());
        restarted.close();
    }

    @Test
    void restorePreparationValidatesEveryStoreBeforeAnyMutation() {
        Path journalPath = temporaryDirectory.resolve("journal.wal");
        SnapshotHarness snapshots = new SnapshotHarness("validation");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        EscrowRuntimeCoordinator first = new EscrowRuntimeCoordinator(
                journalPath, cursor, (record, event) -> {
                }, snapshots);
        assertEquals(EscrowRuntimeState.READY, first.start());
        first.checkpointNow();
        snapshots.failCapture = true;
        assertThrows(EscrowRuntimeException.class, first::checkpointNow);

        AtomicInteger validated = new AtomicInteger();
        AtomicInteger mutations = new AtomicInteger();
        EscrowCheckpointSnapshotBundle rejectingRestore =
                new EscrowCheckpointSnapshotBundle() {
                    @Override
                    public Map<EscrowCheckpointStore, byte[]> captureSnapshots() {
                        return completeSnapshots("unused");
                    }

                    @Override
                    public EscrowPreparedCheckpointRestore prepareTrustedRestore(
                            TrustedEscrowCheckpoint trustedCheckpoint) {
                        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
                            byte[] snapshot = trustedCheckpoint.checkpoint().snapshot(store);
                            assertTrue(snapshot.length > 0);
                            validated.incrementAndGet();
                        }
                        throw new IllegalArgumentException("Rejected after complete validation");
                    }
                };
        EscrowRuntimeCoordinator restarted = new EscrowRuntimeCoordinator(
                journalPath, cursor, (record, event) -> mutations.incrementAndGet(),
                rejectingRestore);

        assertEquals(EscrowRuntimeState.MAINTENANCE, restarted.start());
        assertEquals(EscrowCheckpointStore.values().length, validated.get());
        assertEquals(0, mutations.get());
        restarted.close();
    }

    @Test
    void checkpointGenerationMoveCrashKeepsPriorActivePair() throws Exception {
        verifyRotationCrash(EscrowPersistencePhase.CHECKPOINT_AFTER_GENERATION_MOVE, false);
    }

    @Test
    void pointerMoveCrashKeepsPriorPairAndNewPointerRecoverable() throws Exception {
        verifyRotationCrash(EscrowPersistencePhase.JOURNAL_AFTER_POINTER_MOVE, true);
    }

    private void verifyRotationCrash(EscrowPersistencePhase failurePhase,
                                     boolean replacementBecomesActive) throws Exception {
        Path root = temporaryDirectory.resolve(failurePhase.name());
        Path journalPath = root.resolve("journal.wal");
        UUID sourceLineage = UUID.randomUUID();
        UUID firstLineage = UUID.randomUUID();
        UUID secondLineage = UUID.randomUUID();
        AtomicBoolean armed = new AtomicBoolean();
        AtomicBoolean interrupted = new AtomicBoolean();
        EscrowPersistenceFaultInjector faultInjector = phase -> {
            if (armed.get() && phase == failurePhase
                    && interrupted.compareAndSet(false, true)) {
                throw new IOException("Injected coordinator rotation interruption");
            }
        };
        SnapshotHarness snapshots = new SnapshotHarness("first");
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        EscrowRuntimeCoordinator first = coordinator(
                journalPath,
                cursor,
                (record, event) -> {
                },
                snapshots,
                ids(sourceLineage, firstLineage, secondLineage),
                ids(UUID.randomUUID(), UUID.randomUUID()),
                faultInjector);

        assertEquals(EscrowRuntimeState.READY, first.start());
        ActiveEscrowJournal prior = first.checkpointNow();
        first.commit(UUID.randomUUID(), mutation(2));
        snapshots.setSnapshots("second");
        armed.set(true);

        assertThrows(EscrowRuntimeException.class, first::checkpointNow);
        assertTrue(interrupted.get());
        assertEquals(EscrowRuntimeState.MAINTENANCE, first.state());
        assertTrue(Files.isRegularFile(prior.journalPath()));
        assertTrue(Files.isRegularFile(prior.trustedCheckpoint().generationPath()));
        first.close();
        first.close();
        assertEquals(EscrowRuntimeState.MAINTENANCE, first.state());

        SnapshotHarness restored = new SnapshotHarness("stale");
        AtomicInteger replayedTail = new AtomicInteger();
        EscrowRuntimeCoordinator recovered = new EscrowRuntimeCoordinator(
                journalPath, cursor,
                (record, event) -> replayedTail.incrementAndGet(), restored);
        assertEquals(EscrowRuntimeState.READY, recovered.start(10));
        if (replacementBecomesActive) {
            assertEquals(secondLineage, recovered.lineageId().orElseThrow());
            assertEquals(0, replayedTail.get());
            assertSnapshotsEqual(snapshots.current, restored.current);
        } else {
            assertEquals(firstLineage, recovered.lineageId().orElseThrow());
            assertEquals(1, replayedTail.get());
        }
        assertTrue(Files.isRegularFile(prior.journalPath()));
        assertTrue(Files.isRegularFile(prior.trustedCheckpoint().generationPath()));
        recovered.close();
    }

    private static EscrowRuntimeCoordinator coordinator(
            Path journalPath,
            EscrowRuntimeSavedData cursor,
            EscrowMutationApplier applier,
            EscrowCheckpointSnapshotBundle snapshots,
            Supplier<UUID> lineageIds,
            Supplier<UUID> checkpointIds,
            EscrowPersistenceFaultInjector faultInjector) {
        return new EscrowRuntimeCoordinator(
                journalPath,
                cursor,
                applier,
                () -> false,
                CLOCK,
                lineageIds,
                snapshots,
                checkpointIds,
                faultInjector);
    }

    private static EscrowJournalEvent mutation(int marker) {
        return new EscrowJournalEvent(
                EscrowJournalEventType.CLAIM_CREATE, new byte[]{(byte) marker});
    }

    private static Supplier<UUID> ids(UUID... values) {
        ArrayDeque<UUID> ids = new ArrayDeque<>(List.of(values));
        return ids::removeFirst;
    }

    private static long countFiles(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static long countMatching(Path directory, String prefix, String suffix)
            throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(prefix) && name.endsWith(suffix))
                    .count();
        }
    }

    private static Map<EscrowCheckpointStore, byte[]> completeSnapshots(String prefix) {
        EnumMap<EscrowCheckpointStore, byte[]> snapshots =
                new EnumMap<>(EscrowCheckpointStore.class);
        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
            snapshots.put(store, (prefix + "." + store.name())
                    .getBytes(StandardCharsets.UTF_8));
        }
        return snapshots;
    }

    private static void assertSnapshotsEqual(Map<EscrowCheckpointStore, byte[]> expected,
                                             Map<EscrowCheckpointStore, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
            assertArrayEquals(expected.get(store), actual.get(store));
        }
    }

    private static final class SnapshotHarness implements EscrowCheckpointSnapshotBundle {
        private final EnumMap<EscrowCheckpointStore, byte[]> current =
                new EnumMap<>(EscrowCheckpointStore.class);
        private final List<String> restoreEvents = new ArrayList<>();
        private boolean failCapture;
        private int validatedStores;
        private int appliedRestores;

        private SnapshotHarness(String prefix) {
            setSnapshots(prefix);
        }

        private void setSnapshots(String prefix) {
            current.clear();
            completeSnapshots(prefix).forEach(
                    (store, bytes) -> current.put(store, bytes.clone()));
        }

        @Override
        public Map<EscrowCheckpointStore, byte[]> captureSnapshots() {
            if (failCapture) {
                throw new IllegalStateException("Injected snapshot capture failure");
            }
            EnumMap<EscrowCheckpointStore, byte[]> captured =
                    new EnumMap<>(EscrowCheckpointStore.class);
            current.forEach((store, bytes) -> captured.put(store, bytes.clone()));
            return captured;
        }

        @Override
        public EscrowPreparedCheckpointRestore prepareTrustedRestore(
                TrustedEscrowCheckpoint trustedCheckpoint) {
            EnumMap<EscrowCheckpointStore, byte[]> prepared =
                    new EnumMap<>(EscrowCheckpointStore.class);
            for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
                byte[] snapshot = trustedCheckpoint.checkpoint().snapshot(store);
                assertTrue(snapshot.length > 0);
                prepared.put(store, snapshot);
                validatedStores++;
            }
            restoreEvents.add("prepare");
            return () -> {
                current.clear();
                prepared.forEach((store, bytes) -> current.put(store, bytes.clone()));
                appliedRestores++;
                restoreEvents.add("apply");
            };
        }
    }
}
