package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.SavedDataMigrations;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairCommand;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairPayload;
import com.enviouse.futureshops.server.escrow.checkpoint.EscrowCheckpointReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class EscrowRuntimeSavedData extends EscrowManagedSavedData {
    public static final String DATA_NAME = "futureshops_escrow_runtime";
    private static final int CURRENT_VERSION = 3;
    private static final int MAX_INCIDENT_LENGTH = 128;

    private UUID journalLineage;
    private long lastAppliedSequence;
    private UUID checkpointId;
    private UUID checkpointSourceLineage;
    private long checkpointBaseSequence;
    private long maintenanceRevision;
    private boolean maintenanceRequested;
    private UUID lastMaintenanceCommandId;
    private String maintenanceIncident = "";
    private long maintenanceVerifiedSequence = -1L;
    private byte[] maintenanceVerificationFingerprint = new byte[0];
    private EscrowMutationPermit runtimeMutationPermit;

    public static EscrowRuntimeSavedData load(CompoundTag tag) {
        if (tag.contains("schemaVersion")
                && !tag.contains("schemaVersion", Tag.TAG_INT)) {
            throw new IllegalStateException("Escrow runtime schema is malformed");
        }
        int version = SavedDataMigrations.readVersion(tag);
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException("Escrow runtime schema is newer than this build");
        }
        if (version < 0) {
            throw new IllegalStateException("Escrow runtime schema is invalid");
        }
        SavedDataMigrations.needsMigration(DATA_NAME, version, CURRENT_VERSION);
        if (version >= 1
                && !tag.contains("lastAppliedSequence", Tag.TAG_LONG)) {
            throw new IllegalStateException("Escrow journal cursor is missing");
        }
        if (tag.contains("lastAppliedSequence")
                && !tag.contains("lastAppliedSequence", Tag.TAG_LONG)) {
            throw new IllegalStateException("Escrow journal cursor is malformed");
        }
        if (tag.contains("journalLineage") && !tag.hasUUID("journalLineage")) {
            throw new IllegalStateException("Escrow journal lineage is malformed");
        }
        if (tag.contains("checkpointId") && !tag.hasUUID("checkpointId")) {
            throw new IllegalStateException("Escrow checkpoint identity is malformed");
        }
        if (tag.contains("checkpointSourceLineage")
                && !tag.hasUUID("checkpointSourceLineage")) {
            throw new IllegalStateException("Escrow checkpoint source lineage is malformed");
        }
        if (tag.contains("checkpointBaseSequence")
                && !tag.contains("checkpointBaseSequence", Tag.TAG_LONG)) {
            throw new IllegalStateException("Escrow checkpoint base sequence is malformed");
        }
        if (version >= 3) {
            requireMaintenanceFields(tag);
        }
        EscrowRuntimeSavedData data = new EscrowRuntimeSavedData();
        data.journalLineage = tag.hasUUID("journalLineage") ? tag.getUUID("journalLineage") : null;
        data.lastAppliedSequence = tag.getLong("lastAppliedSequence");
        boolean hasCheckpointId = tag.hasUUID("checkpointId");
        boolean hasCheckpointSource = tag.hasUUID("checkpointSourceLineage");
        boolean hasCheckpointBase = tag.contains("checkpointBaseSequence", Tag.TAG_LONG);
        if (hasCheckpointId != hasCheckpointSource || hasCheckpointId != hasCheckpointBase) {
            throw new IllegalStateException("Escrow checkpoint cursor metadata is incomplete");
        }
        if (hasCheckpointId) {
            data.checkpointId = tag.getUUID("checkpointId");
            data.checkpointSourceLineage = tag.getUUID("checkpointSourceLineage");
            data.checkpointBaseSequence = tag.getLong("checkpointBaseSequence");
        }
        if (version >= 3) {
            data.maintenanceRevision = tag.getLong("maintenanceRevision");
            data.maintenanceRequested = readStrictBoolean(tag, "maintenanceRequested");
            data.lastMaintenanceCommandId = tag.hasUUID("lastMaintenanceCommand")
                    ? tag.getUUID("lastMaintenanceCommand") : null;
            data.maintenanceIncident = tag.getString("maintenanceIncident");
            data.maintenanceVerifiedSequence = tag.getLong("maintenanceVerifiedSequence");
            data.maintenanceVerificationFingerprint =
                    tag.getByteArray("maintenanceVerificationFingerprint");
            data.validateMaintenanceState();
        }
        if (data.lastAppliedSequence < 0L) {
            throw new IllegalStateException("Escrow journal cursor cannot be negative");
        }
        if ((data.journalLineage == null) != (data.lastAppliedSequence == 0L)) {
            throw new IllegalStateException("Escrow journal lineage and cursor do not match");
        }
        if (data.checkpointId != null
                && (data.checkpointBaseSequence < 1L
                || data.lastAppliedSequence < 2L
                || data.journalLineage.equals(data.checkpointSourceLineage))) {
            throw new IllegalStateException("Escrow checkpoint cursor metadata is invalid");
        }
        if (version < CURRENT_VERSION) {
            data.setDirty();
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        SavedDataMigrations.writeVersion(tag, CURRENT_VERSION);
        if (journalLineage != null) {
            tag.putUUID("journalLineage", journalLineage);
        }
        tag.putLong("lastAppliedSequence", lastAppliedSequence);
        if (checkpointId != null) {
            tag.putUUID("checkpointId", checkpointId);
            tag.putUUID("checkpointSourceLineage", checkpointSourceLineage);
            tag.putLong("checkpointBaseSequence", checkpointBaseSequence);
        }
        tag.putLong("maintenanceRevision", maintenanceRevision);
        tag.putBoolean("maintenanceRequested", maintenanceRequested);
        if (lastMaintenanceCommandId != null) {
            tag.putUUID("lastMaintenanceCommand", lastMaintenanceCommandId);
        }
        tag.putString("maintenanceIncident", maintenanceIncident);
        tag.putLong("maintenanceVerifiedSequence", maintenanceVerifiedSequence);
        tag.putByteArray("maintenanceVerificationFingerprint",
                maintenanceVerificationFingerprint);
        return tag;
    }

    public static EscrowRuntimeSavedData get(MinecraftServer server) {
        EscrowRuntimeSavedData data = server.overworld().getDataStorage().computeIfAbsent(
                EscrowRuntimeSavedData::load, EscrowRuntimeSavedData::new, DATA_NAME);
        data.acquireManagedMutationPermit();
        return data;
    }

    public synchronized void replaceFromValidated(EscrowRuntimeSavedData source) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return;
        }
        RuntimeStateSnapshot snapshot = source.snapshotForRestore();
        journalLineage = snapshot.journalLineage();
        lastAppliedSequence = snapshot.lastAppliedSequence();
        checkpointId = snapshot.checkpointId();
        checkpointSourceLineage = snapshot.checkpointSourceLineage();
        checkpointBaseSequence = snapshot.checkpointBaseSequence();
        maintenanceRevision = snapshot.maintenanceRevision();
        maintenanceRequested = snapshot.maintenanceRequested();
        lastMaintenanceCommandId = snapshot.lastMaintenanceCommandId();
        maintenanceIncident = snapshot.maintenanceIncident();
        maintenanceVerifiedSequence = snapshot.maintenanceVerifiedSequence();
        maintenanceVerificationFingerprint =
                snapshot.maintenanceVerificationFingerprint().clone();
        setDirty();
    }

    public synchronized Optional<UUID> journalLineage() {
        return Optional.ofNullable(journalLineage);
    }

    public synchronized long lastAppliedSequence() {
        return lastAppliedSequence;
    }

    public synchronized Optional<UUID> checkpointId() {
        return Optional.ofNullable(checkpointId);
    }

    public synchronized Optional<UUID> checkpointSourceLineage() {
        return Optional.ofNullable(checkpointSourceLineage);
    }

    public synchronized OptionalLong checkpointBaseSequence() {
        return checkpointId == null
                ? OptionalLong.empty()
                : OptionalLong.of(checkpointBaseSequence);
    }

    public synchronized boolean maintenanceRequested() {
        return maintenanceRequested;
    }

    public synchronized MaintenanceRuntimeSnapshot maintenanceSnapshot() {
        return snapshotOf(maintenanceRevision, maintenanceRequested,
                lastMaintenanceCommandId, maintenanceIncident,
                maintenanceVerifiedSequence, maintenanceVerificationFingerprint);
    }

    synchronized MaintenanceRuntimeSnapshot previewMaintenance(
            MaintenanceRepairCommand command
    ) {
        return valuesFor(command).snapshot();
    }

    synchronized void applyMaintenance(MaintenanceRepairCommand command,
                                       MaintenanceRuntimeSnapshot expectedResult) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(expectedResult, "expectedResult");
        if (maintenanceSnapshot().equals(expectedResult)) {
            return;
        }
        if (maintenanceRevision >= expectedResult.revision()) {
            throw new EscrowRuntimeException(
                    "Maintenance runtime revision conflicts with its journal effect");
        }
        MaintenanceStateFingerprints.requireExpected(command.expectedState(),
                maintenanceRevision, maintenanceSnapshot().fingerprint());
        MaintenanceValues next = valuesFor(command);
        if (!next.snapshot().equals(expectedResult)) {
            throw new EscrowRuntimeException(
                    "Maintenance runtime journal result does not match its command");
        }
        maintenanceRevision = next.revision();
        maintenanceRequested = next.requested();
        lastMaintenanceCommandId = next.commandId();
        maintenanceIncident = next.incident();
        maintenanceVerifiedSequence = next.verifiedSequence();
        maintenanceVerificationFingerprint = next.verificationFingerprint().clone();
        setDirty();
    }

    synchronized boolean maintenanceIsCurrent(MaintenanceRuntimeSnapshot result) {
        return maintenanceSnapshot().equals(Objects.requireNonNull(result, "result"));
    }

    synchronized boolean maintenanceWasApplied(MaintenanceRuntimeSnapshot result) {
        Objects.requireNonNull(result, "result");
        return maintenanceRevision > result.revision()
                || maintenanceRevision == result.revision()
                && maintenanceSnapshot().equals(result);
    }

    public synchronized void establishLineage(UUID lineage, long sequence) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(lineage, "lineage");
        if (sequence != 1L) {
            throw new IllegalArgumentException("Escrow lineage must be sequence one");
        }
        if (journalLineage != null) {
            if (!journalLineage.equals(lineage)) {
                throw new IllegalStateException("Escrow journal lineage does not match");
            }
            return;
        }
        journalLineage = lineage;
        lastAppliedSequence = sequence;
        checkpointId = null;
        checkpointSourceLineage = null;
        checkpointBaseSequence = 0L;
        setDirty();
    }

    public synchronized void adoptTrustedCheckpoint(EscrowCheckpointReference reference) {
        requireEscrowMutationPermit();
        Objects.requireNonNull(reference, "reference");
        UUID replacementLineage = reference.replacementJournalLineageId();
        if (replacementLineage.equals(reference.sourceJournalLineageId())) {
            throw new IllegalArgumentException("Escrow checkpoint replacement lineage must be new");
        }
        if (replacementLineage.equals(journalLineage)
                && lastAppliedSequence == 2L
                && reference.checkpointId().equals(checkpointId)
                && reference.sourceJournalLineageId().equals(checkpointSourceLineage)
                && reference.baseJournalSequence() == checkpointBaseSequence) {
            return;
        }
        journalLineage = replacementLineage;
        lastAppliedSequence = 2L;
        checkpointId = reference.checkpointId();
        checkpointSourceLineage = reference.sourceJournalLineageId();
        checkpointBaseSequence = reference.baseJournalSequence();
        setDirty();
    }

    public synchronized void advance(UUID lineage, long sequence) {
        requireEscrowMutationPermit();
        if (journalLineage == null || !journalLineage.equals(lineage)) {
            throw new IllegalStateException("Escrow journal lineage does not match");
        }
        if (sequence <= lastAppliedSequence) {
            return;
        }
        if (sequence != Math.addExact(lastAppliedSequence, 1L)) {
            throw new IllegalStateException("Escrow journal cursor is not contiguous");
        }
        lastAppliedSequence = sequence;
        setDirty();
    }

    synchronized EscrowMutationPermit acquireManagedMutationPermit() {
        if (runtimeMutationPermit == null) {
            runtimeMutationPermit = new EscrowMutationPermit();
            bindManagedMutationPermit(runtimeMutationPermit);
        }
        return runtimeMutationPermit;
    }

    private synchronized RuntimeStateSnapshot snapshotForRestore() {
        return new RuntimeStateSnapshot(journalLineage, lastAppliedSequence, checkpointId,
                checkpointSourceLineage, checkpointBaseSequence, maintenanceRevision,
                maintenanceRequested, lastMaintenanceCommandId, maintenanceIncident,
                maintenanceVerifiedSequence, maintenanceVerificationFingerprint.clone());
    }

    private MaintenanceValues valuesFor(MaintenanceRepairCommand command) {
        long nextRevision = Math.addExact(maintenanceRevision, 1L);
        if (command.payload() instanceof MaintenanceRepairPayload.EnterMaintenance enter) {
            if (maintenanceRequested) {
                throw new EscrowRuntimeException(
                        "Escrow runtime is already in requested maintenance");
            }
            return new MaintenanceValues(nextRevision, true, command.commandId(),
                    enter.incidentReference(), -1L, new byte[0]);
        }
        if (command.payload() instanceof MaintenanceRepairPayload.VerifyAndResume resume) {
            return new MaintenanceValues(nextRevision, false, command.commandId(), "",
                    resume.verifiedJournalSequence(),
                    resume.verificationFingerprint().bytes());
        }
        throw new EscrowRuntimeException(
                "Maintenance runtime command payload is unsupported");
    }

    private void validateMaintenanceState() {
        if (maintenanceRevision < 0L
                || maintenanceIncident.length() > MAX_INCIDENT_LENGTH) {
            throw new IllegalStateException("Escrow maintenance runtime state is invalid");
        }
        if (maintenanceRevision == 0L) {
            if (maintenanceRequested || lastMaintenanceCommandId != null
                    || !maintenanceIncident.isEmpty()
                    || maintenanceVerifiedSequence != -1L
                    || maintenanceVerificationFingerprint.length != 0) {
                throw new IllegalStateException(
                        "Escrow initial maintenance runtime state is invalid");
            }
            return;
        }
        if (lastMaintenanceCommandId == null
                || lastMaintenanceCommandId.equals(new UUID(0L, 0L))) {
            throw new IllegalStateException(
                    "Escrow maintenance command identity is invalid");
        }
        if (maintenanceRequested) {
            if (maintenanceIncident.isBlank()
                    || maintenanceVerifiedSequence != -1L
                    || maintenanceVerificationFingerprint.length != 0) {
                throw new IllegalStateException(
                        "Escrow requested maintenance state is invalid");
            }
        } else if (!maintenanceIncident.isEmpty()
                || maintenanceVerifiedSequence < 0L
                || maintenanceVerificationFingerprint.length
                != com.enviouse.futureshops.server.escrow.admin.MaintenanceStateFingerprint.BYTE_LENGTH) {
            throw new IllegalStateException(
                    "Escrow resumed maintenance state is invalid");
        }
    }

    private static MaintenanceRuntimeSnapshot snapshotOf(
            long revision,
            boolean requested,
            UUID commandId,
            String incident,
            long verifiedSequence,
            byte[] verificationFingerprint
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(0x46534D53);
            output.writeInt(1);
            output.writeLong(revision);
            output.writeBoolean(requested);
            output.writeBoolean(commandId != null);
            if (commandId != null) {
                output.writeLong(commandId.getMostSignificantBits());
                output.writeLong(commandId.getLeastSignificantBits());
            }
            byte[] incidentBytes = Objects.requireNonNull(incident, "incident")
                    .getBytes(StandardCharsets.UTF_8);
            output.writeInt(incidentBytes.length);
            output.write(incidentBytes);
            output.writeLong(verifiedSequence);
            output.writeInt(verificationFingerprint.length);
            output.write(verificationFingerprint);
            output.flush();
            return new MaintenanceRuntimeSnapshot(revision,
                    MaintenanceStateFingerprints.sha256(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint maintenance runtime state", exception);
        }
    }

    private static void requireMaintenanceFields(CompoundTag tag) {
        if (!tag.contains("maintenanceRevision", Tag.TAG_LONG)
                || !tag.contains("maintenanceRequested", Tag.TAG_BYTE)
                || !tag.contains("maintenanceIncident", Tag.TAG_STRING)
                || !tag.contains("maintenanceVerifiedSequence", Tag.TAG_LONG)
                || !tag.contains("maintenanceVerificationFingerprint", Tag.TAG_BYTE_ARRAY)
                || tag.contains("lastMaintenanceCommand")
                && !tag.hasUUID("lastMaintenanceCommand")) {
            throw new IllegalStateException(
                    "Escrow maintenance runtime fields are malformed");
        }
    }

    private static boolean readStrictBoolean(CompoundTag tag, String key) {
        byte value = tag.getByte(key);
        if (value != 0 && value != 1) {
            throw new IllegalStateException(
                    "Escrow maintenance runtime boolean is invalid");
        }
        return value == 1;
    }

    private record RuntimeStateSnapshot(UUID journalLineage,
                                        long lastAppliedSequence,
                                        UUID checkpointId,
                                        UUID checkpointSourceLineage,
                                        long checkpointBaseSequence,
                                        long maintenanceRevision,
                                        boolean maintenanceRequested,
                                        UUID lastMaintenanceCommandId,
                                        String maintenanceIncident,
                                        long maintenanceVerifiedSequence,
                                        byte[] maintenanceVerificationFingerprint) {
        private RuntimeStateSnapshot {
            maintenanceVerificationFingerprint =
                    maintenanceVerificationFingerprint.clone();
        }

        @Override
        public byte[] maintenanceVerificationFingerprint() {
            return maintenanceVerificationFingerprint.clone();
        }
    }

    private record MaintenanceValues(long revision,
                                     boolean requested,
                                     UUID commandId,
                                     String incident,
                                     long verifiedSequence,
                                     byte[] verificationFingerprint) {
        private MaintenanceValues {
            verificationFingerprint = verificationFingerprint.clone();
        }

        @Override
        public byte[] verificationFingerprint() {
            return verificationFingerprint.clone();
        }

        private MaintenanceRuntimeSnapshot snapshot() {
            return snapshotOf(revision, requested, commandId, incident,
                    verifiedSequence, verificationFingerprint);
        }
    }
}
