package com.enviouse.futureshops.server.escrow.stock.migration;

public enum CatalogStockCutoverReadiness {
    WAITING_FOR_TRANSACTIONAL_CALLERS,
    MIGRATION_REQUIRED,
    CHECKPOINT_REQUIRED,
    READY_TO_ACTIVATE,
    ACTIVE,
    FAILED
}
