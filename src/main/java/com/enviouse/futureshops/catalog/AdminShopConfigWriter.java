package com.enviouse.futureshops.catalog;

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
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

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
        ShopCatalog.reload(server);
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
        ShopCatalog.reload(server);
        return true;
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
        ShopCatalog.reload(server);
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
        ShopCatalog.reload(server);
        return true;
    }

    /**
     * Returns the union of category display names known to {@code admin.json} plus runtime
     * admin categories from {@link com.enviouse.futureshops.server.shop.AdminCategorySavedData}.
     * Excludes the synthetic "All" tab.
     */
    public static List<String> listAllCategoryDisplayNames(MinecraftServer server) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (ShopDefinition def : ShopCatalog.getAllDefinitions()) {
            for (CategoryDef c : def.categories()) {
                if ("all".equalsIgnoreCase(c.id())) continue;
                names.add(c.displayName());
            }
        }
        names.addAll(com.enviouse.futureshops.server.shop.AdminCategorySavedData.get(server).getAllSorted());
        return new ArrayList<>(names);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static JsonObject buildItemEntry(AdminShopItemSpec spec) {
        JsonObject o = new JsonObject();
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
            String content = Files.readString(path);
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

    private static boolean writeJson(Path path, JsonObject root) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, PRETTY.toJson(root));
            return true;
        } catch (IOException e) {
            LOGGER.error("[FutureShops] Failed to write admin.json at '{}': {}", path, e.getMessage());
            return false;
        }
    }
}
