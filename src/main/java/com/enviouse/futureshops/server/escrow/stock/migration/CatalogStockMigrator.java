package com.enviouse.futureshops.server.escrow.stock.migration;

import java.time.Instant;
import java.util.Objects;

public final class CatalogStockMigrator {
    public static final int MAXIMUM_BATCH_SIZE = 1024;

    private final CatalogStockSeedGateway gateway;

    public CatalogStockMigrator(CatalogStockSeedGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    public CatalogStockMigrationResult runBatch(
            CatalogStockSeedSnapshot source,
            CatalogStockMigrationSavedData migration,
            int requestedBatchSize,
            Instant now,
            CatalogStockMigrationDurabilityBarrier durabilityBarrier
    ) {
        source = Objects.requireNonNull(source, "source");
        Objects.requireNonNull(migration, "migration");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(durabilityBarrier, "durabilityBarrier");
        if (requestedBatchSize <= 0) {
            throw new IllegalArgumentException(
                    "Catalog stock migration batch size must be positive");
        }
        int batchSize = Math.min(
                requestedBatchSize, MAXIMUM_BATCH_SIZE);
        if (migration.stage()
                == CatalogStockMigrationStage.COMPLETE) {
            return migration.result(0, migration.failureDetail());
        }
        if (migration.stage() == CatalogStockMigrationStage.FAILED
                && !migration.canRetryMaterializedState()) {
            return migration.result(0, migration.failureDetail());
        }
        if (!gateway.ready()) {
            return migration.result(0,
                    "Escrow stock is not ready");
        }

        boolean verifiedMaterializedState = false;
        if (migration.canRetryMaterializedState()) {
            CatalogStockVerification verification = gateway.verify(source);
            if (!verification.valid()) {
                String detail = materializedFailureDetail(verification);
                migration.recordMaterializedStateFailure(detail);
                durabilityBarrier.flush();
                return migration.result(0, detail);
            }
            migration.retryMaterializedState();
            durabilityBarrier.flush();
            verifiedMaterializedState = true;
        }

        if (migration.stage()
                == CatalogStockMigrationStage.UNINITIALIZED) {
            if (!gateway.pristine() && !verifiedMaterializedState) {
                CatalogStockVerification verification = gateway.verify(source);
                if (!verification.valid()) {
                    return fail(migration, durabilityBarrier,
                            CatalogStockMigrationFailure
                                    .STOCK_STORE_NOT_EMPTY,
                            materializedFailureDetail(verification), 0);
                }
            }
            migration.initializeSnapshot(source);
            durabilityBarrier.flush();
        } else if (!migration.snapshot().equals(source)) {
            return fail(migration, durabilityBarrier,
                    CatalogStockMigrationFailure.SOURCE_CHANGED,
                    "Catalog stock source changed after capture", 0);
        }

        if (migration.stage()
                == CatalogStockMigrationStage.SNAPSHOT_PENDING) {
            durabilityBarrier.flush();
            migration.markSnapshotDurable();
            durabilityBarrier.flush();
        }

        int processed = 0;
        if (migration.stage()
                == CatalogStockMigrationStage.IMPORTING) {
            while (processed < batchSize
                    && migration.nextEntry().isPresent()) {
                CatalogStockSeedEntry entry =
                        migration.nextEntry().orElseThrow();
                CatalogStockImportDisposition disposition;
                try {
                    disposition = gateway.importEntry(
                            source, entry, now);
                } catch (RuntimeException exception) {
                    return fail(migration, durabilityBarrier,
                            CatalogStockMigrationFailure.IMPORT_CONFLICT,
                            "Catalog stock entry import conflicts", processed);
                }
                if (disposition
                        == CatalogStockImportDisposition.RETRY_LATER) {
                    if (processed > 0) {
                        durabilityBarrier.flush();
                    }
                    return migration.result(processed,
                            "Escrow stock is not ready");
                }
                migration.advance(entry,
                        CatalogStockMigrationIds.entryCompletion(
                                source, entry));
                processed++;
            }
            if (processed > 0) {
                durabilityBarrier.flush();
            }
            if (migration.nextEntry().isPresent()) {
                return migration.result(processed, "");
            }
            migration.markImportsComplete();
            durabilityBarrier.flush();
        }

        if (migration.stage()
                == CatalogStockMigrationStage.IMPORTS_COMPLETE) {
            CatalogStockVerification verification;
            try {
                CatalogStockImportDisposition disposition =
                        gateway.reconcile(source, now);
                if (disposition
                        == CatalogStockImportDisposition.RETRY_LATER) {
                    return migration.result(processed,
                            "Escrow stock is not ready");
                }
                verification = gateway.verify(source);
            } catch (RuntimeException exception) {
                return fail(migration, durabilityBarrier,
                        CatalogStockMigrationFailure.VERIFICATION_FAILED,
                        "Catalog stock verification failed", processed);
            }
            if (!verification.valid()) {
                return fail(migration, durabilityBarrier,
                        CatalogStockMigrationFailure.VERIFICATION_FAILED,
                        verification.detail(), processed);
            }
            migration.markVerified(
                    verification.completionSequence());
            durabilityBarrier.flush();
        }

        if (migration.stage()
                == CatalogStockMigrationStage.VERIFIED) {
            if (!durabilityBarrier.checkpointVerified(
                    source.fingerprint(),
                    migration.completionSequence())) {
                return migration.result(processed,
                        "Awaiting a verified escrow checkpoint");
            }
            migration.markComplete();
            durabilityBarrier.flush();
        }
        return migration.result(processed, "");
    }

    private static String materializedFailureDetail(
            CatalogStockVerification verification
    ) {
        Objects.requireNonNull(verification, "verification");
        return "Escrow stock already contains incompatible materialized state. "
                + verification.detail();
    }

    private static CatalogStockMigrationResult fail(
            CatalogStockMigrationSavedData migration,
            CatalogStockMigrationDurabilityBarrier durabilityBarrier,
            CatalogStockMigrationFailure failure,
            String detail,
            int processed
    ) {
        migration.fail(failure, detail);
        durabilityBarrier.flush();
        return migration.result(processed, detail);
    }
}
