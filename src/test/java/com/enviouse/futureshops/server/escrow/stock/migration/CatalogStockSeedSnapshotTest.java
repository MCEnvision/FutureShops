package com.enviouse.futureshops.server.escrow.stock.migration;

import com.enviouse.futureshops.catalog.ItemDef;
import com.enviouse.futureshops.catalog.ShopDefinition;
import com.enviouse.futureshops.server.escrow.stock.StockKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogStockSeedSnapshotTest {
    @Test
    void capturesStableListingIdentityAndBothStockPolicies() {
        ShopDefinition shop = new ShopDefinition(
                "default", "Default", List.of(),
                List.of(
                        item("diamond_offer", "minecraft:diamond", 12),
                        item("free_stone", "minecraft:stone", -1)),
                List.of(), List.of());

        CatalogStockSeedSnapshot first = CatalogStockSeedCapture.capture(
                List.of(shop), (shopId, listingId) ->
                        listingId.equals("diamond_offer") ? 7 : -1);
        CatalogStockSeedSnapshot second = CatalogStockSeedCapture.capture(
                List.of(shop), (shopId, listingId) ->
                        listingId.equals("diamond_offer") ? 7 : -1);

        assertEquals(first, second);
        assertEquals(2, first.entries().size());
        CatalogStockSeedEntry finite = first.entries().stream()
                .filter(entry -> entry.key().listingId()
                        .equals("diamond_offer"))
                .findFirst().orElseThrow();
        assertEquals(new StockKey("default", "diamond_offer"),
                finite.key());
        assertEquals(12L, finite.configuredQuantity());
        assertEquals(7L, finite.availableQuantity());
        assertFalse(finite.unlimited());
        CatalogStockSeedEntry unlimited = first.entries().stream()
                .filter(CatalogStockSeedEntry::unlimited)
                .findFirst().orElseThrow();
        assertEquals(0L, unlimited.configuredQuantity());
        assertEquals(0L, unlimited.availableQuantity());
        assertEquals(1, first.unlimitedListings());
        assertEquals(7L, first.finiteAvailableQuantity());
        assertTrue(CatalogStockSeedCapture.configurationMatches(
                List.of(shop), first));
    }

    @Test
    void preservesFiniteAvailabilityAboveOldConfiguredTarget() {
        CatalogStockSeedEntry entry = new CatalogStockSeedEntry(
                new StockKey("default", "minecraft:iron_ingot"),
                false, 5L, 9L, fingerprint('a'));

        assertEquals(9L, entry.durableCapacity());
        assertEquals(9L,
                entry.definition().policy().configuredQuantity());
        assertEquals(9L, CatalogStockSeedSnapshot.capture(
                List.of(entry)).finiteAvailableQuantity());
    }

    @Test
    void duplicateStableIdentifierFailsClosed() {
        CatalogStockSeedEntry entry = new CatalogStockSeedEntry(
                new StockKey("default", "minecraft:diamond"),
                false, 2L, 2L, fingerprint('a'));

        assertThrows(IllegalArgumentException.class,
                () -> CatalogStockSeedSnapshot.capture(
                        List.of(entry, entry)));
    }

    @Test
    void finiteSourceCannotMasqueradeAsUnlimited() {
        ShopDefinition shop = new ShopDefinition(
                "default", "Default", List.of(),
                List.of(item("diamond", "minecraft:diamond", 4)),
                List.of(), List.of());

        assertThrows(IllegalStateException.class,
                () -> CatalogStockSeedCapture.capture(
                        List.of(shop), (shopId, listingId) -> -1));
    }

    @Test
    void identityFingerprintBindsItemAndNbtButNotStockPolicy() {
        StockKey key = new StockKey("default", "offer");
        ItemDef original = item("offer", "minecraft:diamond", 4, "");
        ItemDef restocked = item("offer", "minecraft:diamond", 20, "");
        ItemDef replaced = item("offer", "minecraft:emerald", 4, "");
        ItemDef retagged = item("offer", "minecraft:diamond", 4,
                "{CustomModelData:1}");

        assertEquals(CatalogStockSeedCapture.configFingerprint(key, original),
                CatalogStockSeedCapture.configFingerprint(key, restocked));
        assertNotEquals(CatalogStockSeedCapture.configFingerprint(key, original),
                CatalogStockSeedCapture.configFingerprint(key, replaced));
        assertNotEquals(CatalogStockSeedCapture.configFingerprint(key, original),
                CatalogStockSeedCapture.configFingerprint(key, retagged));
    }

    private static ItemDef item(
            String listingId,
            String itemId,
            int stock
    ) {
        return item(listingId, itemId, stock, "");
    }

    private static ItemDef item(
            String listingId,
            String itemId,
            int stock,
            String nbt
    ) {
        return new ItemDef(listingId, itemId, itemId,
                100L, 50L, stock, false, "all",
                0, nbt, 0L);
    }

    private static String fingerprint(char value) {
        return String.valueOf(value).repeat(64);
    }
}
