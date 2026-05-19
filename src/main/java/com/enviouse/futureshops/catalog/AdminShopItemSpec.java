package com.enviouse.futureshops.catalog;

/**
 * Lightweight DTO for one admin shop entry, used by the {@code /shopadmin adminshop add} wizard
 * to hand collected values to {@link AdminShopConfigWriter}.
 *
 * <p>{@code stock} of {@code -1} means unlimited. Prices are minor units.
 * {@code stockRefreshSeconds} of {@code 0} means no automatic refresh.
 */
public record AdminShopItemSpec(
        String itemId,
        String displayName,
        long buyPriceMinor,
        long sellPriceMinor,
        int stock,
        int stockRefreshSeconds,
        String categoryId) {
}
