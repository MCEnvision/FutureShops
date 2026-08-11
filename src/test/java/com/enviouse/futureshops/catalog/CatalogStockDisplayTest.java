package com.enviouse.futureshops.catalog;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CatalogStockDisplayTest {
    private static final ItemDef LIMITED = new ItemDef(
            "minecraft:iron_ingot", "Iron Ingot",
            100L, 50L, 64, false, "materials");
    private static final ItemDef UNLIMITED = new ItemDef(
            "minecraft:diamond", "Diamond",
            500L, 250L, -1, false, "materials");

    @Test
    void frozenCutoverKeepsBrowsingAvailableAndShowsZeroStock() {
        AtomicBoolean durableLookup = new AtomicBoolean();

        int stock = ShopCatalog.resolveDisplayStock(
                LIMITED, CatalogStockAuthorityMode.CUTOVER_FROZEN,
                64, () -> {
                    durableLookup.set(true);
                    throw new IllegalStateException("must not run");
                });

        assertEquals(0, stock);
        assertFalse(durableLookup.get());
    }

    @Test
    void brokenDurableLookupFailsClosedForDisplay() {
        assertEquals(0, ShopCatalog.resolveDisplayStock(
                LIMITED, CatalogStockAuthorityMode.DURABLE,
                64, () -> {
                    throw new IllegalStateException("missing stock");
                }));
    }

    @Test
    void unlimitedAndLegacyStockKeepTheirNormalDisplayValues() {
        assertEquals(-1, ShopCatalog.resolveDisplayStock(
                UNLIMITED, CatalogStockAuthorityMode.CUTOVER_FROZEN,
                -1, () -> 0));
        assertEquals(12, ShopCatalog.resolveDisplayStock(
                LIMITED, CatalogStockAuthorityMode.LEGACY,
                12, () -> 0));
    }
}
