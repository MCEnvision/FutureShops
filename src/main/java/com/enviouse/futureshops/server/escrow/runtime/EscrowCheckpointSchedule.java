package com.enviouse.futureshops.server.escrow.runtime;

public final class EscrowCheckpointSchedule {
    public static final int SERVER_TICKS_PER_SECOND = 20;

    private long elapsedReadyTicks;

    public boolean tick(int intervalSeconds, boolean eligible) {
        return tick(intervalSeconds, eligible, false);
    }

    public boolean tick(int intervalSeconds, boolean eligible, boolean thresholdReached) {
        if (intervalSeconds <= 0) {
            throw new IllegalArgumentException("Escrow checkpoint interval must be positive");
        }
        long intervalTicks = Math.multiplyExact(
                (long) intervalSeconds, SERVER_TICKS_PER_SECOND);
        if (!eligible) {
            return false;
        }
        if (thresholdReached) {
            return true;
        }
        if (elapsedReadyTicks < intervalTicks) {
            elapsedReadyTicks++;
        }
        return elapsedReadyTicks >= intervalTicks;
    }

    public void checkpointCompleted() {
        elapsedReadyTicks = 0L;
    }

    public long elapsedReadyTicks() {
        return elapsedReadyTicks;
    }
}
