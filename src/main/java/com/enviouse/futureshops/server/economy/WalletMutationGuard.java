package com.enviouse.futureshops.server.economy;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class WalletMutationGuard {
    private static final ThreadLocal<Set<UUID>> ACTIVE_ACCOUNTS =
            ThreadLocal.withInitial(HashSet::new);

    private WalletMutationGuard() {
    }

    public static Optional<Lease> tryAcquire(
            Collection<UUID> playerIds
    ) {
        Objects.requireNonNull(playerIds, "playerIds");
        LinkedHashSet<UUID> distinct = new LinkedHashSet<>();
        for (UUID playerId : playerIds) {
            distinct.add(Objects.requireNonNull(playerId, "playerId"));
        }
        if (distinct.isEmpty()) {
            throw new IllegalArgumentException(
                    "Wallet mutation guard requires an account");
        }
        Set<UUID> active = ACTIVE_ACCOUNTS.get();
        if (distinct.stream().anyMatch(active::contains)) {
            return Optional.empty();
        }
        active.addAll(distinct);
        return Optional.of(new Lease(Set.copyOf(distinct),
                Thread.currentThread()));
    }

    public static final class Lease implements AutoCloseable {
        private final Set<UUID> playerIds;
        private final Thread ownerThread;
        private boolean closed;

        private Lease(Set<UUID> playerIds, Thread ownerThread) {
            this.playerIds = playerIds;
            this.ownerThread = ownerThread;
        }

        @Override
        public void close() {
            if (closed || Thread.currentThread() != ownerThread) {
                throw new IllegalStateException(
                        "Wallet mutation guard lease is invalid");
            }
            closed = true;
            Set<UUID> active = ACTIVE_ACCOUNTS.get();
            if (!active.containsAll(playerIds)) {
                throw new IllegalStateException(
                        "Wallet mutation guard state is invalid");
            }
            active.removeAll(playerIds);
            if (active.isEmpty()) {
                ACTIVE_ACCOUNTS.remove();
            }
        }
    }
}
