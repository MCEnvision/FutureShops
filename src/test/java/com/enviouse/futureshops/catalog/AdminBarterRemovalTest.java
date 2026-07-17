package com.enviouse.futureshops.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@code /shopadmin items barter <id> off} — the in-game way to make a global-catalog listing
 * money-only by deleting barter recipes that target it. Global "barter" is not a per-item flag; an
 * item is barterable only because a barterRecipes entry targets it, so removal is target-matching.
 */
class AdminBarterRemovalTest {

    private static JsonObject recipe(String recipeId, String targetItemId) {
        JsonObject r = new JsonObject();
        r.addProperty("recipeId", recipeId);
        r.addProperty("targetItemId", targetItemId);
        r.add("ingredients", new JsonArray());
        return r;
    }

    private static JsonObject rootWith(JsonObject... recipes) {
        JsonArray arr = new JsonArray();
        for (JsonObject r : recipes) arr.add(r);
        JsonObject root = new JsonObject();
        root.add("barterRecipes", arr);
        return root;
    }

    @Test
    void removesRecipeMatchingRegistryItemId() {
        // Legacy single-variant listing: resolutionKey == registry id.
        JsonObject root = rootWith(
                recipe("golden_apple_trade_gold", "minecraft:golden_apple"),
                recipe("keep_me", "minecraft:diamond"));

        int removed = AdminShopConfigWriter.removeBarterRecipesFromRoot(
                root, "minecraft:golden_apple", "minecraft:golden_apple");

        assertEquals(1, removed);
        JsonArray kept = root.getAsJsonArray("barterRecipes");
        assertEquals(1, kept.size());
        assertEquals("keep_me", kept.get(0).getAsJsonObject().get("recipeId").getAsString());
    }

    @Test
    void removesRecipeMatchingListingIdWhenTargetIsTheGeneratedId() {
        // Multi-variant listing added via `items add`: id "apple_1", registry "minecraft:apple".
        // A recipe may target either form; both must be strippable.
        JsonObject root = rootWith(
                recipe("by_listing_id", "apple_1"),
                recipe("by_registry_id", "minecraft:apple"),
                recipe("unrelated", "minecraft:bread"));

        int removed = AdminShopConfigWriter.removeBarterRecipesFromRoot(root, "apple_1", "minecraft:apple");

        assertEquals(2, removed);
        JsonArray kept = root.getAsJsonArray("barterRecipes");
        assertEquals(1, kept.size());
        assertEquals("unrelated", kept.get(0).getAsJsonObject().get("recipeId").getAsString());
    }

    @Test
    void removesNothingWhenNoRecipeTargetsTheListing() {
        JsonObject root = rootWith(recipe("other", "minecraft:emerald"));
        assertEquals(0, AdminShopConfigWriter.removeBarterRecipesFromRoot(root, "apple_1", "minecraft:apple"));
        assertEquals(1, root.getAsJsonArray("barterRecipes").size());
    }

    @Test
    void handlesMissingOrEmptyBarterRecipesArray() {
        assertEquals(0, AdminShopConfigWriter.removeBarterRecipesFromRoot(new JsonObject(), "x", "y"));
        assertEquals(0, AdminShopConfigWriter.removeBarterRecipesFromRoot(rootWith(), "x", "y"));
    }

    @Test
    void leavesArrayUntouchedWhenNothingRemoved() {
        JsonObject root = rootWith(recipe("a", "minecraft:emerald"), recipe("b", "minecraft:gold_ingot"));
        int before = root.getAsJsonArray("barterRecipes").size();
        assertEquals(0, AdminShopConfigWriter.removeBarterRecipesFromRoot(root, "nope", "also:nope"));
        assertEquals(before, root.getAsJsonArray("barterRecipes").size());
        assertTrue(root.has("barterRecipes"));
        assertFalse(root.getAsJsonArray("barterRecipes").isEmpty());
    }
}
