package com.enviouse.futureshops.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads shop definitions from {@code config/futureshops/shops/*.json}.
 *
 * <p>If the directory is empty on first run, writes {@code default.json} and uses it.
 * JSON schema: see {@code DEFAULT_SHOP_JSON} for a fully annotated example.
 * Prices are in minor currency units (e.g., 1500 = $15.00 at 2 dp).
 */
public final class ShopDefinitionLoader {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ShopDefinitionLoader() {
    }

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    /**
     * Loads all shop definitions from disk and returns them as an immutable list.
     * Never returns null; falls back to the hardcoded default if loading fails entirely.
     */
    public static List<ShopDefinition> loadAll() {
        Path shopsDir = FMLPaths.CONFIGDIR.get().resolve("futureshops").resolve("shops");

        try {
            Files.createDirectories(shopsDir);
        } catch (IOException e) {
            LOGGER.error("[FutureShops] Could not create shops config directory '{}': {}", shopsDir, e.getMessage());
            return List.of(buildDefaultShop());
        }

        // Collect .json files
        List<Path> jsonFiles = new ArrayList<>();
        try (var stream = Files.list(shopsDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                  .forEach(jsonFiles::add);
        } catch (IOException e) {
            LOGGER.error("[FutureShops] Failed to list files in '{}': {}", shopsDir, e.getMessage());
        }

        // First run: write and load the default shop
        if (jsonFiles.isEmpty()) {
            Path defaultFile = shopsDir.resolve("default.json");
            writeFile(defaultFile, DEFAULT_SHOP_JSON);
            jsonFiles.add(defaultFile);
        }

        List<ShopDefinition> result = new ArrayList<>();
        for (Path file : jsonFiles) {
            try {
                String content = Files.readString(file);
                ShopDefinition def = parseJson(content, file.getFileName().toString());
                if (def != null) {
                    result.add(def);
                    LOGGER.info("[FutureShops] Loaded shop '{}' from {}", def.shopId(), file.getFileName());
                }
            } catch (IOException e) {
                LOGGER.error("[FutureShops] Failed to read '{}': {}", file.getFileName(), e.getMessage());
            }
        }

        if (result.isEmpty()) {
            LOGGER.warn("[FutureShops] No valid shop definitions loaded — using hardcoded default.");
            result.add(buildDefaultShop());
        }

        return List.copyOf(result);
    }

    // -------------------------------------------------------------------------
    // JSON parsing
    // -------------------------------------------------------------------------

    private static ShopDefinition parseJson(String json, String filename) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            String shopId      = getString(root, "shopId", "default");
            String displayName = getString(root, "displayName", shopId);

            List<CategoryDef> categories = new ArrayList<>();
            if (root.has("categories")) {
                for (JsonElement el : root.getAsJsonArray("categories")) {
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("id")) continue;
                    categories.add(new CategoryDef(
                            o.get("id").getAsString(),
                            getString(o, "displayName", o.get("id").getAsString()),
                            getInt(o, "sortOrder", 0)));
                }
            }

            List<ItemDef> items = new ArrayList<>();
            if (root.has("items")) {
                for (JsonElement el : root.getAsJsonArray("items")) {
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("itemId")) continue;
                    items.add(new ItemDef(
                            o.get("itemId").getAsString(),
                            getString(o, "displayName", null),
                            getLong(o, "buyPrice", 0L),
                            getLong(o, "sellPrice", 0L),
                            getInt(o, "stock", -1),
                            getBool(o, "barterEnabled", false),
                            getString(o, "categoryId", "all"),
                            getInt(o, "stockRefreshSeconds", 0)));
                }
            }

            List<PromoDef> promos = new ArrayList<>();
            if (root.has("promos")) {
                for (JsonElement el : root.getAsJsonArray("promos")) {
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("promoId")) continue;
                    promos.add(new PromoDef(
                            o.get("promoId").getAsString(),
                            getString(o, "promoType", "PERCENTAGE"),
                            getString(o, "targetItemId", ""),
                            getDouble(o, "discountValue", 0.0),
                            getLong(o, "endTimeEpoch", 0L)));
                }
            }

            List<BarterRecipeDef> barterRecipes = new ArrayList<>();
            if (root.has("barterRecipes")) {
                for (JsonElement el : root.getAsJsonArray("barterRecipes")) {
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("recipeId") || !o.has("targetItemId") || !o.has("ingredients")) continue;

                    List<BarterIngredientDef> ingredients = new ArrayList<>();
                    for (JsonElement ingredientEl : o.getAsJsonArray("ingredients")) {
                        JsonObject ingredient = ingredientEl.getAsJsonObject();
                        if (!ingredient.has("itemId")) continue;
                        ingredients.add(new BarterIngredientDef(
                                ingredient.get("itemId").getAsString(),
                                getInt(ingredient, "count", 1)));
                    }

                    barterRecipes.add(new BarterRecipeDef(
                            o.get("recipeId").getAsString(),
                            o.get("targetItemId").getAsString(),
                            getInt(o, "outputCount", 1),
                            List.copyOf(ingredients)));
                }
            }

            return new ShopDefinition(shopId, displayName,
                    List.copyOf(categories), List.copyOf(items), List.copyOf(promos), List.copyOf(barterRecipes));

        } catch (Exception e) {
            LOGGER.error("[FutureShops] Parse error in '{}': {}", filename, e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // JSON helpers
    // -------------------------------------------------------------------------

    private static String getString(JsonObject o, String key, String fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
    }

    private static int getInt(JsonObject o, String key, int fallback) {
        return o.has(key) ? o.get(key).getAsInt() : fallback;
    }

    private static long getLong(JsonObject o, String key, long fallback) {
        return o.has(key) ? o.get(key).getAsLong() : fallback;
    }

    private static double getDouble(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }

    private static boolean getBool(JsonObject o, String key, boolean fallback) {
        return o.has(key) ? o.get(key).getAsBoolean() : fallback;
    }

    // -------------------------------------------------------------------------
    // File write helper
    // -------------------------------------------------------------------------

    private static void writeFile(Path path, String content) {
        try {
            Files.writeString(path, content);
            LOGGER.info("[FutureShops] Wrote default shop config to {}", path);
        } catch (IOException e) {
            LOGGER.error("[FutureShops] Failed to write default shop to '{}': {}", path, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Hardcoded default catalog — also written as the initial default.json
    // -------------------------------------------------------------------------

    private static ShopDefinition buildDefaultShop() {
        List<CategoryDef> cats = List.of(
                new CategoryDef("all",       "All",       0),
                new CategoryDef("tools",     "Tools",     1),
                new CategoryDef("materials", "Materials", 2),
                new CategoryDef("food",      "Food",      3));

        List<ItemDef> items = List.of(
                new ItemDef("minecraft:diamond_pickaxe",         "Diamond Pickaxe",         2000L, 1000L, -1,  false, "tools"),
                new ItemDef("minecraft:diamond_sword",           "Diamond Sword",            1500L,  750L, -1,  false, "tools"),
                new ItemDef("minecraft:iron_sword",              "Iron Sword",                500L,  250L, -1,  false, "tools"),
                new ItemDef("minecraft:diamond",                 "Diamond",                   500L,  250L, -1,  false, "materials"),
                new ItemDef("minecraft:emerald",                 "Emerald",                   300L,  150L, -1,  true,  "materials"),
                new ItemDef("minecraft:iron_ingot",              "Iron Ingot",                 50L,   25L, 100, false, "materials"),
                new ItemDef("minecraft:gold_ingot",              "Gold Ingot",                150L,   75L, -1,  false, "materials"),
                new ItemDef("minecraft:netherite_ingot",         "Netherite Ingot",          5000L, 2500L, 10,  false, "materials"),
                new ItemDef("minecraft:bread",                   "Bread",                      10L,    5L, -1,  false, "food"),
                new ItemDef("minecraft:cooked_beef",             "Steak",                      20L,   10L, -1,  false, "food"),
                new ItemDef("minecraft:golden_apple",            "Golden Apple",              200L,  100L, 50,  false, "food"),
                new ItemDef("minecraft:enchanted_golden_apple",  "Enchanted Golden Apple",   5000L,    0L, 5,   false, "food"));

        List<PromoDef> promos = List.of(
                new PromoDef("promo_emerald_10", "PERCENTAGE", "minecraft:emerald", 10.0, 0L));

        List<BarterRecipeDef> barterRecipes = List.of(
                new BarterRecipeDef(
                        "emerald_trade_iron",
                        "minecraft:emerald",
                        1,
                        List.of(new BarterIngredientDef("minecraft:iron_ingot", 4))),
                new BarterRecipeDef(
                        "golden_apple_trade_gold",
                        "minecraft:golden_apple",
                        1,
                        List.of(new BarterIngredientDef("minecraft:gold_ingot", 8), new BarterIngredientDef("minecraft:apple", 1))));

        return new ShopDefinition("default", "Server Shop",
                List.copyOf(cats), List.copyOf(items), List.copyOf(promos), List.copyOf(barterRecipes));
    }

    // ─── Default JSON template written to disk on first run ──────────────────
    private static final String DEFAULT_SHOP_JSON = """
            {
              "shopId": "default",
              "displayName": "Server Shop",
              "categories": [
                { "id": "all",       "displayName": "All",       "sortOrder": 0 },
                { "id": "tools",     "displayName": "Tools",     "sortOrder": 1 },
                { "id": "materials", "displayName": "Materials", "sortOrder": 2 },
                { "id": "food",      "displayName": "Food",      "sortOrder": 3 }
              ],
              "items": [
                { "itemId": "minecraft:diamond_pickaxe",        "displayName": "Diamond Pickaxe",        "buyPrice": 2000, "sellPrice": 1000, "stock": -1,  "categoryId": "tools"     },
                { "itemId": "minecraft:diamond_sword",          "displayName": "Diamond Sword",          "buyPrice": 1500, "sellPrice":  750, "stock": -1,  "categoryId": "tools"     },
                { "itemId": "minecraft:iron_sword",             "displayName": "Iron Sword",             "buyPrice":  500, "sellPrice":  250, "stock": -1,  "categoryId": "tools"     },
                { "itemId": "minecraft:diamond",                "displayName": "Diamond",                "buyPrice":  500, "sellPrice":  250, "stock": -1,  "categoryId": "materials" },
                { "itemId": "minecraft:emerald",                "displayName": "Emerald",                "buyPrice":  300, "sellPrice":  150, "stock": -1,  "barterEnabled": true, "categoryId": "materials", "promoId": "promo_emerald_10" },
                { "itemId": "minecraft:iron_ingot",             "displayName": "Iron Ingot",             "buyPrice":   50, "sellPrice":   25, "stock": 100, "categoryId": "materials", "stockRefreshSeconds": 300 },
                { "itemId": "minecraft:gold_ingot",             "displayName": "Gold Ingot",             "buyPrice":  150, "sellPrice":   75, "stock": -1,  "categoryId": "materials" },
                { "itemId": "minecraft:netherite_ingot",        "displayName": "Netherite Ingot",        "buyPrice": 5000, "sellPrice": 2500, "stock":  10, "categoryId": "materials" },
                { "itemId": "minecraft:bread",                  "displayName": "Bread",                  "buyPrice":   10, "sellPrice":    5, "stock": -1,  "categoryId": "food"      },
                { "itemId": "minecraft:cooked_beef",            "displayName": "Steak",                  "buyPrice":   20, "sellPrice":   10, "stock": -1,  "categoryId": "food"      },
                { "itemId": "minecraft:golden_apple",           "displayName": "Golden Apple",           "buyPrice":  200, "sellPrice":  100, "stock":  50, "categoryId": "food"      },
                { "itemId": "minecraft:enchanted_golden_apple", "displayName": "Enchanted Golden Apple", "buyPrice": 5000, "sellPrice":    0, "stock":   5, "categoryId": "food"      }
              ],
              "promos": [
                { "promoId": "promo_emerald_10", "promoType": "PERCENTAGE", "targetItemId": "minecraft:emerald", "discountValue": 10.0, "endTimeEpoch": 0 }
              ],
              "barterRecipes": [
                {
                  "recipeId": "emerald_trade_iron",
                  "targetItemId": "minecraft:emerald",
                  "outputCount": 1,
                  "ingredients": [
                    { "itemId": "minecraft:iron_ingot", "count": 4 }
                  ]
                },
                {
                  "recipeId": "golden_apple_trade_gold",
                  "targetItemId": "minecraft:golden_apple",
                  "outputCount": 1,
                  "ingredients": [
                    { "itemId": "minecraft:gold_ingot", "count": 8 },
                    { "itemId": "minecraft:apple", "count": 1 }
                  ]
                }
              ]
            }
            """;
}


