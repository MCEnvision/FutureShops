package com.enviouse.futureshops.server.escrow.item.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

final class PlayerItemMutationLocks {
    private final Map<UUID, Cell> cells = new HashMap<>();

    <T> T withLock(UUID playerId, Supplier<T> action) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(action, "action");
        Cell cell;
        synchronized (cells) {
            cell = cells.computeIfAbsent(playerId, ignored -> new Cell());
            cell.users = Math.addExact(cell.users, 1);
        }
        if (cell.lock.isHeldByCurrentThread()) {
            releaseUser(playerId, cell);
            throw new IllegalStateException(
                    "Nested item inventory mutation is not allowed");
        }
        cell.lock.lock();
        try {
            return action.get();
        } finally {
            cell.lock.unlock();
            releaseUser(playerId, cell);
        }
    }

    int trackedPlayers() {
        synchronized (cells) {
            return cells.size();
        }
    }

    private void releaseUser(UUID playerId, Cell cell) {
        synchronized (cells) {
            cell.users = Math.subtractExact(cell.users, 1);
            if (cell.users == 0 && !cell.lock.isLocked()) {
                cells.remove(playerId, cell);
            }
        }
    }

    private static final class Cell {
        private final ReentrantLock lock = new ReentrantLock();
        private int users;
    }
}
