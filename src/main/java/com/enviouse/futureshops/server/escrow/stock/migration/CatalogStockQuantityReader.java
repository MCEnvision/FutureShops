package com.enviouse.futureshops.server.escrow.stock.migration;

@FunctionalInterface
public interface CatalogStockQuantityReader {
    int currentStock(String shopId, String listingId);
}
