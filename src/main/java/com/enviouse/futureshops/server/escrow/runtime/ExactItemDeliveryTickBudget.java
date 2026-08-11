package com.enviouse.futureshops.server.escrow.runtime;

import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class ExactItemDeliveryTickBudget {
    private static final Map<MinecraftServer, State> STATES =
            new WeakHashMap<>();

    private ExactItemDeliveryTickBudget() {
    }

    public static int remaining(MinecraftServer server, int limit) {
        Objects.requireNonNull(server, "server");
        return state(server).remaining(server.getTickCount(), limit);
    }

    public static boolean tryAcquire(MinecraftServer server, int limit) {
        Objects.requireNonNull(server, "server");
        return state(server).tryAcquire(server.getTickCount(), limit);
    }

    private static State state(MinecraftServer server) {
        synchronized (STATES) {
            return STATES.computeIfAbsent(server, ignored -> new State());
        }
    }

    static final class State {
        private int tick = Integer.MIN_VALUE;
        private int used;

        synchronized int remaining(int currentTick, int limit) {
            reset(currentTick);
            return Math.max(0, Math.max(0, limit) - used);
        }

        synchronized boolean tryAcquire(int currentTick, int limit) {
            reset(currentTick);
            int safeLimit = Math.max(0, limit);
            if (used >= safeLimit) {
                return false;
            }
            used++;
            return true;
        }

        private void reset(int currentTick) {
            if (tick == currentTick) {
                return;
            }
            tick = currentTick;
            used = 0;
        }
    }
}
