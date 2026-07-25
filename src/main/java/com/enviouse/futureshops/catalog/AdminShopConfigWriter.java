package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.server.escrow.stock.migration.CatalogStockRuntime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Mutates the on-disk admin shop config ({@code admin.json}). All edits are reflected in the
 * file, then {@link ShopCatalog#reload(MinecraftServer)} is called so the change takes effect
 * for active sessions.
 */
public final class AdminShopConfigWriter {
    private static final long MAX_ADMIN_JSON_BYTES = 8L * 1024L * 1024L;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int BACKUP_COUNT = 3;

    private AdminShopConfigWriter() {
    }

    /**
     * Adds the item to {@code admin.json}, or replaces an existing entry with the same itemId.
     * Triggers a catalog reload on success.
     */
    public static synchronized boolean addOrUpdateItem(MinecraftServer server, AdminShopItemSpec spec) {
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return false;

        JsonArray items = ensureArray(root, "items");
        int existing = indexOfByField(items, "itemId", spec.itemId());
        JsonObject entry = buildItemEntry(spec);
        if (existing >= 0) {
            items.set(existing, entry);
        } else {
            items.add(entry);
        }

        if (!writeJson(path, root)) return false;
        CatalogStockRuntime.reload(server);
        return true;
    }

    /**
     * Removes the entry whose {@code itemId} matches (case-insensitive) from {@code admin.json}.
     * Triggers a catalog reload on success.
     */
    public static synchronized boolean removeItem(MinecraftServer server, String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return false;

        JsonArray items = ensureArray(root, "items");
        int idx = indexOfByField(items, "itemId", itemId);
        if (idx < 0) return false;
        items.remove(idx);

        if (!writeJson(path, root)) return false;
        CatalogStockRuntime.reload(server);
        return true;
    }

    // ─── Per-listing-id API (used by /shopadmin items) ───────────────────────
    //
    // These coexist with the legacy itemId-keyed addOrUpdateItem/removeItem (used by the
    // /shopadmin adminshop wizard). They address entries by their stable listingId — the "id"
    // field, or the itemId for legacy entries that predate it — so several listings can share a
    // registry itemId yet be edited/removed individually.

    /**
     * Appends a NEW listing (never replaces) and returns its assigned listing id, or an empty string
     * on collision / I/O failure. A blank {@code spec.listingId()} auto-generates a unique id from
     * the itemId; an explicit id must not collide. Triggers a catalog reload on success.
     */
    public static synchronized String addWithGeneratedId(MinecraftServer server, AdminShopItemSpec spec) {
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return "";
        JsonArray items = ensureArray(root, "items");

        String listingId;
        if (spec.listingId() == null || spec.listingId().isBlank()) {
            listingId = nextId(items, spec.itemId());
        } else {
            listingId = spec.listingId().trim();
            if (indexOfByListingId(items, listingId) >= 0) {
                return ""; // explicit id collides — never silently overwrite
            }
        }

        AdminShopItemSpec stamped = withListingId(spec, listingId);
        items.add(buildItemEntry(stamped));

        if (!writeJson(path, root)) return "";
        CatalogStockRuntime.reload(server);
        return listingId;
    }

    /**
     * Adds one or more listings AND a matching barter recipe for each, in a SINGLE read/write/reload
     * (avoids the double-reload stock re-seed of add-then-add-recipe). Each recipe targets its
     * listing's GENERATED resolution key — never the bare registry itemId — so the Barter-tab filter
     * binds to the exact new listing rather than a same-item sibling. The recipe is paid with
     * {@code ingredientItemId × ingredientCount} (blank NBT = lenient identity match). Returns the
     * number of listings actually added (skips explicit-id collisions).
     */
    public static synchronized int addItemsWithBarter(MinecraftServer server, List<AdminShopItemSpec> specs,
                                                      int outputCount, String ingredientItemId, int ingredientCount) {
        if (specs == null || specs.isEmpty() || ingredientItemId == null || ingredientItemId.isBlank()) return 0;
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return 0;
        int added = addItemsWithBarterToRoot(root, specs, outputCount, ingredientItemId, ingredientCount);
        if (added == 0) return 0;
        if (!writeJson(path, root)) return 0;
        CatalogStockRuntime.reload(server);
        return added;
    }

    /**
     * Pure JSON form of {@link #addItemsWithBarter}: for each spec, append a listing (carrying its
     * categoryId + generated {@code id}) AND a barter recipe whose {@code targetItemId} is that same
     * generated id, so {@code ShopCatalog.reload} flags the new listing {@code hasBarterRecipes}.
     * Returns the number added (explicit-id collisions skipped). Package-private for unit tests.
     */
    static int addItemsWithBarterToRoot(JsonObject root, List<AdminShopItemSpec> specs,
                                        int outputCount, String ingredientItemId, int ingredientCount) {
        if (root == null || specs == null || specs.isEmpty()
                || ingredientItemId == null || ingredientItemId.isBlank()) return 0;
        JsonArray items = ensureArray(root, "items");
        JsonArray recipes = ensureArray(root, "barterRecipes");
        int outCount = Math.max(1, outputCount);
        int ingCount = Math.max(1, ingredientCount);
        int added = 0;
        for (AdminShopItemSpec spec : specs) {
            String listingId;
            if (spec.listingId() == null || spec.listingId().isBlank()) {
                listingId = nextId(items, spec.itemId());
            } else {
                listingId = spec.listingId().trim();
                if (indexOfByListingId(items, listingId) >= 0) continue; // explicit-id collision — skip
            }
            AdminShopItemSpec stamped = withListingId(spec, listingId);
            items.add(buildItemEntry(stamped));

            JsonObject recipe = new JsonObject();
            recipe.addProperty("recipeId", nextRecipeId(recipes, listingId));
            recipe.addProperty("targetItemId", stamped.resolutionKey()); // the generated listing id
            recipe.addProperty("outputCount", outCount);
            JsonArray ings = new JsonArray();
            JsonObject ing = new JsonObject();
            ing.addProperty("itemId", ingredientItemId);
            ing.addProperty("count", ingCount);
            ings.add(ing);
            recipe.add("ingredients", ings);
            recipes.add(recipe);
            added++;
        }
        return added;
    }

    /** Unique recipeId derived from the target key (mirrors {@link #nextId}). */
    private static String nextRecipeId(JsonArray recipes, String targetKey) {
        String base = ("barter_" + (targetKey == null ? "recipe" : targetKey))
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        int n = 1;
        String candidate = base + "_" + n;
        while (indexOfByField(recipes, "recipeId", candidate) >= 0) {
            n++;
            candidate = base + "_" + n;
        }
        return candidate;
    }

    /**
     * Replaces the entry addressed by {@code listingId} in place (keeping its id), using all fields
     * from {@code spec}. Returns {@code false} if no such listing exists. Triggers a reload on success.
     *
     * <p>The {@code /shopadmin items edit} command re-captures the held item, so an edit MAY change the
     * listing's registry itemId/NBT while keeping its listingId. That is intentional (the listing slot is
     * stable); be aware that listingId-keyed state — dynamic pricing volume, stock-refresh timing — then
     * carries over to the repurposed listing.
     */
    public static synchronized boolean editById(MinecraftServer server, String listingId, AdminShopItemSpec spec) {
        if (listingId == null || listingId.isBlank()) return false;
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return false;
        JsonArray items = ensureArray(root, "items");
        int idx = indexOfByListingId(items, listingId);
        if (idx < 0) return false;
        items.set(idx, buildItemEntry(withListingId(spec, listingId.trim())));
        if (!writeJson(path, root)) return false;
        CatalogStockRuntime.reload(server);
        return true;
    }

    /** Removes the entry addressed by {@code listingId} (matches an explicit id, or a legacy itemId). */
    public static synchronized boolean removeById(MinecraftServer server, String listingId) {
        if (listingId == null || listingId.isBlank()) return false;
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return false;
        JsonArray items = ensureArray(root, "items");
        int idx = indexOfByListingId(items, listingId);
        if (idx < 0) return false;
        // Capture the listing's target forms BEFORE removing it, so we can also strip any barter
        // recipe targeting it. Otherwise the recipe is orphaned, and because nextId REUSES freed
        // ids, a later same-item add would resurrect that stale trade onto the new listing (even a
        // money-only one) — a silent economy leak the OP thought they'd deleted.
        JsonObject entry = items.get(idx).getAsJsonObject();
        String resolutionKey = entry.has("id") ? entry.get("id").getAsString()
                : (entry.has("itemId") ? entry.get("itemId").getAsString() : listingId);
        String registryItemId = entry.has("itemId") ? entry.get("itemId").getAsString() : resolutionKey;
        items.remove(idx);
        removeBarterRecipesFromRoot(root, resolutionKey, registryItemId);
        if (!writeJson(path, root)) return false;
        CatalogStockRuntime.reload(server);
        return true;
    }

    /**
     * Sets (or clears, when {@code seconds <= 0}) the stock-refresh interval on a listing without
     * needing the item in hand. Returns {@code false} if no such listing exists.
     */
    public static synchronized boolean setStockRefresh(MinecraftServer server, String listingId, int seconds) {
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return false;
        JsonArray items = ensureArray(root, "items");
        int idx = indexOfByListingId(items, listingId);
        if (idx < 0) return false;
        JsonObject o = items.get(idx).getAsJsonObject();
        if (seconds > 0) {
            o.addProperty("stockRefreshSeconds", seconds);
        } else {
            o.remove("stockRefreshSeconds");
        }
        if (!writeJson(path, root)) return false;
        CatalogStockRuntime.reload(server);
        return true;
    }

    /**
     * Removes every barter recipe whose target is the given listing, making that listing money-only.
     * Global-catalog "barter" is not a per-item flag — an item is barterable purely because a
     * {@code barterRecipes[]} entry targets it — so this is the in-game way to turn barter OFF.
     * Matches a recipe's {@code targetItemId} against the listing's stable resolution key OR its
     * registry item id (the two forms a target may be written as; a legacy single-variant listing
     * has both equal). Triggers a catalog reload on success. Returns the number of recipes removed.
     */
    public static synchronized int removeBarterRecipesTargeting(MinecraftServer server,
                                                                String resolutionKey, String registryItemId) {
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return 0;
        int removed = removeBarterRecipesFromRoot(root, resolutionKey, registryItemId);
        if (removed == 0) return 0;
        if (!writeJson(path, root)) return 0;
        CatalogStockRuntime.reload(server);
        return removed;
    }

    /**
     * Pure {@code barterRecipes} filter for {@link #removeBarterRecipesTargeting} — mutates {@code root}
     * in place, dropping recipes targeting the listing, and returns how many were removed. Split out so
     * the matching logic is unit-testable without a running server. Does not write or reload.
     */
    static int removeBarterRecipesFromRoot(JsonObject root, String resolutionKey, String registryItemId) {
        if (root == null || !root.has("barterRecipes") || !root.get("barterRecipes").isJsonArray()) {
            return 0;
        }
        JsonArray recipes = root.getAsJsonArray("barterRecipes");
        JsonArray kept = new JsonArray();
        int removed = 0;
        for (JsonElement el : recipes) {
            if (el.isJsonObject() && el.getAsJsonObject().has("targetItemId")) {
                String target = el.getAsJsonObject().get("targetItemId").getAsString();
                if (target.equals(resolutionKey) || target.equals(registryItemId)) {
                    removed++;
                    continue;
                }
            }
            kept.add(el);
        }
        if (removed > 0) {
            root.add("barterRecipes", kept);
        }
        return removed;
    }

    // ─── Multi-ingredient barter editor (OP adds ingredients one held item at a time) ─────────

    /**
     * Creates a barter TARGET listing from {@code spec} (money prices 0 = barter-only) plus an EMPTY
     * barter recipe targeting it, and returns the generated listingId. Ingredients are added later,
     * one at a time, via {@link #addBarterIngredient}. A zero-ingredient recipe is rejected at the buy
     * path (ShopBarterService), so this transient "no cost yet" state can't be exploited. Null on I/O fail.
     */
    public static synchronized String addBarterTargetHeld(MinecraftServer server, AdminShopItemSpec spec, int outputCount) {
        if (spec == null) return null;
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return null;
        String listingId = addBarterTargetToRoot(root, spec, outputCount);
        if (listingId == null || !writeJson(path, root)) return null;
        CatalogStockRuntime.reload(server);
        return listingId;
    }

    /**
     * Batch variant used by the searchable barter-output picker. Every selected registry item is
     * appended with an empty, safe-by-default recipe in one read/write/reload. The returned ids are
     * ordered like {@code specs} so the client can walk the operator through each ingredient editor.
     */
    public static synchronized List<String> addBarterTargets(MinecraftServer server,
                                                             List<AdminShopItemSpec> specs,
                                                             int outputCount) {
        if (specs == null || specs.isEmpty()) return List.of();
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return List.of();
        List<String> listingIds = addBarterTargetsToRoot(root, specs, outputCount);
        if (listingIds.isEmpty() || !writeJson(path, root)) return List.of();
        CatalogStockRuntime.reload(server);
        return listingIds;
    }

    /** Pure batch target creation for tests and the disk-backed wrapper above. */
    static List<String> addBarterTargetsToRoot(JsonObject root, List<AdminShopItemSpec> specs, int outputCount) {
        if (root == null || specs == null || specs.isEmpty()) return List.of();
        List<String> listingIds = new ArrayList<>();
        for (AdminShopItemSpec spec : specs) {
            String listingId = addBarterTargetToRoot(root, spec, outputCount);
            if (listingId != null && !listingId.isBlank()) {
                listingIds.add(listingId);
            }
        }
        return List.copyOf(listingIds);
    }

    /** Pure: appends the target listing + an empty recipe; returns the generated listingId. */
    static String addBarterTargetToRoot(JsonObject root, AdminShopItemSpec spec, int outputCount) {
        if (root == null || spec == null || spec.itemId() == null || spec.itemId().isBlank()) return null;
        JsonArray items = ensureArray(root, "items");
        JsonArray recipes = ensureArray(root, "barterRecipes");
        String listingId = nextId(items, spec.itemId());
        items.add(buildItemEntry(withListingId(spec, listingId)));
        JsonObject recipe = new JsonObject();
        recipe.addProperty("recipeId", nextRecipeId(recipes, listingId));
        recipe.addProperty("targetItemId", listingId);
        recipe.addProperty("outputCount", Math.max(1, outputCount));
        recipe.add("ingredients", new JsonArray());
        recipes.add(recipe);
        return listingId;
    }

    /** Appends (or count-merges) one ingredient onto the recipe targeting {@code targetKey}, creating
     *  the recipe if none exists. {@code ingredientNbt} blank = lenient match. Reloads on success. */
    public static synchronized boolean addBarterIngredient(MinecraftServer server, String targetKey,
                                                           String ingredientItemId, String ingredientNbt, int count) {
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return false;
        if (!addBarterIngredientToRoot(root, targetKey, ingredientItemId, ingredientNbt, count)) return false;
        if (!writeJson(path, root)) return false;
        CatalogStockRuntime.reload(server);
        return true;
    }

    static boolean addBarterIngredientToRoot(JsonObject root, String targetKey,
                                             String ingredientItemId, String ingredientNbt, int count) {
        if (root == null || targetKey == null || targetKey.isBlank()
                || ingredientItemId == null || ingredientItemId.isBlank()) return false;
        JsonArray recipes = ensureArray(root, "barterRecipes");
        JsonObject recipe = findRecipeByTarget(recipes, targetKey);
        if (recipe == null) {
            recipe = new JsonObject();
            recipe.addProperty("recipeId", nextRecipeId(recipes, targetKey));
            recipe.addProperty("targetItemId", targetKey);
            recipe.addProperty("outputCount", 1);
            recipe.add("ingredients", new JsonArray());
            recipes.add(recipe);
        }
        if (!recipe.has("ingredients") || !recipe.get("ingredients").isJsonArray()) {
            recipe.add("ingredients", new JsonArray());
        }
        JsonArray ings = recipe.getAsJsonArray("ingredients");
        int add = Math.max(1, count);
        String nbt = ingredientNbt == null ? "" : ingredientNbt;
        // Count-merge with an existing identical (itemId + nbt) line so adding the same item twice
        // bumps its count instead of stacking duplicate rows.
        for (JsonElement el : ings) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String id = o.has("itemId") ? o.get("itemId").getAsString() : "";
            String enbt = o.has("nbt") ? o.get("nbt").getAsString() : "";
            if (id.equals(ingredientItemId) && enbt.equals(nbt)) {
                o.addProperty("count", (o.has("count") ? o.get("count").getAsInt() : 1) + add);
                return true;
            }
        }
        JsonObject ing = new JsonObject();
        ing.addProperty("itemId", ingredientItemId);
        ing.addProperty("count", add);
        if (!nbt.isBlank()) ing.addProperty("nbt", nbt);
        ings.add(ing);
        return true;
    }

    /** Removes ingredient {@code index} from the recipe targeting {@code targetKey}; drops the whole
     *  recipe when its last ingredient goes (a 0-ingredient recipe would be a free trade). Reloads. */
    public static synchronized boolean removeBarterIngredient(MinecraftServer server, String targetKey, int index) {
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return false;
        if (!removeBarterIngredientFromRoot(root, targetKey, index)) return false;
        if (!writeJson(path, root)) return false;
        CatalogStockRuntime.reload(server);
        return true;
    }

    static boolean removeBarterIngredientFromRoot(JsonObject root, String targetKey, int index) {
        if (root == null || targetKey == null || !root.has("barterRecipes") || !root.get("barterRecipes").isJsonArray()) {
            return false;
        }
        JsonArray recipes = root.getAsJsonArray("barterRecipes");
        JsonObject recipe = findRecipeByTarget(recipes, targetKey);
        if (recipe == null || !recipe.has("ingredients") || !recipe.get("ingredients").isJsonArray()) return false;
        JsonArray ings = recipe.getAsJsonArray("ingredients");
        if (index < 0 || index >= ings.size()) return false;
        ings.remove(index);
        if (ings.size() == 0) {
            JsonArray kept = new JsonArray();
            for (JsonElement el : recipes) {
                if (el != recipe) kept.add(el);
            }
            root.add("barterRecipes", kept);
        }
        return true;
    }

    /** Sets the output (reward) count on the recipe targeting {@code targetKey}. Reloads on success. */
    public static synchronized boolean setBarterOutputCount(MinecraftServer server, String targetKey, int count) {
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return false;
        if (!setBarterOutputCountInRoot(root, targetKey, count)) return false;
        if (!writeJson(path, root)) return false;
        CatalogStockRuntime.reload(server);
        return true;
    }

    static boolean setBarterOutputCountInRoot(JsonObject root, String targetKey, int count) {
        if (root == null || targetKey == null || !root.has("barterRecipes") || !root.get("barterRecipes").isJsonArray()) {
            return false;
        }
        JsonObject recipe = findRecipeByTarget(root.getAsJsonArray("barterRecipes"), targetKey);
        if (recipe == null) return false;
        recipe.addProperty("outputCount", Math.max(1, count));
        return true;
    }

    /** Sets one ingredient count by row index. Counts are clamped to one; the row identity is kept. */
    public static synchronized boolean setBarterIngredientCount(MinecraftServer server, String targetKey,
                                                                int index, int count) {
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null || !setBarterIngredientCountInRoot(root, targetKey, index, count)) return false;
        if (!writeJson(path, root)) return false;
        CatalogStockRuntime.reload(server);
        return true;
    }

    static boolean setBarterIngredientCountInRoot(JsonObject root, String targetKey, int index, int count) {
        if (root == null || targetKey == null || !root.has("barterRecipes")
                || !root.get("barterRecipes").isJsonArray()) return false;
        JsonObject recipe = findRecipeByTarget(root.getAsJsonArray("barterRecipes"), targetKey);
        if (recipe == null || !recipe.has("ingredients") || !recipe.get("ingredients").isJsonArray()) return false;
        JsonArray ingredients = recipe.getAsJsonArray("ingredients");
        if (index < 0 || index >= ingredients.size() || !ingredients.get(index).isJsonObject()) return false;
        ingredients.get(index).getAsJsonObject().addProperty("count", Math.max(1, count));
        return true;
    }

    /** First recipe whose targetItemId equals {@code targetKey} (case-insensitive), or null. */
    private static JsonObject findRecipeByTarget(JsonArray recipes, String targetKey) {
        if (recipes == null || targetKey == null) return null;
        String needle = targetKey.toLowerCase(Locale.ROOT);
        for (JsonElement el : recipes) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            if (o.has("targetItemId") && o.get("targetItemId").getAsString().toLowerCase(Locale.ROOT).equals(needle)) {
                return o;
            }
        }
        return null;
    }

    // ─── In-GUI admin editor API ─────────────────────────────────────────────
    //
    // Partial in-place mutations for the edit-mode GUI. Unlike editById (which rebuilds the whole
    // entry from a re-captured spec), these touch ONLY the keys they own so listing fields the GUI
    // never surfaces — nbt, expiresAtEpoch, stockRefreshSeconds — survive every save untouched.
    // Each has a pure package-private JsonObject-level helper (unit-testable without a server,
    // mirroring removeBarterRecipesFromRoot) plus a thin synchronized read/write/reload wrapper.

    /**
     * Updates the GUI-editable fields of the listing addressed by {@code listingId}: displayName
     * (blank → removed), categoryId (blank → removed, i.e. "all"), buyPrice, sellPrice and stock.
     * Never rewrites {@code nbt} / {@code expiresAtEpoch} / {@code stockRefreshSeconds} / {@code id}
     * / {@code itemId}. Returns {@code false} if no such listing exists or on I/O failure.
     * Triggers a catalog reload on success.
     */
    public static synchronized boolean updateListingFields(MinecraftServer server, String listingId,
                                                           String displayName, String categoryId,
                                                           long buyMinor, long sellMinor, int stock) {
        if (listingId == null || listingId.isBlank()) return false;
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return false;
        if (!updateListingFieldsInRoot(root, listingId, displayName, categoryId, buyMinor, sellMinor, stock)) {
            return false;
        }
        if (!writeJson(path, root)) return false;
        CatalogStockRuntime.reload(server);
        return true;
    }

    /**
     * Pure field update for {@link #updateListingFields} — mutates the matched entry in place and
     * returns whether a listing matched. Does not write or reload.
     */
    static boolean updateListingFieldsInRoot(JsonObject root, String listingId, String displayName,
                                             String categoryId, long buyMinor, long sellMinor, int stock) {
        JsonArray items = ensureArray(root, "items");
        int idx = indexOfByListingId(items, listingId);
        if (idx < 0) return false;
        JsonObject o = items.get(idx).getAsJsonObject();
        if (displayName == null || displayName.isBlank()) {
            o.remove("displayName");
        } else {
            o.addProperty("displayName", displayName.trim());
        }
        if (categoryId == null || categoryId.isBlank()) {
            o.remove("categoryId");
        } else {
            o.addProperty("categoryId", categoryId);
        }
        o.addProperty("buyPrice", buyMinor);
        o.addProperty("sellPrice", sellMinor);
        o.addProperty("stock", stock);
        return true;
    }

    /**
     * Rewrites the displayName of the category entry with the given id, KEEPING its {@code id} and
     * {@code sortOrder} untouched — so items assigned to the id and the tab ordering both survive.
     * Returns {@code false} if no category with that id exists in admin.json or on I/O failure.
     * Triggers a catalog reload on success.
     */
    public static synchronized boolean renameCategory(MinecraftServer server, String categoryId, String newDisplayName) {
        if (categoryId == null || categoryId.isBlank() || newDisplayName == null || newDisplayName.isBlank()) return false;
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return false;
        if (!renameCategoryInRoot(root, categoryId, newDisplayName.trim())) return false;
        if (!writeJson(path, root)) return false;
        CatalogStockRuntime.reload(server);
        return true;
    }

    /** Pure displayName rewrite for {@link #renameCategory}. Does not write or reload. */
    static boolean renameCategoryInRoot(JsonObject root, String categoryId, String newDisplayName) {
        JsonArray cats = ensureArray(root, "categories");
        int idx = indexOfByField(cats, "id", categoryId);
        if (idx < 0) return false;
        cats.get(idx).getAsJsonObject().addProperty("displayName", newDisplayName);
        return true;
    }

    /**
     * Rewrites each listed category's {@code sortOrder} to its index in {@code categoryIdsInOrder}
     * (0..n-1); categories not listed keep their current sortOrder. The edit service composes the
     * neighbour swap and passes the full desired order. Returns {@code false} when no listed
     * category matched or on I/O failure. Triggers a catalog reload on success.
     */
    public static synchronized boolean setCategorySortOrders(MinecraftServer server, List<String> categoryIdsInOrder) {
        if (categoryIdsInOrder == null || categoryIdsInOrder.isEmpty()) return false;
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return false;
        if (setCategorySortOrdersInRoot(root, categoryIdsInOrder) == 0) return false;
        if (!writeJson(path, root)) return false;
        CatalogStockRuntime.reload(server);
        return true;
    }

    /**
     * Pure sortOrder rewrite for {@link #setCategorySortOrders} — returns how many categories were
     * re-numbered. Does not write or reload.
     */
    static int setCategorySortOrdersInRoot(JsonObject root, List<String> categoryIdsInOrder) {
        JsonArray cats = ensureArray(root, "categories");
        int updated = 0;
        for (int i = 0; i < categoryIdsInOrder.size(); i++) {
            int idx = indexOfByField(cats, "id", categoryIdsInOrder.get(i));
            if (idx < 0) continue;
            cats.get(idx).getAsJsonObject().addProperty("sortOrder", i);
            updated++;
        }
        return updated;
    }

    /**
     * Returns the ids of admin.json's categories sorted by {@code sortOrder} (stable for ties, file
     * order). Used by the edit service to compose MOVE_CATEGORY swaps. Reads the file directly.
     */
    public static List<String> listCategoryIdsSorted(MinecraftServer server) {
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return List.of();
        return categoryIdsSortedFromRoot(root);
    }

    /** Pure ordered-id extraction for {@link #listCategoryIdsSorted}. */
    static List<String> categoryIdsSortedFromRoot(JsonObject root) {
        JsonArray cats = ensureArray(root, "categories");
        record IdOrder(String id, int sortOrder) {
        }
        List<IdOrder> rows = new ArrayList<>();
        for (JsonElement el : cats) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            if (!o.has("id")) continue;
            int sort = 0;
            if (o.has("sortOrder")) {
                try {
                    sort = o.get("sortOrder").getAsInt();
                } catch (Exception ignored) {
                }
            }
            rows.add(new IdOrder(o.get("id").getAsString(), sort));
        }
        rows.sort(java.util.Comparator.comparingInt(IdOrder::sortOrder));
        return rows.stream().map(IdOrder::id).toList();
    }

    /**
     * Appends a batch of NEW listings — ONE file read, ONE write and ONE catalog reload for the
     * whole batch (the item picker can add dozens at once; per-item reloads would refill stock and
     * hammer the disk). Blank {@code listingId}s get generated ids; explicit ids that collide are
     * skipped. Returns how many listings were added (0 on I/O failure).
     */
    public static synchronized int addItems(MinecraftServer server, List<AdminShopItemSpec> specs) {
        if (specs == null || specs.isEmpty()) return 0;
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return 0;
        int added = addItemsToRoot(root, specs);
        if (added == 0) return 0;
        if (!writeJson(path, root)) return 0;
        CatalogStockRuntime.reload(server);
        return added;
    }

    /**
     * Pure batch append for {@link #addItems} — returns how many entries were added. Generated ids
     * are computed against the growing array, so two same-item specs in one batch get distinct ids.
     * Does not write or reload.
     */
    static int addItemsToRoot(JsonObject root, List<AdminShopItemSpec> specs) {
        JsonArray items = ensureArray(root, "items");
        int added = 0;
        for (AdminShopItemSpec spec : specs) {
            String listingId;
            if (spec.listingId() == null || spec.listingId().isBlank()) {
                listingId = nextId(items, spec.itemId());
            } else {
                listingId = spec.listingId().trim();
                if (indexOfByListingId(items, listingId) >= 0) {
                    continue; // explicit id collides — never silently overwrite
                }
            }
            items.add(buildItemEntry(withListingId(spec, listingId)));
            added++;
        }
        return added;
    }

    /**
     * Returns a read-only snapshot of every admin.json listing (optionally filtered to one category),
     * for {@code /shopadmin items list|info}. Reads the file directly — does not reload the catalog.
     */
    public static List<AdminListingView> listItems(MinecraftServer server, String categoryFilter) {
        List<AdminListingView> out = new ArrayList<>();
        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return out;
        JsonArray items = ensureArray(root, "items");
        String filter = (categoryFilter == null || categoryFilter.isBlank() || "all".equalsIgnoreCase(categoryFilter))
                ? null : normalizeId(categoryFilter);
        for (JsonElement el : items) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            if (!o.has("itemId")) continue;
            String itemId = o.get("itemId").getAsString();
            String listingId = o.has("id") ? o.get("id").getAsString() : itemId;
            String displayName = o.has("displayName") ? o.get("displayName").getAsString() : itemId;
            long buyPrice = o.has("buyPrice") ? o.get("buyPrice").getAsLong() : 0L;
            long sellPrice = o.has("sellPrice") ? o.get("sellPrice").getAsLong() : 0L;
            int stock = o.has("stock") ? o.get("stock").getAsInt() : -1;
            int refresh = o.has("stockRefreshSeconds") ? o.get("stockRefreshSeconds").getAsInt() : 0;
            String categoryId = o.has("categoryId") ? o.get("categoryId").getAsString() : "all";
            long expiresAt = o.has("expiresAtEpoch") ? o.get("expiresAtEpoch").getAsLong() : 0L;
            boolean nbtPresent = o.has("nbt") && !o.get("nbt").getAsString().isBlank();
            if (filter != null && !normalizeId(categoryId).equals(filter)) continue;
            out.add(new AdminListingView(listingId, itemId, displayName, buyPrice, sellPrice,
                    stock, refresh, categoryId, expiresAt, nbtPresent));
        }
        return out;
    }

    /**
     * Adds a category entry (id + displayName) to {@code admin.json}'s categories array. The id is
     * derived as {@code displayName.toLowerCase().replace(' ', '_')}.
     *
     * @return {@code true} if a new category was written, {@code false} if it already exists or on I/O failure.
     */
    public static synchronized boolean addCategory(MinecraftServer server, String displayName) {
        if (displayName == null || displayName.isBlank()) return false;
        String trimmed = displayName.trim();
        String id = normalizeId(trimmed);

        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return false;

        JsonArray cats = ensureArray(root, "categories");
        if (indexOfByField(cats, "id", id) >= 0) return false;

        int maxSort = 0;
        for (int i = 0; i < cats.size(); i++) {
            JsonElement el = cats.get(i);
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            if (o.has("sortOrder")) {
                try {
                    maxSort = Math.max(maxSort, o.get("sortOrder").getAsInt());
                } catch (Exception ignored) {
                }
            }
        }

        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        entry.addProperty("displayName", trimmed);
        entry.addProperty("sortOrder", maxSort + 1);
        cats.add(entry);

        if (!writeJson(path, root)) return false;
        CatalogStockRuntime.reload(server);
        return true;
    }

    /**
     * Removes a category from {@code admin.json}. Matches by id or displayName (case-insensitive,
     * spaces-as-underscores). Items previously assigned to this categoryId in the JSON keep their
     * field but will fall back to "all" since the category no longer exists.
     */
    public static synchronized boolean removeCategory(MinecraftServer server, String idOrName) {
        if (idOrName == null || idOrName.isBlank()) return false;
        String trimmed = idOrName.trim();
        String normalizedId = normalizeId(trimmed);

        Path path = ShopDefinitionLoader.adminShopPath();
        JsonObject root = readOrInit(path);
        if (root == null) return false;

        JsonArray cats = ensureArray(root, "categories");
        int found = -1;
        for (int i = 0; i < cats.size(); i++) {
            JsonElement el = cats.get(i);
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String id = o.has("id") ? o.get("id").getAsString() : "";
            String dn = o.has("displayName") ? o.get("displayName").getAsString() : "";
            if (id.equalsIgnoreCase(trimmed)
                    || id.equalsIgnoreCase(normalizedId)
                    || dn.equalsIgnoreCase(trimmed)) {
                found = i;
                break;
            }
        }
        if (found < 0) return false;
        cats.remove(found);

        if (!writeJson(path, root)) return false;
        CatalogStockRuntime.reload(server);
        return true;
    }

    /**
     * Returns the union of category display names known to {@code admin.json} plus runtime
     * admin categories from {@link com.enviouse.futureshops.server.shop.AdminCategorySavedData}.
     * Excludes the synthetic "All" tab.
     */
    public static List<String> listAllCategoryDisplayNames(MinecraftServer server) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        LinkedHashSet<String> jsonIds = new LinkedHashSet<>();
        for (ShopDefinition def : ShopCatalog.getAllDefinitions()) {
            for (CategoryDef c : def.categories()) {
                if ("all".equalsIgnoreCase(c.id())) continue;
                names.add(c.displayName());
                jsonIds.add(c.id());
            }
        }
        // Skip any SavedData name whose normalized id already names a JSON category — otherwise a
        // GUI rename (which changes the JSON displayName but keeps the id + its SavedData twin)
        // would surface the OLD name here as a phantom duplicate.
        for (String saved : com.enviouse.futureshops.server.shop.AdminCategorySavedData.get(server).getAllSorted()) {
            if (!jsonIds.contains(normalizeId(saved))) {
                names.add(saved);
            }
        }
        return new ArrayList<>(names);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    // package-private for unit testing
    static JsonObject buildItemEntry(AdminShopItemSpec spec) {
        JsonObject o = new JsonObject();
        // Write the stable listing id only when it actually differs from itemId, so legacy-shaped
        // single-variant entries (id == itemId) stay clean and unchanged in the file.
        String listingId = spec.resolutionKey();
        if (listingId != null && !listingId.isBlank() && !listingId.equals(spec.itemId())) {
            o.addProperty("id", listingId);
        }
        o.addProperty("itemId", spec.itemId());
        if (spec.displayName() != null && !spec.displayName().isBlank()) {
            o.addProperty("displayName", spec.displayName());
        }
        o.addProperty("buyPrice", spec.buyPriceMinor());
        o.addProperty("sellPrice", spec.sellPriceMinor());
        o.addProperty("stock", spec.stock());
        if (spec.stockRefreshSeconds() > 0) {
            o.addProperty("stockRefreshSeconds", spec.stockRefreshSeconds());
        }
        if (spec.categoryId() != null && !spec.categoryId().isBlank()) {
            o.addProperty("categoryId", spec.categoryId());
        }
        // Persist captured NBT (SNBT) when present. Loader skips a blank value so
        // legacy entries continue to deserialise as bare items.
        if (spec.nbtJson() != null && !spec.nbtJson().isBlank()) {
            o.addProperty("nbt", spec.nbtJson());
        }
        // Availability-window expiry; omitted when 0 (never).
        if (spec.expiresAtEpoch() > 0L) {
            o.addProperty("expiresAtEpoch", spec.expiresAtEpoch());
        }
        return o;
    }

    private static int indexOfByField(JsonArray arr, String fieldName, String value) {
        String needle = value.toLowerCase(Locale.ROOT);
        for (int i = 0; i < arr.size(); i++) {
            JsonElement el = arr.get(i);
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            if (!o.has(fieldName)) continue;
            if (o.get(fieldName).getAsString().toLowerCase(Locale.ROOT).equals(needle)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Index of the entry whose effective listing id matches {@code listingId} case-insensitively.
     * The effective id is the {@code "id"} field when present, else the {@code "itemId"} — so this
     * resolves both explicit-id entries and legacy entries (whose listingId defaults to their itemId).
     */
    // package-private for unit testing
    static int indexOfByListingId(JsonArray arr, String listingId) {
        if (listingId == null) return -1;
        String needle = listingId.toLowerCase(Locale.ROOT);
        for (int i = 0; i < arr.size(); i++) {
            JsonElement el = arr.get(i);
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String eff = o.has("id") ? o.get("id").getAsString()
                    : (o.has("itemId") ? o.get("itemId").getAsString() : null);
            if (eff != null && eff.toLowerCase(Locale.ROOT).equals(needle)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Generates a unique listing id from the registry itemId: the path after {@code ':'} sanitized to
     * {@code [a-z0-9_]}, suffixed {@code _1}, {@code _2}, … until it collides with no existing entry.
     */
    // package-private for unit testing
    static String nextId(JsonArray items, String itemId) {
        String base = itemId == null ? "item" : itemId;
        int colon = base.indexOf(':');
        if (colon >= 0 && colon + 1 < base.length()) {
            base = base.substring(colon + 1);
        }
        base = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        if (base.isBlank()) base = "item";
        int n = 1;
        String candidate = base + "_" + n;
        while (indexOfByListingId(items, candidate) >= 0) {
            n++;
            candidate = base + "_" + n;
        }
        return candidate;
    }

    /** Returns a copy of {@code spec} with its listingId forced to {@code listingId}. */
    private static AdminShopItemSpec withListingId(AdminShopItemSpec spec, String listingId) {
        return new AdminShopItemSpec(
                listingId, spec.itemId(), spec.displayName(), spec.buyPriceMinor(), spec.sellPriceMinor(),
                spec.stock(), spec.stockRefreshSeconds(), spec.categoryId(), spec.nbtJson(), spec.expiresAtEpoch());
    }

    /**
     * Read-only view of one admin.json listing for {@code /shopadmin items list|info}.
     * {@code stock == -1} means unlimited; {@code stockRefreshSeconds == 0} no refresh;
     * {@code expiresAtEpoch == 0} never expires.
     */
    public record AdminListingView(
            String listingId,
            String itemId,
            String displayName,
            long buyPriceMinor,
            long sellPriceMinor,
            int stock,
            int stockRefreshSeconds,
            String categoryId,
            long expiresAtEpoch,
            boolean nbtPresent) {
    }

    private static JsonArray ensureArray(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonArray()) {
            JsonArray arr = new JsonArray();
            root.add(key, arr);
            return arr;
        }
        return root.getAsJsonArray(key);
    }

    private static String normalizeId(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static JsonObject readOrInit(Path path) {
        try {
            if (!Files.exists(path)) {
                JsonObject root = new JsonObject();
                root.addProperty("shopId", "default");
                root.addProperty("displayName", "Server Shop");
                root.add("categories", new JsonArray());
                root.add("items", new JsonArray());
                root.add("promos", new JsonArray());
                root.add("barterRecipes", new JsonArray());
                return root;
            }
            if (!safeRegularFile(path)) {
                LOGGER.error(
                        "[FutureShops] Refused unsafe admin shop catalog at '{}'",
                        path);
                return null;
            }
            String content = readBounded(path);
            JsonElement parsed = JsonParser.parseString(content);
            if (!parsed.isJsonObject()) {
                LOGGER.error("[FutureShops] admin.json root is not an object");
                return null;
            }
            return parsed.getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[FutureShops] Failed to read admin.json at '{}': {}", path, e.getMessage());
            return null;
        }
    }

    static JsonObject readRoot(Path path) {
        return readOrInit(path);
    }

    static boolean writeValidatedRoot(Path path, JsonObject root) {
        return writeJson(path, root);
    }

    static boolean restoreLatestBackup(Path path) {
        Path latest = backup(path, 1);
        if (!safeRegularFile(latest)
                || Files.exists(path) && !safeRegularFile(path)) {
            LOGGER.error(
                    "[FutureShops] Cannot restore admin shop catalog from unsafe or missing backup at '{}'",
                    latest);
            return false;
        }
        Path temporary = path.resolveSibling(
                "." + path.getFileName() + "."
                        + java.util.UUID.randomUUID() + ".restore.tmp");
        try {
            String content = readBounded(latest);
            if (!ShopDefinitionLoader.validCandidate(
                    content, latest.getFileName().toString())) {
                LOGGER.error(
                        "[FutureShops] Refused invalid admin shop backup at '{}'",
                        latest);
                return false;
            }
            try (FileChannel channel = FileChannel.open(
                    temporary, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(content);
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            replace(temporary, path);
            return true;
        } catch (IOException exception) {
            LOGGER.error(
                    "[FutureShops] Failed to restore admin shop backup from '{}'",
                    latest, exception);
            return false;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException exception) {
                LOGGER.warn(
                        "[FutureShops] Failed to remove temporary admin shop restore file '{}'",
                        temporary, exception);
            }
        }
    }

    private static boolean writeJson(Path path, JsonObject root) {
        String content = PRETTY.toJson(root);
        if (content.getBytes(StandardCharsets.UTF_8).length
                > MAX_ADMIN_JSON_BYTES
                || Files.exists(path) && !safeRegularFile(path)) {
            LOGGER.error(
                    "[FutureShops] Refused oversized or unsafe admin shop candidate at '{}'",
                    path);
            return false;
        }
        if (!ShopDefinitionLoader.validCandidate(
                content, path.getFileName().toString())) {
            LOGGER.error(
                    "[FutureShops] Refused invalid admin shop candidate at '{}'",
                    path);
            return false;
        }
        Path temporary = path.resolveSibling(
                "." + path.getFileName() + "."
                        + java.util.UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (FileChannel channel = FileChannel.open(
                    temporary, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(content);
                while (bytes.hasRemaining()) {
                    channel.write(bytes);
                }
                channel.force(true);
            }
            String persisted = Files.readString(temporary);
            if (!ShopDefinitionLoader.validCandidate(
                    persisted, temporary.getFileName().toString())) {
                throw new IOException(
                        "Temporary admin shop validation failed");
            }
            rotateBackups(path);
            replace(temporary, path);
            return true;
        } catch (IOException e) {
            LOGGER.error("[FutureShops] Failed to write admin.json at '{}': {}", path, e.getMessage());
            return false;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException exception) {
                LOGGER.warn(
                        "[FutureShops] Failed to remove temporary admin shop file '{}'",
                        temporary, exception);
            }
        }
    }

    private static void rotateBackups(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (!safeRegularFile(path)) {
            throw new IOException("Admin shop catalog is unsafe");
        }
        for (int index = BACKUP_COUNT; index >= 2; index--) {
            Path previous = backup(path, index - 1);
            if (Files.exists(previous)) {
                if (!safeRegularFile(previous)) {
                    throw new IOException("Admin shop backup is unsafe");
                }
                Files.move(previous, backup(path, index),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path temporaryBackup = path.resolveSibling(
                "." + path.getFileName() + "."
                        + java.util.UUID.randomUUID() + ".backup.tmp");
        try {
            Files.copy(path, temporaryBackup);
            try (FileChannel channel = FileChannel.open(
                    temporaryBackup, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporaryBackup, backup(path, 1),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporaryBackup, backup(path, 1),
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporaryBackup);
        }
    }

    private static Path backup(Path path, int index) {
        return path.resolveSibling(path.getFileName() + ".bak." + index);
    }

    private static boolean safeRegularFile(Path path) {
        return Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path)
                && Files.isRegularFile(path,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
    }

    private static String readBounded(Path path) throws IOException {
        if (Files.size(path) > MAX_ADMIN_JSON_BYTES) {
            throw new IOException("Admin shop catalog is too large");
        }
        return Files.readString(path);
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
