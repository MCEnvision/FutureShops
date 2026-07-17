package com.enviouse.futureshops.server.escrow.checkpoint;

import com.enviouse.futureshops.server.escrow.journal.JournalRecord;
import com.enviouse.futureshops.server.escrow.journal.JournalReplayBatch;
import com.enviouse.futureshops.server.escrow.journal.JournalScanResult;
import com.enviouse.futureshops.server.escrow.journal.WriteAheadJournal;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEvent;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventCodec;
import com.enviouse.futureshops.server.escrow.runtime.EscrowJournalEventType;
import com.enviouse.futureshops.server.escrow.runtime.EscrowStepIds;
import com.enviouse.futureshops.server.escrow.runtime.JournalLineage;
import com.enviouse.futureshops.server.escrow.runtime.JournalLineageCodec;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class EscrowJournalGenerationManager {
    public static final String ACTIVE_POINTER_FILE = "journal.active";

    private static final Pattern GENERATION_FILE = Pattern.compile(
            "journal-([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.wal");

    private final Path directory;
    private final EscrowCheckpointManager checkpointManager;
    private final EscrowPersistenceFaultInjector faultInjector;

    public EscrowJournalGenerationManager(Path directory,
                                          EscrowCheckpointManager checkpointManager)
            throws IOException {
        this(directory, checkpointManager, EscrowPersistenceFaultInjector.NONE);
    }

    public EscrowJournalGenerationManager(Path directory,
                                          EscrowCheckpointManager checkpointManager,
                                          EscrowPersistenceFaultInjector faultInjector)
            throws IOException {
        this.directory = Objects.requireNonNull(directory, "directory")
                .toAbsolutePath().normalize();
        this.checkpointManager = Objects.requireNonNull(checkpointManager, "checkpointManager");
        this.faultInjector = Objects.requireNonNull(faultInjector, "faultInjector");
        Files.createDirectories(this.directory);
        if (!Files.isDirectory(this.directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Escrow journal directory is invalid");
        }
    }

    public EscrowJournalGeneration stage(EscrowCheckpointManifest manifest,
                                         Instant journalCreatedAt) throws IOException {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(journalCreatedAt, "journalCreatedAt");
        EscrowCheckpointReference reference = new EscrowCheckpointReference(manifest);
        checkpointManager.loadTrusted(reference, reference.replacementJournalLineageId());
        Path destination = generationPath(reference.replacementJournalLineageId());
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            ActiveEscrowJournal validated = validateGeneration(destination, reference, false);
            return new EscrowJournalGeneration(
                    destination, validated.lineage(), validated.checkpointReference());
        }

        JournalLineage lineage = new JournalLineage(
                reference.replacementJournalLineageId(), journalCreatedAt);
        EscrowJournalEvent lineageEvent = new EscrowJournalEvent(
                EscrowJournalEventType.JOURNAL_LINEAGE, JournalLineageCodec.encode(lineage));
        byte[] lineagePayload = EscrowJournalEventCodec.encode(lineageEvent);
        byte[] referencePayload = EscrowCheckpointReferenceCodec.encode(reference);
        Path temporary = directory.resolve(
                ".journal-" + lineage.lineageId() + "-" + UUID.randomUUID() + ".tmp");
        try (WriteAheadJournal journal = WriteAheadJournal.open(temporary)) {
            journal.append(
                    lineage.lineageId(), EscrowStepIds.forEvent(lineage.lineageId(), lineageEvent),
                    lineagePayload);
            journal.append(
                    reference.checkpointId(), EscrowCheckpointStepIds.forReference(reference),
                    referencePayload);
        }
        faultInjector.at(EscrowPersistencePhase.JOURNAL_AFTER_TEMP_FORCE);
        validateGeneration(temporary, reference, true);

        try {
            EscrowDurableFiles.moveNewAtomically(temporary, destination);
        } catch (FileAlreadyExistsException exception) {
            ActiveEscrowJournal validated = validateGeneration(destination, reference, false);
            Files.deleteIfExists(temporary);
            return new EscrowJournalGeneration(
                    destination, validated.lineage(), validated.checkpointReference());
        }
        faultInjector.at(EscrowPersistencePhase.JOURNAL_AFTER_GENERATION_MOVE);
        EscrowDurableFiles.forceDirectory(directory);
        faultInjector.at(EscrowPersistencePhase.JOURNAL_AFTER_GENERATION_DIRECTORY_FORCE);
        ActiveEscrowJournal validated = validateGeneration(destination, reference, true);
        return new EscrowJournalGeneration(
                destination, validated.lineage(), validated.checkpointReference());
    }

    public ActiveEscrowJournal activate(EscrowJournalGeneration generation) throws IOException {
        return activate(generation, true);
    }

    public ActiveEscrowJournal activateRetainingPrior(EscrowJournalGeneration generation)
            throws IOException {
        return activate(generation, false);
    }

    private ActiveEscrowJournal activate(EscrowJournalGeneration generation,
                                         boolean cleanObsoleteGenerations) throws IOException {
        Objects.requireNonNull(generation, "generation");
        Path expectedPath = generationPath(generation.lineage().lineageId());
        if (!expectedPath.equals(generation.journalPath())) {
            throw new IllegalArgumentException("Escrow journal generation path is invalid");
        }
        ActiveEscrowJournal staged = validateGeneration(
                expectedPath, generation.checkpointReference(), false);
        if (!staged.lineage().equals(generation.lineage())) {
            throw new EscrowCheckpointException("Escrow journal generation metadata does not match");
        }

        EscrowJournalActivePointer pointer = new EscrowJournalActivePointer(
                generation.lineage().lineageId(), generation.checkpointReference());
        byte[] encodedPointer = EscrowJournalActivePointerCodec.encode(pointer);
        Path temporaryPointer = directory.resolve(
                ".journal-active-" + UUID.randomUUID() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporaryPointer,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            writeFully(channel, ByteBuffer.wrap(encodedPointer));
            channel.force(true);
        }
        faultInjector.at(EscrowPersistencePhase.JOURNAL_AFTER_POINTER_TEMP_FORCE);
        EscrowDurableFiles.replaceAtomically(temporaryPointer, activePointerPath());
        faultInjector.at(EscrowPersistencePhase.JOURNAL_AFTER_POINTER_MOVE);
        EscrowDurableFiles.forceDirectory(directory);
        faultInjector.at(EscrowPersistencePhase.JOURNAL_AFTER_POINTER_DIRECTORY_FORCE);

        ActiveEscrowJournal active = loadActive().orElseThrow(
                () -> new EscrowCheckpointException("Escrow active journal pointer is missing"));
        if (!active.checkpointReference().equals(generation.checkpointReference())
                || !active.lineage().equals(generation.lineage())) {
            throw new EscrowCheckpointException("Escrow active journal validation does not match rotation");
        }
        if (cleanObsoleteGenerations) {
            cleanupRetainingJournalGenerations(Set.of(active.lineage().lineageId()));
        }
        return active;
    }

    public Optional<ActiveEscrowJournal> loadActive() throws IOException {
        Path pointerPath = activePointerPath();
        if (!Files.exists(pointerPath, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(pointerPath, LinkOption.NOFOLLOW_LINKS)
                || Files.size(pointerPath) != EscrowJournalActivePointerCodec.ENCODED_BYTES) {
            throw new EscrowCheckpointException("Escrow active journal pointer is invalid");
        }
        EscrowJournalActivePointer pointer;
        try {
            pointer = EscrowJournalActivePointerCodec.decode(Files.readAllBytes(pointerPath));
        } catch (IllegalArgumentException exception) {
            throw new EscrowCheckpointException("Escrow active journal pointer is corrupt", exception);
        }
        return Optional.of(validateGeneration(
                generationPath(pointer.journalLineageId()), pointer.checkpointReference(), false));
    }

    public Optional<ActiveEscrowJournal> loadGeneration(UUID journalLineageId)
            throws IOException {
        Path path = generationPath(journalLineageId);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        return Optional.of(validateGeneration(path, null, false));
    }

    public Path generationPath(UUID journalLineageId) {
        Objects.requireNonNull(journalLineageId, "journalLineageId");
        return directory.resolve("journal-" + journalLineageId + ".wal");
    }

    public Path activePointerPath() {
        return directory.resolve(ACTIVE_POINTER_FILE);
    }

    public Path archiveLegacyJournal(Path legacyJournalPath) throws IOException {
        Path legacy = Objects.requireNonNull(legacyJournalPath, "legacyJournalPath")
                .toAbsolutePath().normalize();
        Path parent = legacy.getParent();
        if (parent == null) {
            throw new IOException("Escrow legacy journal directory is missing");
        }
        Path archive = parent.resolve("journal.legacy.wal");
        if (!Files.exists(legacy, LinkOption.NOFOLLOW_LINKS)) {
            return archive;
        }
        if (!Files.isRegularFile(legacy, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Escrow legacy journal is invalid");
        }
        EscrowDurableFiles.replaceAtomically(legacy, archive);
        EscrowDurableFiles.forceDirectory(parent);
        return archive;
    }

    public Path directory() {
        return directory;
    }

    public void cleanupObsoleteJournalGenerations(Set<UUID> retainedJournalLineages)
            throws IOException {
        Objects.requireNonNull(retainedJournalLineages, "retainedJournalLineages");
        Set<UUID> retained = new HashSet<>(retainedJournalLineages);
        if (retained.contains(null)) {
            throw new IllegalArgumentException("Retained journal lineage is invalid");
        }
        boolean changed = false;
        try (Stream<Path> entries = Files.list(directory)) {
            for (Path entry : entries.toList()) {
                String fileName = entry.getFileName().toString();
                Matcher matcher = GENERATION_FILE.matcher(fileName);
                if (matcher.matches()) {
                    UUID lineageId = UUID.fromString(matcher.group(1));
                    if (!retained.contains(lineageId)) {
                        changed |= Files.deleteIfExists(entry);
                    }
                } else if ((fileName.startsWith(".journal-")
                        || fileName.startsWith(".journal-active-"))
                        && fileName.endsWith(".tmp")) {
                    changed |= Files.deleteIfExists(entry);
                }
            }
        }
        if (changed) {
            EscrowDurableFiles.forceDirectory(directory);
        }
    }

    public void cleanupRetainingJournalGenerations(Set<UUID> retainedJournalLineages)
            throws IOException {
        faultInjector.at(EscrowPersistencePhase.JOURNAL_BEFORE_CLEANUP);
        cleanupObsoleteJournalGenerations(retainedJournalLineages);
        faultInjector.at(EscrowPersistencePhase.JOURNAL_AFTER_CLEANUP);
    }

    private ActiveEscrowJournal validateGeneration(Path path,
                                                   EscrowCheckpointReference expectedReference,
                                                   boolean exactBootstrap) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new EscrowCheckpointException("Escrow journal generation is missing");
        }
        try (WriteAheadJournal journal = WriteAheadJournal.open(path)) {
            JournalScanResult scan = journal.recovery();
            if (scan.firstSequence() != 1L || scan.recordCount() < 2L
                    || exactBootstrap && scan.recordCount() != 2L) {
                throw new EscrowCheckpointException("Escrow journal checkpoint bootstrap is invalid");
            }
            JournalReplayBatch bootstrap = journal.replayBatch(
                    0L, 1L, 2, WriteAheadJournal.MAX_REPLAY_BATCH_BYTES);
            if (bootstrap.records().size() != 2) {
                throw new EscrowCheckpointException("Escrow journal checkpoint bootstrap is incomplete");
            }
            JournalRecord lineageRecord = bootstrap.records().get(0);
            EscrowJournalEvent lineageEvent;
            JournalLineage lineage;
            try {
                lineageEvent = EscrowJournalEventCodec.decode(lineageRecord.payload());
                if (lineageEvent.type() != EscrowJournalEventType.JOURNAL_LINEAGE) {
                    throw new IllegalArgumentException("Journal bootstrap does not begin with lineage");
                }
                lineage = JournalLineageCodec.decode(lineageEvent.body());
            } catch (IllegalArgumentException exception) {
                throw new EscrowCheckpointException("Escrow journal lineage is invalid", exception);
            }
            if (!lineageRecord.transactionId().equals(lineage.lineageId())
                    || !lineageRecord.stepId().equals(
                    EscrowStepIds.forEvent(lineage.lineageId(), lineageEvent))) {
                throw new EscrowCheckpointException("Escrow journal lineage identity does not match");
            }

            JournalRecord referenceRecord = bootstrap.records().get(1);
            EscrowCheckpointReference reference;
            try {
                reference = EscrowCheckpointReferenceCodec.decode(referenceRecord.payload());
            } catch (IllegalArgumentException exception) {
                throw new EscrowCheckpointException("Escrow journal checkpoint reference is invalid", exception);
            }
            if ((expectedReference != null && !reference.equals(expectedReference))
                    || !referenceRecord.transactionId().equals(reference.checkpointId())
                    || !referenceRecord.stepId().equals(
                    EscrowCheckpointStepIds.forReference(reference))
                    || !reference.replacementJournalLineageId().equals(lineage.lineageId())) {
                throw new EscrowCheckpointException("Escrow journal checkpoint reference does not match");
            }
            TrustedEscrowCheckpoint trusted = checkpointManager.loadTrusted(
                    reference, lineage.lineageId());
            return new ActiveEscrowJournal(
                    path, lineage, reference, trusted, scan.recordCount(), scan.lastSequence());
        }
    }

    private static void writeFully(FileChannel channel, ByteBuffer source) throws IOException {
        while (source.hasRemaining()) {
            channel.write(source);
        }
    }
}
