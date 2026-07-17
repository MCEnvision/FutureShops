package com.enviouse.futureshops.server.escrow.stock;

public record StockKey(String shopId, String listingId) implements Comparable<StockKey> {
    public StockKey {
        shopId = StockLimits.requireIdentifier(shopId, "stock shop identifier");
        listingId = StockLimits.requireIdentifier(listingId, "stock listing identifier");
    }

    public String canonicalValue() {
        return shopId.length() + ":" + shopId + listingId;
    }

    @Override
    public int compareTo(StockKey other) {
        int shopOrder = shopId.compareTo(other.shopId);
        return shopOrder != 0 ? shopOrder : listingId.compareTo(other.listingId);
    }
}
