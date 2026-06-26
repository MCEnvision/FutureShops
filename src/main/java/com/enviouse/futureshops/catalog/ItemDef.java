package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.data.CatalogItem;

/**
 * Server-side record representing one purchasable item loaded from the catalog config.
 * All prices are in minor currency units (e.g., 1500 = $15.00 at 2 decimal places).
 * A {@code stock} of {@code -1} means unlimited.
 *
 * <p>{@code listingId} is the stable catalog <em>resolution key</em> — the identity a buy/sell/cart
 * request echoes back to pick exactly this listing. It is independent of {@code itemId} so that
 * several listings can share one registry id (e.g. three {@code minecraft:enchanted_book} with
 * different enchant sets) yet remain distinct. Legacy {@code admin.json} entries with no explicit
 * {@code "id"} default {@code listingId == itemId}, which keeps their stock/pricing keys byte-for-byte
 * identical to the pre-listingId behaviour (zero migration). Always resolve via {@link #resolutionKey()}.
 *
 * <p>{@code itemId} is the real registry id — "which Minecraft item to mint and render". The client
 * renders icons from it (a valid {@link net.minecraft.resources.ResourceLocation}); it must never
 * try to parse {@code listingId} as a resource location.
 *
 * <p>{@code nbtJson} carries the SNBT for items whose identity lives in NBT — enchanted
 * books, Tacz guns, named/lored items, charged tridents, dyed armor, etc. Empty string
 * for bare items (the legacy case). Persisted into and parsed from {@code admin.json};
 * forwarded to {@link CatalogItem} for client display and to the buy-service for minting
 * the delivered stack with the correct tag.
 *
 * <p>{@code expiresAtEpoch} is the listing availability window: when {@code > 0} the listing is
 * hidden from the catalog and rejected at buy/sell time once {@code now >= expiresAtEpoch}.
 * {@code 0} means "never expires" (the legacy default).
 */
public record ItemDef(
        String listingId,
        String itemId,
        String displayName,
        long buyPriceMinorUnits,
        long sellPriceMinorUnits,
        int stock,
        boolean barterEnabled,
        String categoryId,
        int stockRefreshSeconds,
        String nbtJson,
        long expiresAtEpoch) {

    /** Backwards-compatible constructor: no refresh, no NBT, no expiry. {@code listingId} defaults to {@code itemId}. */
    public ItemDef(String itemId, String displayName, long buyPriceMinorUnits, long sellPriceMinorUnits,
                   int stock, boolean barterEnabled, String categoryId) {
        this(itemId, itemId, displayName, buyPriceMinorUnits, sellPriceMinorUnits, stock, barterEnabled,
                categoryId, 0, "", 0L);
    }

    /** Backwards-compatible constructor: refresh seconds, no NBT, no expiry. {@code listingId} defaults to {@code itemId}. */
    public ItemDef(String itemId, String displayName, long buyPriceMinorUnits, long sellPriceMinorUnits,
                   int stock, boolean barterEnabled, String categoryId, int stockRefreshSeconds) {
        this(itemId, itemId, displayName, buyPriceMinorUnits, sellPriceMinorUnits, stock, barterEnabled,
                categoryId, stockRefreshSeconds, "", 0L);
    }

    /** Backwards-compatible constructor: refresh seconds + NBT, no expiry. {@code listingId} defaults to {@code itemId}. */
    public ItemDef(String itemId, String displayName, long buyPriceMinorUnits, long sellPriceMinorUnits,
                   int stock, boolean barterEnabled, String categoryId, int stockRefreshSeconds, String nbtJson) {
        this(itemId, itemId, displayName, buyPriceMinorUnits, sellPriceMinorUnits, stock, barterEnabled,
                categoryId, stockRefreshSeconds, nbtJson, 0L);
    }

    /**
     * The stable resolution key for this listing. Never blank: falls back to {@code itemId} for legacy
     * entries that carry no explicit id, so {@code resolutionKey() == itemId} in the single-variant case.
     */
    public String resolutionKey() {
        return (listingId != null && !listingId.isBlank()) ? listingId : itemId;
    }

    /**
     * Availability-window check. {@code expiresAtEpoch == 0} means the listing never expires.
     *
     * @param nowEpochSeconds current unix time in seconds
     */
    public boolean isExpired(long nowEpochSeconds) {
        return expiresAtEpoch > 0L && nowEpochSeconds >= expiresAtEpoch;
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
                resolutionKey(),
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
                nbtJson == null ? "" : nbtJson);
    }
}
