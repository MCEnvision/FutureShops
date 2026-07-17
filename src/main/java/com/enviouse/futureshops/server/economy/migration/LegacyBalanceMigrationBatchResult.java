package com.enviouse.futureshops.server.economy.migration;

import java.util.Objects;
import java.util.List;
import java.util.UUID;

public record LegacyBalanceMigrationBatchResult(
        LegacyBalanceMigrationStage stage,
        int processedEntries,
        int nextEntryIndex,
        int totalEntries,
        LegacyBalanceMigrationFailure failure,
        String detail,
        List<UUID> affectedPlayers
) {
    public LegacyBalanceMigrationBatchResult {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(failure, "failure");
        detail = Objects.requireNonNull(detail, "detail");
        affectedPlayers = List.copyOf(
                Objects.requireNonNull(affectedPlayers, "affectedPlayers"));
        if (processedEntries < 0 || nextEntryIndex < 0
                || totalEntries < 0 || nextEntryIndex > totalEntries) {
            throw new IllegalArgumentException("Legacy migration batch result is invalid");
        }
    }
}
