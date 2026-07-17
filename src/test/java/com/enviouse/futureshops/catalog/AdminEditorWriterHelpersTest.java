package com.enviouse.futureshops.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the pure JSON helpers behind the in-GUI admin editor's writer API
 * ({@code updateListingFields} / {@code renameCategory} / {@code setCategorySortOrders} /
 * {@code addItems}). The load-bearing guarantees: partial listing edits NEVER touch keys they
 * don't own (nbt, expiresAtEpoch, stockRefreshSeconds — dropping the nbt of a TacZ gun mints a
 * dead item), category rename keeps the stable id + sortOrder, and batch add generates unique
 * ids against the growing array.
 */
class AdminEditorWriterHelpersTest {

    private static JsonObject listing(String id, String itemId) {
        JsonObject o = new JsonObject();
        if (id != null) o.addProperty("id", id);
        o.addProperty("itemId", itemId);
        o.addProperty("displayName", "Old Name");
        o.addProperty("buyPrice", 1000L);
        o.addProperty("sellPrice", 500L);
        o.addProperty("stock", 10);
        o.addProperty("categoryId", "tools");
        return o;
    }

    private static JsonObject category(String id, String displayName, int sortOrder) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("displayName", displayName);
        o.addProperty("sortOrder", sortOrder);
        return o;
    }

    private static JsonObject rootWithItems(JsonObject... items) {
        JsonArray arr = new JsonArray();
        for (JsonObject o : items) arr.add(o);
        JsonObject root = new JsonObject();
        root.add("items", arr);
        return root;
    }

    private static JsonObject rootWithCategories(JsonObject... cats) {
        JsonArray arr = new JsonArray();
        for (JsonObject o : cats) arr.add(o);
        JsonObject root = new JsonObject();
        root.add("categories", arr);
        return root;
    }

    // ─── updateListingFieldsInRoot ───────────────────────────────────────────

    @Test
    void updateListingFieldsEditsInPlaceAndNeverDropsUnownedKeys() {
        JsonObject entry = listing("gun_1", "tacz:modern_kinetic_gun");
        entry.addProperty("nbt", "{GunId:\"tacz:ak47\"}");
        entry.addProperty("expiresAtEpoch", 1_780_000_000L);
        entry.addProperty("stockRefreshSeconds", 300);
        JsonObject root = rootWithItems(entry);

        assertTrue(AdminShopConfigWriter.updateListingFieldsInRoot(
                root, "gun_1", "AK-47", "weapons", 25_000L, 12_500L, 5));

        JsonObject updated = root.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("AK-47", updated.get("displayName").getAsString());
        assertEquals("weapons", updated.get("categoryId").getAsString());
        assertEquals(25_000L, updated.get("buyPrice").getAsLong());
        assertEquals(12_500L, updated.get("sellPrice").getAsLong());
        assertEquals(5, updated.get("stock").getAsInt());
        // Keys the GUI never surfaces must survive the partial edit untouched.
        assertEquals("{GunId:\"tacz:ak47\"}", updated.get("nbt").getAsString());
        assertEquals(1_780_000_000L, updated.get("expiresAtEpoch").getAsLong());
        assertEquals(300, updated.get("stockRefreshSeconds").getAsInt());
        // Identity keys stay stable.
        assertEquals("gun_1", updated.get("id").getAsString());
        assertEquals("tacz:modern_kinetic_gun", updated.get("itemId").getAsString());
    }

    @Test
    void updateListingFieldsBlankDisplayNameAndCategoryRemoveTheKeys() {
        JsonObject root = rootWithItems(listing("apple_1", "minecraft:apple"));

        assertTrue(AdminShopConfigWriter.updateListingFieldsInRoot(
                root, "apple_1", "", "", 200L, 100L, -1));

        JsonObject updated = root.getAsJsonArray("items").get(0).getAsJsonObject();
        assertFalse(updated.has("displayName"), "blank displayName clears the override");
        assertFalse(updated.has("categoryId"), "blank categoryId means the All fallback");
        assertEquals(-1, updated.get("stock").getAsInt());
    }

    @Test
    void updateListingFieldsResolvesLegacyEntryByItemId() {
        // Legacy entry: no "id" field, listingId == itemId.
        JsonObject root = rootWithItems(listing(null, "minecraft:diamond"));

        assertTrue(AdminShopConfigWriter.updateListingFieldsInRoot(
                root, "minecraft:diamond", "Shiny", "materials", 900L, 450L, 64));

        JsonObject updated = root.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("Shiny", updated.get("displayName").getAsString());
        assertFalse(updated.has("id"), "legacy entries stay id-less (id would change their stock keys)");
    }

    @Test
    void updateListingFieldsReturnsFalseWhenListingMissing() {
        JsonObject root = rootWithItems(listing("apple_1", "minecraft:apple"));
        assertFalse(AdminShopConfigWriter.updateListingFieldsInRoot(
                root, "nope_1", "X", "", 1L, 1L, 1));
        assertEquals("Old Name",
                root.getAsJsonArray("items").get(0).getAsJsonObject().get("displayName").getAsString(),
                "a miss must leave the array untouched");
    }

    // ─── renameCategoryInRoot ────────────────────────────────────────────────

    @Test
    void renameCategoryRewritesDisplayNameKeepingIdAndSortOrder() {
        JsonObject root = rootWithCategories(
                category("weapons", "Weapons", 3),
                category("food", "Food", 1));

        assertTrue(AdminShopConfigWriter.renameCategoryInRoot(root, "weapons", "Guns"));

        JsonObject renamed = root.getAsJsonArray("categories").get(0).getAsJsonObject();
        assertEquals("weapons", renamed.get("id").getAsString(), "id is the stable key items point at");
        assertEquals("Guns", renamed.get("displayName").getAsString());
        assertEquals(3, renamed.get("sortOrder").getAsInt(), "rename must not reorder tabs");
    }

    @Test
    void renameCategoryReturnsFalseWhenIdMissing() {
        JsonObject root = rootWithCategories(category("food", "Food", 1));
        assertFalse(AdminShopConfigWriter.renameCategoryInRoot(root, "weapons", "Guns"));
        assertEquals("Food",
                root.getAsJsonArray("categories").get(0).getAsJsonObject().get("displayName").getAsString());
    }

    // ─── setCategorySortOrdersInRoot / categoryIdsSortedFromRoot ─────────────

    @Test
    void setCategorySortOrdersRewritesListedIdsToListIndex() {
        JsonObject root = rootWithCategories(
                category("tools", "Tools", 1),
                category("materials", "Materials", 2),
                category("food", "Food", 7));

        int updated = AdminShopConfigWriter.setCategorySortOrdersInRoot(
                root, List.of("materials", "tools"));

        assertEquals(2, updated);
        assertEquals(List.of("materials", "tools", "food"),
                AdminShopConfigWriter.categoryIdsSortedFromRoot(root),
                "swap applies; unlisted categories keep their own sortOrder");
        assertEquals(7, root.getAsJsonArray("categories").get(2).getAsJsonObject()
                .get("sortOrder").getAsInt(), "unlisted category keeps its sortOrder");
    }

    @Test
    void setCategorySortOrdersReturnsZeroWhenNothingMatches() {
        JsonObject root = rootWithCategories(category("tools", "Tools", 1));
        assertEquals(0, AdminShopConfigWriter.setCategorySortOrdersInRoot(root, List.of("nope")));
        assertEquals(1, root.getAsJsonArray("categories").get(0).getAsJsonObject()
                .get("sortOrder").getAsInt());
    }

    @Test
    void categoryIdsSortedIsStableForEqualSortOrders() {
        JsonObject root = rootWithCategories(
                category("b", "B", 1),
                category("a", "A", 1),
                category("c", "C", 0));
        assertEquals(List.of("c", "b", "a"), AdminShopConfigWriter.categoryIdsSortedFromRoot(root));
    }

    // ─── addItemsToRoot ──────────────────────────────────────────────────────

    @Test
    void addItemsGeneratesUniqueIdsAgainstTheGrowingArray() {
        // Existing legacy listing occupies the raw registry key; two batch specs of the same
        // item must get distinct generated ids in one pass.
        JsonObject root = rootWithItems(listing(null, "minecraft:diamond"));
        AdminShopItemSpec a = new AdminShopItemSpec(
                "", "minecraft:diamond", "Diamond", 500L, 250L, -1, 0, "materials", "", 0L);
        AdminShopItemSpec b = new AdminShopItemSpec(
                "", "minecraft:diamond", "Diamond", 500L, 250L, -1, 0, "materials", "", 0L);

        int added = AdminShopConfigWriter.addItemsToRoot(root, List.of(a, b));

        assertEquals(2, added);
        JsonArray items = root.getAsJsonArray("items");
        assertEquals(3, items.size());
        assertEquals("diamond_1", items.get(1).getAsJsonObject().get("id").getAsString());
        assertEquals("diamond_2", items.get(2).getAsJsonObject().get("id").getAsString());
    }

    @Test
    void addItemsSkipsCollidingExplicitIdsAndCountsOnlyAdded() {
        JsonObject root = rootWithItems(listing("apple_1", "minecraft:apple"));
        AdminShopItemSpec colliding = new AdminShopItemSpec(
                "apple_1", "minecraft:apple", "Apple", 100L, 50L, -1, 0, "food", "", 0L);
        AdminShopItemSpec fresh = new AdminShopItemSpec(
                "", "minecraft:bread", "Bread", 10L, 5L, -1, 0, "food", "", 0L);

        int added = AdminShopConfigWriter.addItemsToRoot(root, List.of(colliding, fresh));

        assertEquals(1, added, "explicit-id collisions are skipped, never overwritten");
        assertEquals(2, root.getAsJsonArray("items").size());
        assertEquals("Old Name", root.getAsJsonArray("items").get(0).getAsJsonObject()
                .get("displayName").getAsString(), "the colliding existing entry is untouched");
    }

    // ─── addItemsWithBarterToRoot ────────────────────────────────────────────
    // A barter add must produce BOTH: a listing carrying the selected category (so it lands under
    // that department, not "All"), AND a barter recipe whose target is the new listing's generated
    // id (so ShopCatalog flags hasBarterRecipes → it shows under the Barter tab, not just Buy).

    @Test
    void addItemsWithBarterWritesCategorizedListingAndRecipeTargetingIt() {
        JsonObject root = new JsonObject();
        AdminShopItemSpec spec = new AdminShopItemSpec(
                "", "minecraft:diamond", "Diamond", 0L, 0L, -1, 0, "materials", "", 0L);

        int added = AdminShopConfigWriter.addItemsWithBarterToRoot(
                root, List.of(spec), 2, "minecraft:emerald", 3);

        assertEquals(1, added);
        JsonObject item = root.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("diamond_1", item.get("id").getAsString(), "listing gets a generated id");
        assertEquals("minecraft:diamond", item.get("itemId").getAsString());
        assertEquals("materials", item.get("categoryId").getAsString(),
                "the selected category must be written — else it falls back to All");
        assertEquals(0L, item.get("buyPrice").getAsLong(), "barter listing carries no money buy price");

        JsonObject recipe = root.getAsJsonArray("barterRecipes").get(0).getAsJsonObject();
        assertEquals("diamond_1", recipe.get("targetItemId").getAsString(),
                "recipe MUST target the new listing's id, or hasBarterRecipes stays false");
        assertEquals(2, recipe.get("outputCount").getAsInt());
        JsonObject ing = recipe.getAsJsonArray("ingredients").get(0).getAsJsonObject();
        assertEquals("minecraft:emerald", ing.get("itemId").getAsString());
        assertEquals(3, ing.get("count").getAsInt());
    }

    @Test
    void barterAddResolvesEndToEnd_writerThroughRealLoaderAndResolver() {
        // The full server chain minus the network layer: writer JSON → the REAL admin.json loader
        // → the REAL barter-target resolver ShopCatalog uses at reload. If this passes, a barter add
        // is guaranteed to flag hasBarterRecipes=true (Barter tab) AND keep its category.
        JsonObject root = new JsonObject();
        AdminShopItemSpec spec = new AdminShopItemSpec(
                "", "minecraft:diamond", "Diamond", 0L, 0L, -1, 0, "materials", "", 0L);
        AdminShopConfigWriter.addItemsWithBarterToRoot(root, List.of(spec), 2, "minecraft:emerald", 3);

        ShopDefinition def = ShopDefinitionLoader.parseJson(root.toString(), "test-admin.json");

        ItemDef item = def.items().get(0);
        assertEquals("diamond_1", item.resolutionKey(), "listing keeps its generated id through the loader");
        assertEquals("materials", item.categoryId(), "listing keeps the chosen category through the loader");

        BarterRecipeDef recipe = def.barterRecipes().get(0);
        assertEquals("diamond_1", recipe.targetItemId());
        var resolved = ShopCatalog.resolveBarterTargetIn(def, recipe.targetItemId());
        assertTrue(resolved.isPresent(), "the recipe target MUST resolve to a listing");
        assertEquals(item.resolutionKey(), resolved.get().resolutionKey(),
                "resolved key == listing key ⇒ barterTargets.contains(resolutionKey) ⇒ hasBarterRecipes=true (Barter tab)");
    }

    @Test
    void addItemsWithBarterKeepsRecipeTargetAlignedAcrossDuplicateSpecs() {
        // Two barter listings of the same item in one pass: each recipe must bind to its OWN
        // generated id (diamond_1 / diamond_2), never cross-wire or reuse one target.
        JsonObject root = new JsonObject();
        AdminShopItemSpec a = new AdminShopItemSpec(
                "", "minecraft:diamond", "Diamond", 0L, 0L, -1, 0, "materials", "", 0L);
        AdminShopItemSpec b = new AdminShopItemSpec(
                "", "minecraft:diamond", "Diamond", 0L, 0L, -1, 0, "materials", "", 0L);

        int added = AdminShopConfigWriter.addItemsWithBarterToRoot(
                root, List.of(a, b), 1, "minecraft:emerald", 1);

        assertEquals(2, added);
        JsonArray items = root.getAsJsonArray("items");
        JsonArray recipes = root.getAsJsonArray("barterRecipes");
        assertEquals("diamond_1", items.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("diamond_2", items.get(1).getAsJsonObject().get("id").getAsString());
        assertEquals("diamond_1", recipes.get(0).getAsJsonObject().get("targetItemId").getAsString());
        assertEquals("diamond_2", recipes.get(1).getAsJsonObject().get("targetItemId").getAsString());
    }

    // ─── Multi-ingredient barter editor writer helpers ───────────────────────

    private static final AdminShopItemSpec DIAMOND_BARTER = new AdminShopItemSpec(
            "", "minecraft:diamond", "Diamond", 0L, 0L, -1, 0, "materials", "", 0L);

    @Test
    void addBarterTargetCreatesListingAndEmptyRecipe() {
        JsonObject root = new JsonObject();
        String id = AdminShopConfigWriter.addBarterTargetToRoot(root, DIAMOND_BARTER, 2);
        assertEquals("diamond_1", id);
        JsonObject item = root.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("diamond_1", item.get("id").getAsString());
        assertEquals("materials", item.get("categoryId").getAsString());
        JsonObject recipe = root.getAsJsonArray("barterRecipes").get(0).getAsJsonObject();
        assertEquals("diamond_1", recipe.get("targetItemId").getAsString());
        assertEquals(2, recipe.get("outputCount").getAsInt());
        assertEquals(0, recipe.getAsJsonArray("ingredients").size(), "target starts with no ingredients");
    }

    @Test
    void addBarterTargetsBatchPreservesSelectionOrderAndCreatesSafeEmptyRecipes() {
        JsonObject root = new JsonObject();
        AdminShopItemSpec emerald = new AdminShopItemSpec(
                "", "minecraft:emerald", "Emerald", 0L, 0L, 12, 0, "materials", "", 0L);

        List<String> ids = AdminShopConfigWriter.addBarterTargetsToRoot(
                root, List.of(DIAMOND_BARTER, emerald), 3);

        assertEquals(List.of("diamond_1", "emerald_1"), ids);
        JsonArray recipes = root.getAsJsonArray("barterRecipes");
        assertEquals(2, recipes.size());
        assertEquals(3, recipes.get(0).getAsJsonObject().get("outputCount").getAsInt());
        assertEquals(0, recipes.get(0).getAsJsonObject().getAsJsonArray("ingredients").size());
        assertEquals(0, recipes.get(1).getAsJsonObject().getAsJsonArray("ingredients").size());
    }

    @Test
    void addBarterIngredientAppendsThenCountMergesDuplicates() {
        JsonObject root = new JsonObject();
        AdminShopConfigWriter.addBarterTargetToRoot(root, DIAMOND_BARTER, 1);

        assertTrue(AdminShopConfigWriter.addBarterIngredientToRoot(root, "diamond_1", "minecraft:emerald", "", 3));
        assertTrue(AdminShopConfigWriter.addBarterIngredientToRoot(root, "diamond_1", "minecraft:gold_ingot", "", 1));
        // Adding emerald again count-merges instead of duplicating the row.
        assertTrue(AdminShopConfigWriter.addBarterIngredientToRoot(root, "diamond_1", "minecraft:emerald", "", 2));

        JsonArray ings = root.getAsJsonArray("barterRecipes").get(0).getAsJsonObject().getAsJsonArray("ingredients");
        assertEquals(2, ings.size(), "emerald merged, gold distinct");
        assertEquals("minecraft:emerald", ings.get(0).getAsJsonObject().get("itemId").getAsString());
        assertEquals(5, ings.get(0).getAsJsonObject().get("count").getAsInt(), "3 + 2 emerald merged");
        assertEquals("minecraft:gold_ingot", ings.get(1).getAsJsonObject().get("itemId").getAsString());
    }

    @Test
    void addBarterIngredientCreatesRecipeForAMoneyListing() {
        // A plain money listing (no recipe yet) becomes barterable when its first ingredient is added.
        JsonObject root = rootWithItems(listing("apple_1", "minecraft:apple"));
        assertTrue(AdminShopConfigWriter.addBarterIngredientToRoot(root, "apple_1", "minecraft:emerald", "", 1));
        JsonObject recipe = root.getAsJsonArray("barterRecipes").get(0).getAsJsonObject();
        assertEquals("apple_1", recipe.get("targetItemId").getAsString());
        assertEquals(1, recipe.getAsJsonArray("ingredients").size());
    }

    @Test
    void removeBarterIngredientDropsRecipeWhenLastIngredientGoes() {
        JsonObject root = new JsonObject();
        AdminShopConfigWriter.addBarterTargetToRoot(root, DIAMOND_BARTER, 1);
        AdminShopConfigWriter.addBarterIngredientToRoot(root, "diamond_1", "minecraft:emerald", "", 3);
        AdminShopConfigWriter.addBarterIngredientToRoot(root, "diamond_1", "minecraft:gold_ingot", "", 1);

        assertTrue(AdminShopConfigWriter.removeBarterIngredientFromRoot(root, "diamond_1", 0));
        JsonArray ings = root.getAsJsonArray("barterRecipes").get(0).getAsJsonObject().getAsJsonArray("ingredients");
        assertEquals(1, ings.size());
        assertEquals("minecraft:gold_ingot", ings.get(0).getAsJsonObject().get("itemId").getAsString());

        // Removing the last ingredient drops the whole recipe — never a 0-ingredient free trade.
        assertTrue(AdminShopConfigWriter.removeBarterIngredientFromRoot(root, "diamond_1", 0));
        assertEquals(0, root.getAsJsonArray("barterRecipes").size(), "empty recipe removed entirely");
    }

    @Test
    void setBarterOutputCountUpdatesAndClampsToOne() {
        JsonObject root = new JsonObject();
        AdminShopConfigWriter.addBarterTargetToRoot(root, DIAMOND_BARTER, 1);
        assertTrue(AdminShopConfigWriter.setBarterOutputCountInRoot(root, "diamond_1", 5));
        assertEquals(5, root.getAsJsonArray("barterRecipes").get(0).getAsJsonObject().get("outputCount").getAsInt());
        assertTrue(AdminShopConfigWriter.setBarterOutputCountInRoot(root, "diamond_1", 0));
        assertEquals(1, root.getAsJsonArray("barterRecipes").get(0).getAsJsonObject().get("outputCount").getAsInt(),
                "output count clamps to >= 1");
    }

    @Test
    void setBarterIngredientCountUpdatesSelectedRowAndClampsToOne() {
        JsonObject root = new JsonObject();
        AdminShopConfigWriter.addBarterTargetToRoot(root, DIAMOND_BARTER, 1);
        AdminShopConfigWriter.addBarterIngredientToRoot(root, "diamond_1", "minecraft:emerald", "", 3);
        AdminShopConfigWriter.addBarterIngredientToRoot(root, "diamond_1", "minecraft:gold_ingot", "", 2);

        assertTrue(AdminShopConfigWriter.setBarterIngredientCountInRoot(root, "diamond_1", 1, 7));
        JsonArray ingredients = root.getAsJsonArray("barterRecipes").get(0)
                .getAsJsonObject().getAsJsonArray("ingredients");
        assertEquals(3, ingredients.get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals(7, ingredients.get(1).getAsJsonObject().get("count").getAsInt());

        assertTrue(AdminShopConfigWriter.setBarterIngredientCountInRoot(root, "diamond_1", 1, 0));
        assertEquals(1, ingredients.get(1).getAsJsonObject().get("count").getAsInt());
    }

    @Test
    void barterEditorEndToEnd_targetPlusIngredientsResolveAndFlagBarter() {
        // Full chain: create target + add two different ingredients (writer) → real loader → real resolver.
        JsonObject root = new JsonObject();
        AdminShopConfigWriter.addBarterTargetToRoot(root, DIAMOND_BARTER, 1);
        AdminShopConfigWriter.addBarterIngredientToRoot(root, "diamond_1", "minecraft:emerald", "", 3);
        AdminShopConfigWriter.addBarterIngredientToRoot(root, "diamond_1", "minecraft:gold_ingot", "", 1);

        ShopDefinition def = ShopDefinitionLoader.parseJson(root.toString(), "test-admin.json");
        ItemDef item = def.items().get(0);
        BarterRecipeDef recipe = def.barterRecipes().get(0);
        assertEquals(2, recipe.ingredients().size(), "both ingredients survive the loader");
        var resolved = ShopCatalog.resolveBarterTargetIn(def, recipe.targetItemId());
        assertTrue(resolved.isPresent());
        assertEquals(item.resolutionKey(), resolved.get().resolutionKey(),
                "recipe resolves to the target ⇒ hasBarterRecipes true (Barter tab)");
    }
}
