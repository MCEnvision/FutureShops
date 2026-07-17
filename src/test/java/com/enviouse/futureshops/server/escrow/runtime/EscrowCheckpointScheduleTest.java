package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EscrowCheckpointScheduleTest {
    @Test
    void countsOnlyEligibleReadyTicksWithoutSleeping() {
        EscrowCheckpointSchedule schedule = new EscrowCheckpointSchedule();

        for (int tick = 0; tick < 19; tick++) {
            assertFalse(schedule.tick(1, true));
        }
        assertFalse(schedule.tick(1, false));
        assertEquals(19L, schedule.elapsedReadyTicks());
        assertTrue(schedule.tick(1, true));
        assertTrue(schedule.tick(1, true));
    }

    @Test
    void successfulCheckpointResetsTheMonotonicSchedule() {
        EscrowCheckpointSchedule schedule = new EscrowCheckpointSchedule();
        for (int tick = 0; tick < 20; tick++) {
            schedule.tick(1, true);
        }
        assertTrue(schedule.tick(1, true));

        schedule.checkpointCompleted();

        assertEquals(0L, schedule.elapsedReadyTicks());
        for (int tick = 0; tick < 19; tick++) {
            assertFalse(schedule.tick(1, true));
        }
        assertTrue(schedule.tick(1, true));
    }

    @Test
    void rejectsNonpositiveIntervals() {
        EscrowCheckpointSchedule schedule = new EscrowCheckpointSchedule();

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> schedule.tick(0, true));
    }

    @Test
    void journalThresholdForcesOnlyAnEligibleCheckpoint() {
        EscrowCheckpointSchedule schedule = new EscrowCheckpointSchedule();

        assertFalse(schedule.tick(30, false, true));
        assertEquals(0L, schedule.elapsedReadyTicks());
        assertTrue(schedule.tick(30, true, true));
        assertEquals(0L, schedule.elapsedReadyTicks());
    }

    @Test
    void journalThresholdPreservesPartialIntervalUntilCheckpointCompletes() {
        EscrowCheckpointSchedule schedule = new EscrowCheckpointSchedule();
        for (int tick = 0; tick < 7; tick++) {
            assertFalse(schedule.tick(30, true, false));
        }

        assertTrue(schedule.tick(30, true, true));
        assertEquals(7L, schedule.elapsedReadyTicks());
        assertTrue(schedule.tick(30, true, true));
        assertEquals(7L, schedule.elapsedReadyTicks());

        schedule.checkpointCompleted();
        assertEquals(0L, schedule.elapsedReadyTicks());
        assertFalse(schedule.tick(30, true, false));
        assertEquals(1L, schedule.elapsedReadyTicks());
    }

    @Test
    void shortenedIntervalUsesAlreadyElapsedEligibleTicks() {
        EscrowCheckpointSchedule schedule = new EscrowCheckpointSchedule();
        for (int tick = 0; tick < 20; tick++) {
            assertFalse(schedule.tick(2, true, false));
        }

        assertTrue(schedule.tick(1, true, false));
        assertEquals(20L, schedule.elapsedReadyTicks());
    }

    @Test
    void invalidIntervalIsRejectedEvenWhenThresholdWouldForceCheckpoint() {
        EscrowCheckpointSchedule schedule = new EscrowCheckpointSchedule();

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> schedule.tick(0, false, true));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> schedule.tick(-1, true, true));
    }
}
