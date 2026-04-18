package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.data.CatalogItem;

/**
 * Server-side record representing one purchasable item loaded from the catalog config.
 * All prices are in minor currency units (e.g., 1500 = $15.00 at 2 decimal places).
 * A {@code stock} of {@code -1} means unlimited.
 */
public record ItemDef(
        String itemId,
        String displayName,
        long buyPriceMinorUnits,
        long sellPriceMinorUnits,
        int stock,
        boolean barterEnabled,
        String categoryId,
        int stockRefreshSeconds) {

    /**
     * Backwards-compatible constructor without stockRefreshSeconds.
     */
    public ItemDef(String itemId, String displayName, long buyPriceMinorUnits, long sellPriceMinorUnits,
                   int stock, boolean barterEnabled, String categoryId) {
        this(itemId, displayName, buyPriceMinorUnits, sellPriceMinorUnits, stock, barterEnabled, categoryId, 0);
    }

    /** Returns {@code true} when this item has unlimited stock. */
    public boolean isUnlimited() {
        return stock < 0;
    }

    /** Returns {@code true} when this item has automatic stock refresh enabled. */
    public boolean hasStockRefresh() {
        return stockRefreshSeconds > 0 && !isUnlimited();
    }

    /**
     * Converts to a network-sendable {@link CatalogItem}.
     *
     * @param hasPromo   whether an active promo applies to this item
     * @param promoPrice discounted price (minor units); ignored when {@code hasPromo} is false
     */
    public CatalogItem toCatalogItem(boolean hasPromo, long promoPrice) {
        return toCatalogItem(stock, hasPromo, promoPrice, barterEnabled);
    }

    public CatalogItem toCatalogItem(int currentStock, boolean hasPromo, long promoPrice) {
        return toCatalogItem(currentStock, hasPromo, promoPrice, barterEnabled);
    }

    public CatalogItem toCatalogItem(int currentStock, boolean hasPromo, long promoPrice, boolean hasBarterRecipes) {
        String name = (displayName != null && !displayName.isBlank()) ? displayName : itemId;
        // Use explicit JSON-defined category first, then fall back to admin-set categories.
        // TagDepartmentClassifier is deprecated — admin categories take priority.
        String resolvedCategory = (categoryId != null && !categoryId.isBlank()) ? categoryId : "all";
        return new CatalogItem(
                itemId,
                name,
                buyPriceMinorUnits,
                sellPriceMinorUnits,
                currentStock,
                currentStock < 0,
                barterEnabled,
                resolvedCategory,
                hasPromo,
                promoPrice,
                hasBarterRecipes,
                "");
    }
}

