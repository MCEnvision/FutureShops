package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.data.CatalogPromo;

/**
 * Server-side record representing an active promotional discount loaded from the catalog config.
 * Mirrors spec §23 / §25.7 promo types: {@code PERCENTAGE}, {@code FLAT}, {@code BUY_X_GET_Y},
 * {@code FLASH}, {@code BARTER_DISCOUNT}.
 * <p>
 * {@code endTimeEpoch = 0} means the promo is permanent.
 * An empty {@code targetItemId} means the promo applies shop-wide.
 */
public record PromoDef(
        String promoId,
        String promoType,
        String targetItemId,
        double discountValue,
        long endTimeEpoch) {

    /** Returns {@code true} if this promo has an end time that has already passed. */
    public boolean isExpired() {
        return endTimeEpoch > 0L && endTimeEpoch < System.currentTimeMillis() / 1000L;
    }

    /** Converts to a network-sendable {@link CatalogPromo}. */
    public CatalogPromo toCatalogPromo() {
        return new CatalogPromo(
                promoId,
                promoType,
                targetItemId != null ? targetItemId : "",
                discountValue,
                endTimeEpoch);
    }
}

