package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.config.EscrowConfig;
import com.enviouse.futureshops.server.escrow.checkpoint.ActiveEscrowJournal;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpoint;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointManager;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointManifest;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointReference;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointReferenceCodec;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointStepIds;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointStore;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowJournalGeneration;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowJournalGenerationManager;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowPersistenceFaultInjector;
import com.enviouse.futureshops.server.escrow.checkpoint.TrustedEscrowCheckpoint;
import com.enviouse.futureshops.server.escrow.journal.JournalRecord;
import com.enviouse.futureshops.server.escrow.journal.JournalReplayBatch;
import com.enviouse.futureshops.server.escrow.journal.JournalScanResult;
import com.enviouse.futureshops.server.escrow.journal.WriteAheadJournal;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class EscrowRuntimeCoordinator implements AutoCloseable {
    public static final int DEFAULT_RECOVERY_BATCH_SIZE = 256;
    public static final int MAX_RECOVERY_BATCH_SIZE = 10_000;

    private static final int CHECKPOINT_BOOTSTRAP_RECORDS = 2;
    private static final long CHECKPOINT_TAIL_SEQUENCE = 3L;
    private static final int MAX_LINEAGE_ID_ATTEMPTS = 32;

    private final Path journalPath;
    private final EscrowRuntimeSavedData cursor;
    private final EscrowMutationApplier applier;
    private final BooleanSupplier materializedStateExists;
    private final Clock clock;
    private final Supplier<UUID> lineageIds;
    private final EscrowCheckpointSnapshotBundle snapshotBundle;
    private final Supplier<UUID> checkpointIds;
    private final EscrowPersistenceFaultInjector faultInjector;
    private final int checkpointGenerationRetention;
    private final EscrowMutationPermit mutationPermit;

    private EscrowRuntimeState state = EscrowRuntimeState.STOPPED;
    private WriteAheadJournal journal;
    private EscrowCheckpointManager checkpointManager;
    private EscrowJournalGenerationManager generationManager;
    private ActiveEscrowJournal activeJournal;
    private UUID lineageId;
    private JournalScanResult recoveryScan;
    private long recoveryOffset;
    private long recoveryExpectedSequence = 1L;
    private Throwable failure;
    private boolean quiescing;
    private boolean checkpointFailure;

    public EscrowRuntimeCoordinator(Path journalPath, EscrowRuntimeSavedData cursor,
                                    EscrowMutationApplier applier) {
        this(journalPath, cursor, applier, () -> false);
    }

    public EscrowRuntimeCoordinator(Path journalPath, EscrowRuntimeSavedData cursor,
                                    EscrowMutationApplier applier,
                                    BooleanSupplier materializedStateExists) {
        this(journalPath, cursor, applier, materializedStateExists,
                Clock.systemUTC(), UUID::randomUUID, null, UUID::randomUUID,
                EscrowPersistenceFaultInjector.NONE);
    }

    EscrowRuntimeCoordinator(Path journalPath, EscrowRuntimeSavedData cursor,
                             EscrowMutationApplier applier,
                             BooleanSupplier materializedStateExists,
                             EscrowMutationPermit mutationPermit) {
        this(journalPath, cursor, applier, materializedStateExists,
                Clock.systemUTC(), UUID::randomUUID, null, UUID::randomUUID,
                EscrowPersistenceFaultInjector.NONE,
                Objects.requireNonNull(mutationPermit, "mutationPermit"));
    }

    public EscrowRuntimeCoordinator(Path journalPath, EscrowRuntimeSavedData cursor,
                                    EscrowMutationApplier applier,
                                    EscrowCheckpointSnapshotBundle snapshotBundle) {
        this(journalPath, cursor, applier, () -> false, snapshotBundle);
    }

    public EscrowRuntimeCoordinator(Path journalPath, EscrowRuntimeSavedData cursor,
                                    EscrowMutationApplier applier,
                                    BooleanSupplier materializedStateExists,
                                    EscrowCheckpointSnapshotBundle snapshotBundle) {
        this(journalPath, cursor, applier, materializedStateExists,
                Clock.systemUTC(), UUID::randomUUID,
                Objects.requireNonNull(snapshotBundle, "snapshotBundle"), UUID::randomUUID,
                EscrowPersistenceFaultInjector.NONE);
    }

    EscrowRuntimeCoordinator(Path journalPath, EscrowRuntimeSavedData cursor,
                             EscrowMutationApplier applier,
                             BooleanSupplier materializedStateExists,
                             EscrowCheckpointSnapshotBundle snapshotBundle,
                             EscrowMutationPermit mutationPermit) {
        this(journalPath, cursor, applier, materializedStateExists,
                Clock.systemUTC(), UUID::randomUUID,
                Objects.requireNonNull(snapshotBundle, "snapshotBundle"), UUID::randomUUID,
                EscrowPersistenceFaultInjector.NONE,
                Objects.requireNonNull(mutationPermit, "mutationPermit"));
    }

    EscrowRuntimeCoordinator(Path journalPath, EscrowRuntimeSavedData cursor,
                             EscrowMutationApplier applier,
                             BooleanSupplier materializedStateExists, Clock clock,
                             Supplier<UUID> lineageIds) {
        this(journalPath, cursor, applier, materializedStateExists, clock, lineageIds,
                null, UUID::randomUUID, EscrowPersistenceFaultInjector.NONE);
    }

    EscrowRuntimeCoordinator(Path journalPath, EscrowRuntimeSavedData cursor,
                             EscrowMutationApplier applier,
                             BooleanSupplier materializedStateExists, Clock clock,
                             Supplier<UUID> lineageIds,
                             EscrowCheckpointSnapshotBundle snapshotBundle,
                             Supplier<UUID> checkpointIds,
                             EscrowPersistenceFaultInjector faultInjector) {
        this(journalPath, cursor, applier, materializedStateExists, clock, lineageIds,
                snapshotBundle, checkpointIds, faultInjector, null);
    }

    EscrowRuntimeCoordinator(Path journalPath, EscrowRuntimeSavedData cursor,
                             EscrowMutationApplier applier,
                             BooleanSupplier materializedStateExists, Clock clock,
                             Supplier<UUID> lineageIds,
                             EscrowCheckpointSnapshotBundle snapshotBundle,
                             Supplier<UUID> checkpointIds,
                             EscrowPersistenceFaultInjector faultInjector,
                             EscrowMutationPermit mutationPermit) {
        this.journalPath = Objects.requireNonNull(journalPath, "journalPath")
                .toAbsolutePath().normalize();
        this.cursor = Objects.requireNonNull(cursor, "cursor");
        this.applier = Objects.requireNonNull(applier, "applier");
        this.materializedStateExists = Objects.requireNonNull(
                materializedStateExists, "materializedStateExists");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lineageIds = Objects.requireNonNull(lineageIds, "lineageIds");
        this.snapshotBundle = snapshotBundle;
        this.checkpointIds = Objects.requireNonNull(checkpointIds, "checkpointIds");
        this.faultInjector = Objects.requireNonNull(faultInjector, "faultInjector");
        this.mutationPermit = mutationPermit;
        this.checkpointGenerationRetention =
                EscrowConfig.settings().checkpointGenerationRetention();
        if (checkpointGenerationRetention < 2) {
            throw new IllegalArgumentException("Escrow checkpoint retention is invalid");
        }
    }

    public synchronized EscrowRuntimeState start() {
        return start(DEFAULT_RECOVERY_BATCH_SIZE);
    }

    public synchronized EscrowRuntimeState start(int maximumRecords) {
        requireRecoveryLimit(maximumRecords);
        if (state != EscrowRuntimeState.STOPPED) {
            throw new EscrowRuntimeException("Escrow runtime is already started");
        }
        state = EscrowRuntimeState.STARTING;
        failure = null;
        lineageId = null;
        activeJournal = null;
        quiescing = false;
        checkpointFailure = false;
        resetRecovery();
        try {
            if (checkpointPersistenceEnabled()) {
                initializeCheckpointPersistence();
                Optional<ActiveEscrowJournal> active = generationManager.loadActive();
                if (active.isPresent()) {
                    prepareCheckpointRecovery(active.orElseThrow());
                    recoverBatch(maximumRecords);
                    return state;
                }
            }
            openLegacyJournal(maximumRecords);
        } catch (IOException | RuntimeException exception) {
            enterMaintenance(exception);
            closeJournalAfterFailure(exception);
        }
        return state;
    }

    public synchronized int recoverBatch(int maximumRecords) {
        requireRecoveryLimit(maximumRecords);
        if (state == EscrowRuntimeState.READY) {
            return 0;
        }
        if (state != EscrowRuntimeState.RECOVERING) {
            throw new EscrowRuntimeException("Escrow runtime is not recovering");
        }
        int applied = 0;
        try {
            JournalReplayBatch batch = journal.replayBatch(
                    recoveryOffset,
                    recoveryExpectedSequence,
                    maximumRecords,
                    WriteAheadJournal.MAX_REPLAY_BATCH_BYTES);
            for (JournalRecord record : batch.records()) {
                applyRecoveryRecord(record);
                applied++;
            }
            recoveryOffset = batch.nextOffset();
            recoveryExpectedSequence = batch.nextExpectedSequence();
            if (batch.endOfJournal()) {
                resetRecovery();
                state = EscrowRuntimeState.READY;
            }
        } catch (IOException | RuntimeException exception) {
            enterMaintenance(exception);
        }
        return applied;
    }

    synchronized EscrowCommitResult commit(UUID transactionId, EscrowJournalEvent event) {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(event, "event");
        requireReady();
        if (event.type() == EscrowJournalEventType.JOURNAL_LINEAGE) {
            throw new IllegalArgumentException("Journal lineage cannot be committed as a mutation");
        }
        if (event.type() == EscrowJournalEventType.MAINTENANCE_REPAIR) {
            throw new IllegalArgumentException(
                    "Maintenance repair requires the scoped maintenance lane");
        }
        if (event.type() == EscrowJournalEventType.ATM_WITHDRAWAL_COMMIT) {
            throw new IllegalArgumentException(
                    "ATM withdrawal requires the scoped composite lane");
        }
        return commitReady(transactionId, event);
    }

    synchronized EscrowCommitResult commitAtmWithdrawal(
            UUID transactionId,
            EscrowJournalEvent event
    ) {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(event, "event");
        requireReady();
        if (event.type() != EscrowJournalEventType.ATM_WITHDRAWAL_COMMIT) {
            throw new IllegalArgumentException(
                    "Scoped ATM withdrawal lane only accepts ATM withdrawal commits");
        }
        return commitReady(transactionId, event);
    }

    synchronized EscrowCommitResult commitMaintenanceRepair(
            UUID commandId,
            EscrowJournalEvent event
    ) {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(event, "event");
        requireReady();
        if (event.type() != EscrowJournalEventType.MAINTENANCE_REPAIR) {
            throw new IllegalArgumentException(
                    "Scoped maintenance lane only accepts maintenance repair events");
        }
        return commitReady(commandId, event);
    }

    private EscrowCommitResult commitReady(UUID transactionId,
                                           EscrowJournalEvent event) {
        if (!cursorAligned()) {
            EscrowRuntimeException exception = new EscrowRuntimeException(
                    "Escrow journal and cursor are not aligned");
            enterMaintenance(exception);
            throw exception;
        }
        EscrowPreflightResult preflight = Objects.requireNonNull(
                applier.preflight(transactionId, event), "preflight");
        if (preflight == EscrowPreflightResult.REPLAY) {
            return EscrowCommitResult.replay();
        }
        byte[] payload = EscrowJournalEventCodec.encode(event);
        UUID stepId = EscrowStepIds.forEvent(transactionId, event);
        try {
            JournalRecord record = journal.append(transactionId, stepId, payload);
            applier.apply(record, event);
            advanceCursor(lineageId, record.sequence());
            return EscrowCommitResult.applied(record);
        } catch (IOException | RuntimeException exception) {
            enterMaintenance(exception);
            throw new EscrowRuntimeException("Escrow mutation entered maintenance", exception);
        }
    }

    public synchronized ActiveEscrowJournal checkpointNow() {
        if (!checkpointPersistenceEnabled()) {
            throw new EscrowRuntimeException("Escrow checkpoint persistence is not configured");
        }
        requireReady();
        quiescing = true;
        try {
            return rotateCheckpoint();
        } catch (IOException | RuntimeException exception) {
            checkpointFailure = true;
            enterMaintenance(exception);
            closeJournalAfterFailure(exception);
            throw new EscrowRuntimeException("Escrow checkpoint entered maintenance", exception);
        } finally {
            quiescing = false;
        }
    }

    public synchronized EscrowRuntimeState state() {
        return state;
    }

    public synchronized boolean isReady() {
        return state == EscrowRuntimeState.READY && !quiescing;
    }

    public synchronized boolean isQuiescing() {
        return quiescing;
    }

    public synchronized boolean supportsCheckpoints() {
        return checkpointPersistenceEnabled();
    }

    public synchronized Optional<Throwable> failure() {
        return Optional.ofNullable(failure);
    }

    public synchronized Optional<UUID> lineageId() {
        return Optional.ofNullable(lineageId);
    }

    public synchronized long lastAppliedSequence() {
        return cursor.lastAppliedSequence();
    }

    public synchronized boolean journalHealthyAndAligned() {
        return isReady() && cursorAligned();
    }

    public synchronized long pendingRecoveryRecords() {
        if (state != EscrowRuntimeState.RECOVERING || recoveryScan == null) {
            return 0L;
        }
        return Math.max(0L,
                recoveryScan.lastSequence() - recoveryExpectedSequence + 1L);
    }

    public synchronized EscrowJournalMetrics journalMetrics() {
        requireReady();
        try {
            return new EscrowJournalMetrics(journal.sizeBytes(), journal.recordCount());
        } catch (IOException | ArithmeticException exception) {
            enterMaintenance(exception);
            throw new EscrowRuntimeException(
                    "Unable to read escrow journal metrics", exception);
        }
    }

    public synchronized void stop() {
        if (state == EscrowRuntimeState.STOPPED
                || checkpointFailure && state == EscrowRuntimeState.MAINTENANCE
                && journal == null) {
            return;
        }
        boolean cleanCheckpointRequested = checkpointPersistenceEnabled()
                && state == EscrowRuntimeState.READY;
        quiescing = true;
        try {
            if (cleanCheckpointRequested) {
                rotateCheckpoint();
            }
            state = EscrowRuntimeState.STOPPING;
            closeJournal();
            resetRecovery();
            lineageId = null;
            checkpointManager = null;
            generationManager = null;
            activeJournal = null;
            state = EscrowRuntimeState.STOPPED;
        } catch (IOException | RuntimeException exception) {
            checkpointFailure = cleanCheckpointRequested;
            enterMaintenance(exception);
            closeJournalAfterFailure(exception);
            throw new EscrowRuntimeException("Unable to stop escrow runtime cleanly", exception);
        } finally {
            quiescing = false;
        }
    }

    @Override
    public void close() {
        stop();
    }

    private void initializeCheckpointPersistence() throws IOException {
        Path root = journalPath.getParent();
        if (root == null) {
            throw new EscrowRuntimeException("Escrow journal directory is missing");
        }
        checkpointManager = new EscrowCheckpointManager(
                root.resolve("checkpoints"), faultInjector);
        generationManager = new EscrowJournalGenerationManager(
                root.resolve("journals"), checkpointManager, faultInjector);
    }

    private void openLegacyJournal(int maximumRecords) throws IOException {
        journal = WriteAheadJournal.open(journalPath);
        JournalScanResult scan = journal.recovery();
        if (scan.empty()) {
            initializeJournal();
            return;
        }
        prepareLegacyRecovery(scan);
        recoverBatch(maximumRecords);
    }

    private void initializeJournal() throws IOException {
        if (cursor.journalLineage().isPresent() || cursor.lastAppliedSequence() != 0L) {
            throw new EscrowRuntimeException("Escrow journal is missing for its persisted cursor");
        }
        if (materializedStateExists.getAsBoolean()) {
            throw new EscrowRuntimeException("Escrow journal is missing for materialized state");
        }
        JournalLineage lineage = new JournalLineage(
                Objects.requireNonNull(lineageIds.get(), "lineageId"), clock.instant());
        EscrowJournalEvent event = new EscrowJournalEvent(
                EscrowJournalEventType.JOURNAL_LINEAGE, JournalLineageCodec.encode(lineage));
        byte[] payload = EscrowJournalEventCodec.encode(event);
        JournalRecord record = journal.append(
                lineage.lineageId(), EscrowStepIds.forEvent(lineage.lineageId(), event), payload);
        if (record.sequence() != 1L) {
            throw new EscrowRuntimeException("Escrow journal lineage is not sequence one");
        }
        establishCursorLineage(lineage.lineageId(), record.sequence());
        lineageId = lineage.lineageId();
        state = EscrowRuntimeState.READY;
    }

    private void prepareLegacyRecovery(JournalScanResult scan) throws IOException {
        if (scan.firstSequence() != 1L) {
            throw new EscrowRuntimeException("Escrow journal does not begin at sequence one");
        }
        JournalReplayBatch firstBatch = journal.replayBatch(
                0L, 1L, 1, WriteAheadJournal.MAX_RECORD_BYTES);
        if (firstBatch.records().size() != 1) {
            throw new EscrowRuntimeException("Escrow journal lineage record is missing");
        }
        JournalRecord first = firstBatch.records().get(0);
        EscrowJournalEvent event = decodeAndVerifyStep(first);
        if (event.type() != EscrowJournalEventType.JOURNAL_LINEAGE) {
            throw new EscrowRuntimeException("Escrow journal sequence one is not a lineage event");
        }
        JournalLineage lineage = JournalLineageCodec.decode(event.body());
        if (!first.transactionId().equals(lineage.lineageId())) {
            throw new EscrowRuntimeException("Escrow journal lineage identity does not match");
        }
        Optional<UUID> persistedLineage = cursor.journalLineage();
        if (persistedLineage.isPresent()
                && !persistedLineage.orElseThrow().equals(lineage.lineageId())) {
            throw new EscrowRuntimeException("Escrow persisted lineage does not match the journal");
        }
        if (cursor.lastAppliedSequence() > scan.lastSequence()) {
            throw new EscrowRuntimeException("Escrow cursor is ahead of the journal");
        }
        lineageId = lineage.lineageId();
        recoveryScan = scan;
        recoveryOffset = 0L;
        recoveryExpectedSequence = 1L;
        state = EscrowRuntimeState.RECOVERING;
    }

    private void prepareCheckpointRecovery(ActiveEscrowJournal active) throws IOException {
        WriteAheadJournal opened = WriteAheadJournal.open(active.journalPath());
        JournalReplayBatch bootstrap;
        try {
            bootstrap = validateActiveBootstrap(active, opened);
            restoreTrustedCheckpoint(active.trustedCheckpoint());
        } catch (RuntimeException | IOException exception) {
            try {
                opened.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
        journal = opened;
        activeJournal = active;
        adoptCursorCheckpoint(active.checkpointReference());
        lineageId = active.lineage().lineageId();
        generationManager.archiveLegacyJournal(journalPath);
        recoveryScan = opened.recovery();
        recoveryOffset = bootstrap.nextOffset();
        recoveryExpectedSequence = CHECKPOINT_TAIL_SEQUENCE;
        state = EscrowRuntimeState.RECOVERING;
    }

    private JournalReplayBatch validateActiveBootstrap(ActiveEscrowJournal active,
                                                       WriteAheadJournal opened)
            throws IOException {
        JournalScanResult scan = opened.recovery();
        if (!opened.path().equals(active.journalPath())
                || scan.firstSequence() != 1L
                || scan.recordCount() < CHECKPOINT_BOOTSTRAP_RECORDS
                || scan.recordCount() != active.recordCount()
                || scan.lastSequence() != active.lastSequence()) {
            throw new EscrowRuntimeException("Escrow active journal metadata does not match");
        }
        JournalReplayBatch bootstrap = opened.replayBatch(
                0L, 1L, CHECKPOINT_BOOTSTRAP_RECORDS,
                WriteAheadJournal.MAX_REPLAY_BATCH_BYTES);
        if (bootstrap.records().size() != CHECKPOINT_BOOTSTRAP_RECORDS
                || bootstrap.nextExpectedSequence() != CHECKPOINT_TAIL_SEQUENCE) {
            throw new EscrowRuntimeException("Escrow checkpoint bootstrap is incomplete");
        }
        JournalRecord lineageRecord = bootstrap.records().get(0);
        EscrowJournalEvent lineageEvent = decodeAndVerifyStep(lineageRecord);
        JournalLineage lineage = JournalLineageCodec.decode(lineageEvent.body());
        if (lineageEvent.type() != EscrowJournalEventType.JOURNAL_LINEAGE
                || !lineage.equals(active.lineage())
                || !lineageRecord.transactionId().equals(lineage.lineageId())) {
            throw new EscrowRuntimeException("Escrow checkpoint lineage does not match");
        }
        JournalRecord referenceRecord = bootstrap.records().get(1);
        EscrowCheckpointReference reference;
        try {
            reference = EscrowCheckpointReferenceCodec.decode(referenceRecord.payload());
        } catch (IllegalArgumentException exception) {
            throw new EscrowRuntimeException("Escrow checkpoint reference is invalid", exception);
        }
        if (!reference.equals(active.checkpointReference())
                || !referenceRecord.transactionId().equals(reference.checkpointId())
                || !referenceRecord.stepId().equals(
                EscrowCheckpointStepIds.forReference(reference))) {
            throw new EscrowRuntimeException("Escrow checkpoint reference does not match");
        }
        return bootstrap;
    }

    private void restoreTrustedCheckpoint(TrustedEscrowCheckpoint trustedCheckpoint) {
        Map<EscrowCheckpointStore, byte[]> snapshots =
                trustedCheckpoint.checkpoint().snapshots();
        if (snapshots.size() != EscrowCheckpointStore.values().length) {
            throw new EscrowRuntimeException("Escrow trusted checkpoint store count is invalid");
        }
        for (EscrowCheckpointStore store : EscrowCheckpointStore.values()) {
            if (!snapshots.containsKey(store) || snapshots.get(store) == null) {
                throw new EscrowRuntimeException("Escrow trusted checkpoint store is missing");
            }
        }
        EscrowPreparedCheckpointRestore prepared = Objects.requireNonNull(
                snapshotBundle.prepareTrustedRestore(trustedCheckpoint),
                "preparedCheckpointRestore");
        prepared.apply();
    }

    private ActiveEscrowJournal rotateCheckpoint() throws IOException {
        if (checkpointManager == null || generationManager == null) {
            throw new EscrowRuntimeException("Escrow checkpoint persistence is not initialized");
        }
        if (journal.nextSequence() != Math.addExact(cursor.lastAppliedSequence(), 1L)) {
            throw new EscrowRuntimeException("Escrow journal and cursor are not aligned");
        }
        UUID sourceLineage = Objects.requireNonNull(lineageId, "lineageId");
        UUID replacementLineage = nextReplacementLineage(sourceLineage);
        UUID checkpointId = Objects.requireNonNull(checkpointIds.get(), "checkpointId");
        Instant createdAt = clock.instant();
        EscrowCheckpoint checkpoint = new EscrowCheckpoint(
                checkpointId,
                sourceLineage,
                replacementLineage,
                cursor.lastAppliedSequence(),
                createdAt,
                Objects.requireNonNull(snapshotBundle.captureSnapshots(), "snapshots"));
        EscrowCheckpointManifest manifest = checkpointManager.writeGeneration(checkpoint);
        EscrowCheckpoint verified = checkpointManager.readVerified(manifest);
        if (!verified.equals(checkpoint)) {
            throw new EscrowRuntimeException("Escrow checkpoint verification does not match");
        }
        EscrowJournalGeneration generation = generationManager.stage(manifest, createdAt);
        ActiveEscrowJournal priorActive = activeJournal;
        closeJournal();
        ActiveEscrowJournal active = generationManager.activateRetainingPrior(generation);
        WriteAheadJournal replacement = WriteAheadJournal.open(active.journalPath());
        try {
            JournalReplayBatch bootstrap = validateActiveBootstrap(active, replacement);
            if (!bootstrap.endOfJournal()) {
                throw new EscrowRuntimeException("Escrow replacement journal contains mutations");
            }
        } catch (RuntimeException | IOException exception) {
            try {
                replacement.close();
            } catch (IOException closeFailure) {
                exception.addSuppressed(closeFailure);
            }
            throw exception;
        }
        journal = replacement;
        adoptCursorCheckpoint(active.checkpointReference());
        lineageId = active.lineage().lineageId();
        activeJournal = active;
        generationManager.archiveLegacyJournal(journalPath);
        cleanupCheckpointHistory(active, priorActive);
        resetRecovery();
        state = EscrowRuntimeState.READY;
        return active;
    }

    private void cleanupCheckpointHistory(ActiveEscrowJournal current,
                                          ActiveEscrowJournal previous) throws IOException {
        LinkedHashMap<UUID, ActiveEscrowJournal> retained = new LinkedHashMap<>();
        retained.put(current.lineage().lineageId(), current);
        ActiveEscrowJournal ancestor = previous;
        while (ancestor != null && retained.size() < checkpointGenerationRetention) {
            UUID ancestorLineage = ancestor.lineage().lineageId();
            if (retained.putIfAbsent(ancestorLineage, ancestor) != null) {
                throw new EscrowRuntimeException("Escrow checkpoint lineage history contains a cycle");
            }
            if (retained.size() >= checkpointGenerationRetention) {
                break;
            }
            Optional<ActiveEscrowJournal> next = generationManager.loadGeneration(
                    ancestor.checkpointReference().sourceJournalLineageId());
            ancestor = next.orElse(null);
        }
        Set<UUID> retainedLineages = new LinkedHashSet<>();
        Set<UUID> retainedCheckpoints = new LinkedHashSet<>();
        for (ActiveEscrowJournal retainedPair : retained.values()) {
            retainedLineages.add(retainedPair.lineage().lineageId());
            retainedCheckpoints.add(retainedPair.checkpointReference().checkpointId());
        }
        generationManager.cleanupRetainingJournalGenerations(retainedLineages);
        checkpointManager.cleanupOrphans(retainedCheckpoints);
    }

    private UUID nextReplacementLineage(UUID sourceLineage) {
        for (int attempt = 0; attempt < MAX_LINEAGE_ID_ATTEMPTS; attempt++) {
            UUID candidate = Objects.requireNonNull(lineageIds.get(), "lineageId");
            if (!candidate.equals(sourceLineage)) {
                return candidate;
            }
        }
        throw new EscrowRuntimeException("Unable to allocate a new escrow journal lineage");
    }

    private void applyRecoveryRecord(JournalRecord record) {
        EscrowJournalEvent event = decodeAndVerifyStep(record);
        if (record.sequence() == 1L) {
            if (event.type() != EscrowJournalEventType.JOURNAL_LINEAGE) {
                throw new EscrowRuntimeException("Escrow journal sequence one is not a lineage event");
            }
            JournalLineage lineage = JournalLineageCodec.decode(event.body());
            if (!lineage.lineageId().equals(lineageId)
                    || !record.transactionId().equals(lineage.lineageId())) {
                throw new EscrowRuntimeException("Escrow journal lineage identity does not match");
            }
            establishCursorLineage(lineageId, 1L);
            return;
        }
        if (event.type() == EscrowJournalEventType.JOURNAL_LINEAGE) {
            throw new EscrowRuntimeException("Escrow journal contains another lineage event");
        }
        applier.apply(record, event);
        advanceCursor(lineageId, record.sequence());
    }

    private EscrowJournalEvent decodeAndVerifyStep(JournalRecord record) {
        EscrowJournalEvent event = EscrowJournalEventCodec.decode(record.payload());
        UUID expected = EscrowStepIds.forEvent(record.transactionId(), event);
        if (!record.stepId().equals(expected)) {
            throw new EscrowRuntimeException("Escrow journal step identity does not match");
        }
        return event;
    }

    private void establishCursorLineage(UUID lineage, long sequence) {
        mutateCursor(() -> cursor.establishLineage(lineage, sequence));
    }

    private void adoptCursorCheckpoint(EscrowCheckpointReference reference) {
        mutateCursor(() -> cursor.adoptTrustedCheckpoint(reference));
    }

    private void advanceCursor(UUID lineage, long sequence) {
        mutateCursor(() -> cursor.advance(lineage, sequence));
    }

    private void mutateCursor(Runnable mutation) {
        if (mutationPermit == null) {
            mutation.run();
            return;
        }
        try (EscrowMutationPermit.Scope ignored = mutationPermit.activate()) {
            mutation.run();
        }
    }

    private void requireReady() {
        if (state != EscrowRuntimeState.READY || quiescing
                || journal == null || lineageId == null) {
            throw new EscrowRuntimeException("Escrow runtime is not ready");
        }
    }

    private boolean cursorAligned() {
        if (journal == null || lineageId == null) {
            return false;
        }
        try {
            return journal.nextSequence()
                    == Math.addExact(cursor.lastAppliedSequence(), 1L);
        } catch (IOException | ArithmeticException exception) {
            return false;
        }
    }

    private boolean checkpointPersistenceEnabled() {
        return snapshotBundle != null;
    }

    private void resetRecovery() {
        recoveryScan = null;
        recoveryOffset = 0L;
        recoveryExpectedSequence = 1L;
    }

    private void closeJournal() throws IOException {
        WriteAheadJournal closing = journal;
        journal = null;
        if (closing != null) {
            closing.close();
        }
    }

    private void closeJournalAfterFailure(Throwable cause) {
        try {
            closeJournal();
        } catch (IOException | RuntimeException closeFailure) {
            cause.addSuppressed(closeFailure);
        }
    }

    private void enterMaintenance(Throwable cause) {
        if (failure == null) {
            failure = Objects.requireNonNull(cause, "cause");
        }
        state = EscrowRuntimeState.MAINTENANCE;
    }

    private static void requireRecoveryLimit(int maximumRecords) {
        if (maximumRecords <= 0 || maximumRecords > MAX_RECOVERY_BATCH_SIZE) {
            throw new IllegalArgumentException("Invalid escrow recovery batch size");
        }
    }
}
