package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.catalog.CatalogStockAuthority;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import com.enviouse.futureshops.server.escrow.stock.CatalogStockState;
import com.enviouse.futureshops.server.escrow.stock.CatalogStockStatus;
import com.enviouse.futureshops.server.escrow.stock.StockCommandResult;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import com.enviouse.futureshops.server.escrow.stock.StockMutationCommand;
import com.enviouse.futureshops.server.escrow.stock.StockMutationType;
import com.enviouse.futureshops.server.escrow.stock.StockStoreSnapshot;

import java.util.Objects;
import java.util.Optional;

public final class DurableCatalogStockAuthority
        implements CatalogStockAuthority {
    private final EscrowRuntimeService runtime;
    private final String seedChecksum;
    private final long completionSequence;

    public DurableCatalogStockAuthority(
            EscrowRuntimeService runtime,
            CatalogStockMigrationSavedData migration
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        migration = Objects.requireNonNull(migration, "migration");
        if (migration.stage() != CatalogStockMigrationStage.COMPLETE) {
            throw new IllegalStateException(
                    "Catalog stock migration is not complete");
        }
        if (!runtime.isReady()) {
            throw new IllegalStateException(
                    "Escrow runtime is not ready for catalog stock");
        }
        seedChecksum = migration.snapshot().fingerprint();
        completionSequence = migration.completionSequence();
        StockStoreSnapshot snapshot = runtime.stockSnapshot();
        if (snapshot.storeRevision() < completionSequence
                || !runtime.stockConservationReport().conserved()) {
            throw new IllegalStateException(
                    "Durable catalog stock failed activation checks");
        }
    }

    @Override
    public String seedChecksum() {
        return seedChecksum;
    }

    public long completionSequence() {
        return completionSequence;
    }

    @Override
    public int currentStock(String shopId, String listingId) {
        CatalogStockState state = runtime.stockListing(
                new StockKey(shopId, listingId)).orElse(null);
        if (state == null || state.status() != CatalogStockStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Durable catalog stock listing is unavailable");
        }
        return Math.toIntExact(state.displayQuantity());
    }

    public Optional<CatalogStockState> listing(StockKey key) {
        return runtime.stockListing(Objects.requireNonNull(key, "key"));
    }

    public StockCommandResult commitTransactionScoped(
            StockMutationCommand command
    ) {
        command = Objects.requireNonNull(command, "command");
        StockMutationType operation = command.operation();
        if (operation == StockMutationType.SEED
                || operation == StockMutationType.RELOAD_RECONCILE) {
            throw new IllegalArgumentException(
                    "Catalog seed mutations require the cutover lane");
        }
        return runtime.commitStockMutation(command);
    }

    public StockStoreSnapshot snapshot() {
        return runtime.stockSnapshot();
    }
}
