package com.enviouse.futureshops.server.escrow.item.runtime;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ItemInventoryOnlineRecoveryBatch {
    public static final int MAX_BATCH = 10_000;

    private ItemInventoryOnlineRecoveryBatch() {
    }

    public static int recover(
            ExactItemInventoryRuntime runtime,
            List<? extends ItemInventoryAccess> onlinePlayers,
            int maximumWork
    ) {
        Objects.requireNonNull(runtime, "runtime");
        if (maximumWork <= 0 || maximumWork > MAX_BATCH) {
            throw new IllegalArgumentException(
                    "Invalid item inventory recovery work limit");
        }
        List<? extends ItemInventoryAccess> players = List.copyOf(
                Objects.requireNonNull(onlinePlayers, "onlinePlayers"))
                .stream()
                .sorted(Comparator.comparing(ItemInventoryAccess::playerId))
                .toList();
        Set<UUID> playerIds = new HashSet<>();
        int remaining = maximumWork;
        int examined = 0;
        for (ItemInventoryAccess player : players) {
            UUID playerId = Objects.requireNonNull(
                    player.playerId(), "playerId");
            if (!playerIds.add(playerId)) {
                throw new IllegalArgumentException(
                        "Online item inventory recovery repeats a player");
            }
            if (remaining == 0) {
                continue;
            }
            int playerLimit = Math.min(remaining,
                    ExactItemInventoryRuntime.MAX_RECOVERY_BATCH);
            int recovered = runtime.recoverPrepared(
                    player, playerLimit).size();
            if (recovered > playerLimit) {
                throw new IllegalStateException(
                        "Item inventory recovery exceeded its work limit");
            }
            examined += recovered;
            remaining -= recovered;
        }
        return examined;
    }
}
