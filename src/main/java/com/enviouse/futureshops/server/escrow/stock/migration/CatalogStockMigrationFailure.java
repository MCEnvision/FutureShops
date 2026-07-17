package com.enviouse.futureshops.server.escrow.stock.migration;

public enum CatalogStockMigrationFailure {
    NONE,
    SOURCE_INVALID,
    SOURCE_CHANGED,
    STOCK_STORE_NOT_EMPTY,
    IMPORT_CONFLICT,
    VERIFICATION_FAILED
}
