package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.catalog.CatalogStockAuthorityMode;
import com.enviouse.futureshops.catalog.ShopCatalog;
import com.enviouse.futureshops.server.escrow.runtime.EscrowRuntimeService;
import net.minecraft.server.MinecraftServer;

import java.time.Instant;
import java.util.Objects;

public final class CatalogStockCutoverCoordinator {
    public CatalogStockCutoverReadiness readiness(
            CatalogStockMigrationSavedData migration,
            CatalogStockActivationCoverage coverage
    ) {
        Objects.requireNonNull(migration, "migration");
        Objects.requireNonNull(coverage, "coverage");
        if (ShopCatalog.stockAuthorityMode()
                == CatalogStockAuthorityMode.DURABLE) {
            return CatalogStockCutoverReadiness.ACTIVE;
        }
        if (migration.stage() == CatalogStockMigrationStage.FAILED) {
            return CatalogStockCutoverReadiness.FAILED;
        }
        if (!coverage.complete()) {
            return CatalogStockCutoverReadiness
                    .WAITING_FOR_TRANSACTIONAL_CALLERS;
        }
        return switch (migration.stage()) {
            case VERIFIED ->
                    CatalogStockCutoverReadiness.CHECKPOINT_REQUIRED;
            case COMPLETE ->
                    CatalogStockCutoverReadiness.READY_TO_ACTIVATE;
            default -> CatalogStockCutoverReadiness.MIGRATION_REQUIRED;
        };
    }

    public CatalogStockSeedSnapshot freezeSource(
            CatalogStockMigrationSavedData migration,
            CatalogStockActivationCoverage coverage
    ) {
        Objects.requireNonNull(migration, "migration");
        requireCoverage(coverage);
        if (ShopCatalog.stockAuthorityMode()
                == CatalogStockAuthorityMode.DURABLE) {
            throw new IllegalStateException(
                    "Durable catalog stock is already active");
        }
        CatalogStockSeedSnapshot source;
        if (migration.stage()
                == CatalogStockMigrationStage.UNINITIALIZED
                || migration.canRetryMaterializedState()) {
            return ShopCatalog.captureAndFreezeStockForCutover();
        } else {
            source = migration.snapshot();
        }
        ShopCatalog.freezeStockForCutover(source.fingerprint());
        return source;
    }

    public DurableCatalogStockAuthority activate(
            EscrowRuntimeService runtime,
            CatalogStockMigrationSavedData migration,
            CatalogStockActivationCoverage coverage
    ) {
        requireCoverage(coverage);
        if (migration.stage() != CatalogStockMigrationStage.COMPLETE) {
            throw new IllegalStateException(
                    "Catalog stock migration has not completed");
        }
        CatalogStockVerification verification =
                new CatalogStockSeedGateway(
                        new LiveEscrowCatalogStockSeedBackend(runtime))
                        .verifyRestoredLineage(migration.snapshot());
        if (!verification.valid()
                || verification.completionSequence()
                < migration.completionSequence()) {
            throw new IllegalStateException(
                    "Catalog stock changed before authority activation");
        }
        DurableCatalogStockAuthority authority =
                new DurableCatalogStockAuthority(runtime, migration);
        ShopCatalog.activateDurableStockAuthority(authority, coverage);
        return authority;
    }

    public CatalogStockMigrationResult migrateBatch(
            MinecraftServer server,
            EscrowRuntimeService runtime,
            CatalogStockActivationCoverage coverage,
            int batchSize
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(runtime, "runtime");
        requireCoverage(coverage);
        if (!server.isSameThread()) {
            throw new IllegalStateException(
                    "Catalog stock cutover requires the server thread");
        }
        CatalogStockMigrationSavedData migration =
                CatalogStockMigrationSavedData.get(server);
        if (!runtime.isReady()) {
            return migration.result(0,
                    "Escrow stock is not ready");
        }
        CatalogStockSeedSnapshot source =
                freezeSource(migration, coverage);
        CatalogStockMigrator migrator = new CatalogStockMigrator(
                new CatalogStockSeedGateway(
                        new LiveEscrowCatalogStockSeedBackend(runtime)));
        return migrator.runBatch(
                source, migration, batchSize, Instant.now(),
                new LiveCatalogStockMigrationDurabilityBarrier(
                        server, runtime));
    }

    private static void requireCoverage(
            CatalogStockActivationCoverage coverage
    ) {
        coverage = Objects.requireNonNull(coverage, "coverage");
        if (!coverage.complete()) {
            throw new IllegalStateException(
                    "Catalog stock callers are not transaction scoped");
        }
    }
}
