package com.enviouse.futureshops.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for the per-listing-id identity model in {@link AdminShopConfigWriter} and
 * {@link ShopDefinitionLoader} — the backbone of multi-variant NBT admin listings. Exercises only the
 * pure helpers (no FMLPaths / no catalog reload), so it runs without a Forge runtime.
 */
public class AdminListingIdentityTest {

    // ── AdminShopConfigWriter.buildItemEntry ──────────────────────────────────

    @Test
    void buildItemEntryOmitsIdWhenSameAsItemId() {
        // Legacy single-variant entry (listingId defaults to itemId) must stay clean in the file.
        AdminShopItemSpec legacy = new AdminShopItemSpec("minecraft:diamond", "Diamond", 500L, 250L, -1, 0, "materials");
        JsonObject o = AdminShopConfigWriter.buildItemEntry(legacy);
        assertFalse(o.has("id"), "id omitted when listingId == itemId");
        assertEquals("minecraft:diamond", o.get("itemId").getAsString());
        assertFalse(o.has("expiresAtEpoch"), "expiresAtEpoch omitted when 0");
    }

    @Test
    void buildItemEntryWritesIdNbtAndExpiry() {
        AdminShopItemSpec variant = new AdminShopItemSpec(
                "enchanted_book_2", "minecraft:enchanted_book", "Sharpness V", 3000L, 0L, -1, 0,
                "books", "{Enchantments:[{id:\"minecraft:sharpness\",lvl:5s}]}", 1_700_000_000L);
        JsonObject o = AdminShopConfigWriter.buildItemEntry(variant);
        assertEquals("enchanted_book_2", o.get("id").getAsString());
        assertEquals("minecraft:enchanted_book", o.get("itemId").getAsString());
        assertEquals(1_700_000_000L, o.get("expiresAtEpoch").getAsLong());
        assertTrue(o.get("nbt").getAsString().contains("sharpness"));
    }

    // ── AdminShopConfigWriter.indexOfByListingId ──────────────────────────────

    @Test
    void indexOfByListingIdMatchesExplicitIdAndLegacyItemId() {
        JsonArray arr = new JsonArray();
        JsonObject legacy = new JsonObject();
        legacy.addProperty("itemId", "minecraft:diamond");
        arr.add(legacy);
        JsonObject variant = new JsonObject();
        variant.addProperty("id", "enchanted_book_1");
        variant.addProperty("itemId", "minecraft:enchanted_book");
        arr.add(variant);

        assertEquals(0, AdminShopConfigWriter.indexOfByListingId(arr, "minecraft:diamond"),
                "legacy entry resolves by its itemId-as-listingId");
        assertEquals(1, AdminShopConfigWriter.indexOfByListingId(arr, "ENCHANTED_BOOK_1"),
                "explicit id resolves case-insensitively");
        assertEquals(-1, AdminShopConfigWriter.indexOfByListingId(arr, "missing"));
    }

    // ── AdminShopConfigWriter.nextId ──────────────────────────────────────────

    @Test
    void nextIdProducesUniqueSequentialSuffixes() {
        JsonArray arr = new JsonArray();
        String first = AdminShopConfigWriter.nextId(arr, "minecraft:enchanted_book");
        assertEquals("enchanted_book_1", first);

        JsonObject e1 = new JsonObject();
        e1.addProperty("id", first);
        e1.addProperty("itemId", "minecraft:enchanted_book");
        arr.add(e1);

        assertEquals("enchanted_book_2", AdminShopConfigWriter.nextId(arr, "minecraft:enchanted_book"),
                "second listing of the same base item gets the next free suffix");
    }

    // ── ShopDefinitionLoader.parseJson listingId defaulting ───────────────────

    @Test
    void loaderDefaultsListingIdToItemIdWhenIdAbsentOrBlank() {
        String json = "{"
                + "\"shopId\":\"default\",\"items\":["
                + "  {\"itemId\":\"minecraft:diamond\",\"buyPrice\":500},"
                + "  {\"id\":\"\",\"itemId\":\"minecraft:emerald\",\"buyPrice\":300},"
                + "  {\"id\":\"eb_2\",\"itemId\":\"minecraft:enchanted_book\",\"buyPrice\":1000,\"expiresAtEpoch\":1700000000}"
                + "]}";
        ShopDefinition def = ShopDefinitionLoader.parseJson(json, "test.json");
        assertEquals(3, def.items().size());
        // Absent id -> listingId == itemId (legacy, zero migration).
        assertEquals("minecraft:diamond", def.items().get(0).listingId());
        // Blank id -> still defaults to itemId (getString doesn't fall back on blank).
        assertEquals("minecraft:emerald", def.items().get(1).listingId());
        // Explicit id honored; expiresAtEpoch parsed.
        assertEquals("eb_2", def.items().get(2).listingId());
        assertEquals("minecraft:enchanted_book", def.items().get(2).itemId());
        assertEquals(1_700_000_000L, def.items().get(2).expiresAtEpoch());
        assertEquals(0L, def.items().get(0).expiresAtEpoch(), "expiresAtEpoch defaults to 0 (never)");
    }

    @Test
    void itemDefIsExpiredHonorsWindow() {
        ItemDef never = new ItemDef("id1", "minecraft:diamond", "Diamond", 500L, 0L, -1, false, "all", 0, "", 0L);
        assertFalse(never.isExpired(9_999_999_999L), "expiresAtEpoch 0 never expires");
        ItemDef windowed = new ItemDef("id2", "minecraft:diamond", "Diamond", 500L, 0L, -1, false, "all", 0, "", 1_000L);
        assertFalse(windowed.isExpired(999L));
        assertTrue(windowed.isExpired(1_000L));
        assertTrue(windowed.isExpired(2_000L));
    }
}
