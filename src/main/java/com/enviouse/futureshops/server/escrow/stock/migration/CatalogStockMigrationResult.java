package com.enviouse.futureshops.server.escrow.stock.migration;

import java.util.Objects;

public record CatalogStockMigrationResult(
        CatalogStockMigrationStage stage,
        int processedEntries,
        int nextEntryIndex,
        int totalEntries,
        CatalogStockMigrationFailure failure,
        String detail,
        long completionSequence
) {
    public CatalogStockMigrationResult {
        stage = Objects.requireNonNull(stage, "stage");
        failure = Objects.requireNonNull(failure, "failure");
        detail = Objects.requireNonNull(detail, "detail");
        if (processedEntries < 0 || nextEntryIndex < 0
                || totalEntries < 0 || nextEntryIndex > totalEntries
                || completionSequence < -1L) {
            throw new IllegalArgumentException(
                    "Catalog stock migration result is invalid");
        }
    }
}
