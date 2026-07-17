package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.admin.MaintenanceExpectedState;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairCommand;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairPayload;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceRepairTarget;
import com.enviouse.futureshops.server.escrow.admin.MaintenanceStateFingerprint;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowRuntimeMaintenanceControllerTest {
    private static final Instant NOW = Instant.parse("2026-07-17T20:00:00Z");

    @Test
    void enterAndVerifiedResumePersistExactMonotonicRuntimeState() {
        EscrowRuntimeSavedData metadata = metadataAtSequence(5L);
        EscrowRuntimeMaintenanceController controller =
                new EscrowRuntimeMaintenanceController(metadata);
        Guard guard = new Guard();
        controller.attach(guard);
        MaintenanceRepairCommand enter = command(
                MaintenanceExpectedState.revision(0L),
                new MaintenanceRepairPayload.EnterMaintenance("incident.44"), NOW);

        MaintenanceRuntimeSnapshot entered = controller.plan(enter);
        controller.apply(enter, entered);

        assertEquals(1L, entered.revision());
        assertTrue(controller.maintenanceRequested());
        assertTrue(controller.isCurrent(entered));
        metadata.advance(metadata.journalLineage().orElseThrow(), 6L);

        EscrowRuntimeSavedData reloaded = EscrowRuntimeSavedData.load(
                metadata.save(new CompoundTag()));
        EscrowRuntimeMaintenanceController reloadedController =
                new EscrowRuntimeMaintenanceController(reloaded);
        Guard reloadedGuard = new Guard();
        reloadedGuard.verification = new EscrowGlobalVerificationSnapshot(
                6L, fingerprint(30));
        reloadedController.attach(reloadedGuard);
        assertTrue(reloadedController.maintenanceRequested());
        assertEquals(entered, reloadedController.snapshot());

        MaintenanceRepairCommand resume = command(
                MaintenanceExpectedState.revision(1L),
                new MaintenanceRepairPayload.VerifyAndResume(6L, fingerprint(30)),
                NOW.plusSeconds(1));
        MaintenanceRuntimeSnapshot resumed = reloadedController.plan(resume);
        reloadedController.apply(resume, resumed);

        assertEquals(2L, resumed.revision());
        assertFalse(reloadedController.maintenanceRequested());
        assertTrue(reloadedController.wasApplied(entered));
        assertTrue(reloadedController.isCurrent(resumed));
    }

    @Test
    void resumeFailsForJournalRecoveryConservationSequenceOrFingerprintMismatch() {
        EscrowRuntimeSavedData metadata = metadataAtSequence(3L);
        EscrowRuntimeMaintenanceController controller =
                new EscrowRuntimeMaintenanceController(metadata);
        Guard guard = new Guard();
        controller.attach(guard);
        MaintenanceRepairCommand enter = command(MaintenanceExpectedState.revision(0L),
                new MaintenanceRepairPayload.EnterMaintenance("incident.45"), NOW);
        controller.apply(enter, controller.plan(enter));
        metadata.advance(metadata.journalLineage().orElseThrow(), 4L);
        guard.verification = new EscrowGlobalVerificationSnapshot(4L, fingerprint(40));
        MaintenanceRepairCommand resume = command(MaintenanceExpectedState.revision(1L),
                new MaintenanceRepairPayload.VerifyAndResume(4L, fingerprint(40)),
                NOW.plusSeconds(1));

        guard.healthy = false;
        assertThrows(EscrowRuntimeException.class, () -> controller.plan(resume));
        guard.healthy = true;
        guard.recoveryClear = false;
        assertThrows(EscrowRuntimeException.class, () -> controller.plan(resume));
        guard.recoveryClear = true;
        guard.conserved = false;
        assertThrows(EscrowRuntimeException.class, () -> controller.plan(resume));
        guard.conserved = true;

        MaintenanceRepairCommand badSequence = command(
                MaintenanceExpectedState.revision(1L),
                new MaintenanceRepairPayload.VerifyAndResume(3L, fingerprint(40)),
                NOW.plusSeconds(1));
        assertThrows(EscrowRuntimeException.class,
                () -> controller.plan(badSequence));
        MaintenanceRepairCommand badFingerprint = command(
                MaintenanceExpectedState.revision(1L),
                new MaintenanceRepairPayload.VerifyAndResume(4L, fingerprint(41)),
                NOW.plusSeconds(1));
        assertThrows(EscrowRuntimeException.class,
                () -> controller.plan(badFingerprint));
    }

    @Test
    void resultFromAnotherCommandCannotBeApplied() {
        EscrowRuntimeSavedData metadata = metadataAtSequence(1L);
        EscrowRuntimeMaintenanceController controller =
                new EscrowRuntimeMaintenanceController(metadata);
        controller.attach(new Guard());
        MaintenanceRepairCommand first = command(MaintenanceExpectedState.revision(0L),
                new MaintenanceRepairPayload.EnterMaintenance("incident.one"), NOW);
        MaintenanceRepairCommand second = command(MaintenanceExpectedState.revision(0L),
                new MaintenanceRepairPayload.EnterMaintenance("incident.two"), NOW);

        MaintenanceRuntimeSnapshot firstResult = controller.plan(first);

        assertThrows(EscrowRuntimeException.class,
                () -> controller.apply(second, firstResult));
        assertFalse(controller.maintenanceRequested());
    }

    private static EscrowRuntimeSavedData metadataAtSequence(long sequence) {
        EscrowRuntimeSavedData metadata = new EscrowRuntimeSavedData();
        UUID lineage = UUID.randomUUID();
        metadata.establishLineage(lineage, 1L);
        for (long value = 2L; value <= sequence; value++) {
            metadata.advance(lineage, value);
        }
        return metadata;
    }

    private static MaintenanceRepairCommand command(MaintenanceExpectedState expected,
                                                    MaintenanceRepairPayload payload,
                                                    Instant createdAt) {
        return MaintenanceRepairCommand.create(UUID.randomUUID(), "console",
                "Verified runtime repair", true, createdAt,
                MaintenanceRepairTarget.runtime(), expected, payload, true, "Applied");
    }

    private static MaintenanceStateFingerprint fingerprint(int seed) {
        byte[] value = new byte[MaintenanceStateFingerprint.BYTE_LENGTH];
        for (int index = 0; index < value.length; index++) {
            value[index] = (byte) (seed + index);
        }
        return MaintenanceStateFingerprint.of(value);
    }

    private static final class Guard implements EscrowMaintenanceLiveGuard {
        private boolean healthy = true;
        private boolean domainMaintenance;
        private boolean recoveryClear = true;
        private boolean conserved = true;
        private EscrowGlobalVerificationSnapshot verification =
                new EscrowGlobalVerificationSnapshot(0L, fingerprint(10));

        @Override
        public boolean journalHealthyAndAligned() {
            return healthy;
        }

        @Override
        public boolean domainMaintenanceActive() {
            return domainMaintenance;
        }

        @Override
        public boolean recoveryClear() {
            return recoveryClear;
        }

        @Override
        public boolean conservationVerified() {
            return conserved;
        }

        @Override
        public EscrowGlobalVerificationSnapshot globalVerification() {
            return verification;
        }
    }
}
