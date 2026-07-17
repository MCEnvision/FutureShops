package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairCommand;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairPayload;

import java.security.MessageDigest;
import java.util.Objects;

public final class EscrowRuntimeMaintenanceController
        implements MaintenanceRuntimeMutationHandler {
    private final EscrowRuntimeSavedData runtimeMetadata;
    private final EscrowMutationPermit mutationPermit;
    private EscrowMaintenanceLiveGuard liveGuard;

    public EscrowRuntimeMaintenanceController(EscrowRuntimeSavedData runtimeMetadata) {
        this(runtimeMetadata, null);
    }

    EscrowRuntimeMaintenanceController(EscrowRuntimeSavedData runtimeMetadata,
                                       EscrowMutationPermit mutationPermit) {
        this.runtimeMetadata = Objects.requireNonNull(runtimeMetadata, "runtimeMetadata");
        this.mutationPermit = mutationPermit;
    }

    public synchronized void attach(EscrowMaintenanceLiveGuard guard) {
        Objects.requireNonNull(guard, "guard");
        if (liveGuard != null && liveGuard != guard) {
            throw new EscrowRuntimeException(
                    "Escrow maintenance live guard is already attached");
        }
        liveGuard = guard;
    }

    public synchronized boolean maintenanceRequested() {
        return runtimeMetadata.maintenanceRequested();
    }

    public synchronized EscrowGlobalVerificationSnapshot globalVerification() {
        return requireLiveGuard().globalVerification();
    }

    @Override
    public synchronized MaintenanceRuntimeSnapshot snapshot() {
        return runtimeMetadata.maintenanceSnapshot();
    }

    @Override
    public synchronized MaintenanceRuntimeSnapshot plan(MaintenanceRepairCommand command) {
        Objects.requireNonNull(command, "command");
        EscrowMaintenanceLiveGuard guard = requireLiveGuard();
        if (!guard.journalHealthyAndAligned()) {
            throw new EscrowRuntimeException(
                    "Escrow journal is not healthy and aligned for maintenance");
        }
        if (command.payload() instanceof MaintenanceRepairPayload.EnterMaintenance) {
            return runtimeMetadata.previewMaintenance(command);
        }
        if (command.payload() instanceof MaintenanceRepairPayload.VerifyAndResume resume) {
            if (!runtimeMetadata.maintenanceRequested()
                    && !guard.domainMaintenanceActive()) {
                throw new EscrowRuntimeException(
                        "Escrow runtime is not in domain maintenance");
            }
            if (!guard.recoveryClear() || !guard.conservationVerified()) {
                throw new EscrowRuntimeException(
                        "Escrow global verification has unresolved work or imbalance");
            }
            EscrowGlobalVerificationSnapshot verification = guard.globalVerification();
            if (verification.journalSequence()
                    != runtimeMetadata.lastAppliedSequence()
                    || resume.verifiedJournalSequence() != verification.journalSequence()
                    || !MessageDigest.isEqual(
                    resume.verificationFingerprint().bytes(),
                    verification.fingerprint().bytes())) {
                throw new EscrowRuntimeException(
                        "Escrow resume verification does not match current global state");
            }
            return runtimeMetadata.previewMaintenance(command);
        }
        throw new EscrowRuntimeException(
                "Escrow runtime maintenance payload is unsupported");
    }

    @Override
    public synchronized void apply(MaintenanceRepairCommand command,
                                   MaintenanceRuntimeSnapshot result) {
        if (mutationPermit == null) {
            runtimeMetadata.applyMaintenance(command, result);
            return;
        }
        try (EscrowMutationPermit.Scope ignored = mutationPermit.activate()) {
            runtimeMetadata.applyMaintenance(command, result);
        }
    }

    @Override
    public synchronized boolean isCurrent(MaintenanceRuntimeSnapshot result) {
        return runtimeMetadata.maintenanceIsCurrent(result);
    }

    @Override
    public synchronized boolean wasApplied(MaintenanceRuntimeSnapshot result) {
        return runtimeMetadata.maintenanceWasApplied(result);
    }

    private EscrowMaintenanceLiveGuard requireLiveGuard() {
        if (liveGuard == null) {
            throw new EscrowRuntimeException(
                    "Escrow maintenance live guard is unavailable");
        }
        return liveGuard;
    }
}
