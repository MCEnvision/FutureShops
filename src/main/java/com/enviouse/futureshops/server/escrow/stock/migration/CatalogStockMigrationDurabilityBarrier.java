package com.enviouse.futureshops.server.escrow.stock.migration;

public interface CatalogStockMigrationDurabilityBarrier {
    void flush();

    boolean checkpointVerified(String checksum, long completionSequence);
}
