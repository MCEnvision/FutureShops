package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.server.escrow.stock.StockKey;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class CatalogStockMigrationIds {
    private static final String DOMAIN =
            "futureshops.catalog.stock.migration.v1.";

    private CatalogStockMigrationIds() {
    }

    public static UUID seedRequest(
            CatalogStockSeedSnapshot snapshot,
            CatalogStockSeedEntry entry
    ) {
        return entryId("seed", snapshot, entry.key());
    }

    public static UUID depletionTransaction(
            CatalogStockSeedSnapshot snapshot,
            CatalogStockSeedEntry entry
    ) {
        return entryId("depletion.transaction", snapshot, entry.key());
    }

    public static UUID depletionReserveRequest(
            CatalogStockSeedSnapshot snapshot,
            CatalogStockSeedEntry entry
    ) {
        return entryId("depletion.reserve", snapshot, entry.key());
    }

    public static UUID depletionCommitRequest(
            CatalogStockSeedSnapshot snapshot,
            CatalogStockSeedEntry entry
    ) {
        return entryId("depletion.commit", snapshot, entry.key());
    }

    public static UUID entryCompletion(
            CatalogStockSeedSnapshot snapshot,
            CatalogStockSeedEntry entry
    ) {
        return entryId("entry.complete", snapshot, entry.key());
    }

    public static UUID reconcileRequest(CatalogStockSeedSnapshot snapshot) {
        return deterministic("reconcile." + snapshot.fingerprint());
    }

    private static UUID entryId(
            String operation,
            CatalogStockSeedSnapshot snapshot,
            StockKey key
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(key, "key");
        return deterministic(operation + "." + snapshot.fingerprint()
                + "." + key.canonicalValue());
    }

    private static UUID deterministic(String value) {
        return UUID.nameUUIDFromBytes(
                (DOMAIN + value).getBytes(StandardCharsets.UTF_8));
    }
}
