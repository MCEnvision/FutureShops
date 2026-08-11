package com.enviouse.futureshops.server.escrow.stock.migration;

public enum CatalogStockMigrationStage {
    UNINITIALIZED,
    SNAPSHOT_PENDING,
    IMPORTING,
    IMPORTS_COMPLETE,
    VERIFIED,
    COMPLETE,
    FAILED
}
