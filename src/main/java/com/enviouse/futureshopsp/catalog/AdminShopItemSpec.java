package com.enviouse.futureshopsp.catalog;

/**
 * Lightweight DTO for one admin shop entry, handed to {@link AdminShopConfigWriter}.
 *
 * <p>{@code listingId} is the stable per-listing resolution key written as {@code "id"} in
 * {@code admin.json}. The {@code /shopadmin items} command always supplies an explicit unique id
 * (so several entries can share one {@code itemId}); the legacy {@code /shopadmin adminshop} wizard
 * leaves it equal to {@code itemId} via the back-compat constructors. Resolve via {@link #resolutionKey()}.
 *
 * <p>{@code stock} of {@code -1} means unlimited. Prices are minor units.
 * {@code stockRefreshSeconds} of {@code 0} means no automatic refresh.
 * {@code expiresAtEpoch} of {@code 0} means the listing never expires.
 *
 * <p>{@code nbtJson} is the SNBT representation of the held item's tag, or an empty string when the
 * item was bare. Persisting this lets enchanted books (Mending), Tacz guns, custom-NBT items (named
 * lore, dyed armor, potions, charged Tridents, etc.) round-trip through {@code /shop} buys with their
 * tag intact — without it, the admin shop mints a bare item that displays as missing-texture for
 * variant-keyed items and as the un-enchanted base for books.
 */
public record AdminShopItemSpec(
        String listingId,
        String itemId,
        String displayName,
        long buyPriceMinor,
        long sellPriceMinor,
        int stock,
        int stockRefreshSeconds,
        String categoryId,
        String nbtJson,
        long expiresAtEpoch) {

    /** Backwards-compatible constructor: no NBT, no expiry. {@code listingId} defaults to {@code itemId}. */
    public AdminShopItemSpec(String itemId, String displayName, long buyPriceMinor,
                             long sellPriceMinor, int stock, int stockRefreshSeconds, String categoryId) {
        this(itemId, itemId, displayName, buyPriceMinor, sellPriceMinor, stock, stockRefreshSeconds,
                categoryId, "", 0L);
    }

    /**
     * Backwards-compatible constructor carrying NBT but no explicit listingId/expiry — used by the
     * legacy {@code /shopadmin adminshop} wizard, where {@code listingId} defaults to {@code itemId}.
     */
    public AdminShopItemSpec(String itemId, String displayName, long buyPriceMinor,
                             long sellPriceMinor, int stock, int stockRefreshSeconds, String categoryId, String nbtJson) {
        this(itemId, itemId, displayName, buyPriceMinor, sellPriceMinor, stock, stockRefreshSeconds,
                categoryId, nbtJson, 0L);
    }

    /** Resolution key; falls back to {@code itemId} when no explicit listingId was supplied. */
    public String resolutionKey() {
        return (listingId != null && !listingId.isBlank()) ? listingId : itemId;
    }
}
