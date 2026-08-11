package com.enviouse.futureshops.catalog;

public interface CatalogStockAuthority {
    String seedChecksum();

    int currentStock(String shopId, String listingId);
}
