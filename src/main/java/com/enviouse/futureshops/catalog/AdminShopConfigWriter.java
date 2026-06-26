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
        ShopCatalog.reload(server);
        return listingId;
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
        ShopCatalog.reload(server);
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
        items.remove(idx);
        if (!writeJson(path, root)) return false;
        ShopCatalog.reload(server);
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
        ShopCatalog.reload(server);
        return true;
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
