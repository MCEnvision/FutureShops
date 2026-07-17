package com.enviouse.futureshops.server.escrow.runtime;

import com.enviouse.futureshops.server.escrow.journal.JournalRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowMaintenanceCommitLaneTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void scopedLaneAcceptsOnlyMaintenanceEventsAndNormalLaneRejectsThem() {
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        RecordingApplier applier = new RecordingApplier();
        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(
                temporaryDirectory.resolve("journal.wal"), cursor, applier);
        coordinator.start();
        UUID commandId = UUID.randomUUID();
        EscrowJournalEvent maintenance = new EscrowJournalEvent(
                EscrowJournalEventType.MAINTENANCE_REPAIR, new byte[]{1});

        assertThrows(IllegalArgumentException.class,
                () -> coordinator.commit(commandId, maintenance));
        EscrowCommitResult result = coordinator.commitMaintenanceRepair(
                commandId, maintenance);

        assertTrue(result.record().isPresent());
        assertEquals(1, applier.applied);
        assertEquals(2L, cursor.lastAppliedSequence());
        assertThrows(IllegalArgumentException.class,
                () -> coordinator.commitMaintenanceRepair(commandId,
                        new EscrowJournalEvent(
                                EscrowJournalEventType.LEDGER_APPLY, new byte[]{1})));
        coordinator.close();
    }

    @Test
    void cursorMisalignmentPoisonsTheJournalBeforeMaintenanceAppend() {
        EscrowRuntimeSavedData cursor = new EscrowRuntimeSavedData();
        RecordingApplier applier = new RecordingApplier();
        EscrowRuntimeCoordinator coordinator = new EscrowRuntimeCoordinator(
                temporaryDirectory.resolve("misaligned.wal"), cursor, applier);
        coordinator.start();
        cursor.advance(cursor.journalLineage().orElseThrow(), 2L);
        EscrowJournalEvent maintenance = new EscrowJournalEvent(
                EscrowJournalEventType.MAINTENANCE_REPAIR, new byte[]{1});

        assertFalse(coordinator.journalHealthyAndAligned());
        assertThrows(EscrowRuntimeException.class,
                () -> coordinator.commitMaintenanceRepair(UUID.randomUUID(), maintenance));
        assertEquals(EscrowRuntimeState.MAINTENANCE, coordinator.state());
        assertEquals(0, applier.applied);
        coordinator.close();
    }

    private static final class RecordingApplier implements EscrowMutationApplier {
        private int applied;

        @Override
        public EscrowPreflightResult preflight(UUID transactionId,
                                               EscrowJournalEvent event) {
            return EscrowPreflightResult.APPLY;
        }

        @Override
        public void apply(JournalRecord record, EscrowJournalEvent event) {
            applied++;
        }
    }
}
