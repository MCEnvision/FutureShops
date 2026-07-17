package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.data.CatalogBarterRecipe;
import com.enviouse.futureshops.data.CatalogCategory;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.data.CatalogPromo;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of all loaded {@link ShopDefinition}s.
 *
 * <p>Populated once during {@code ServerStartingEvent} via {@link #reload(MinecraftServer)},
 * and optionally refreshed via {@code /shopadmin reload} (spec §29.2).
 *
 * <p>All reads are safe from any server thread once populated.
 */
public final class ShopCatalog {

    private static final ConcurrentHashMap<String, ShopDefinition> CATALOG = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, Integer>> STOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, PromoDef>> RUNTIME_PROMOS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, RuntimePromoConfig>> RUNTIME_PROMO_CONFIGS = new ConcurrentHashMap<>();
    // Indexes built once per reload. Immutable after reload() returns.
    private static volatile Map<String, Map<String, ItemDef>> CATALOG_BY_ITEM = Map.of();
    private static volatile Map<String, Set<String>> BARTER_TARGETS = Map.of();
    private static volatile Map<String, Map<String, List<BarterRecipeDef>>> BARTER_BY_TARGET = Map.of();
    private static volatile Map<String, Map<String, BarterRecipeDef>> BARTER_BY_ID = Map.of();
    // Per-shop locks for stock mutations (replaces class-level `synchronized`).
    private static final ConcurrentHashMap<String, java.util.concurrent.locks.ReentrantLock> STOCK_LOCKS = new ConcurrentHashMap<>();

    private ShopCatalog() {
    }

    /**
     * Items that are inert without their NBT (model, name and function all
     * resolve from the stack tag — TacZ resolves everything from GunId /
     * AmmoId / AttachmentId). A listing for one of these with no captured NBT
     * renders as a missing texture and mints a dead item — silent, so we
     * surface it at catalog load instead of leaving admins guessing.
     */
    private static final java.util.Set<String> TAG_DEPENDENT_ITEM_IDS = java.util.Set.of(
            "tacz:modern_kinetic_gun", "tacz:ammo", "tacz:attachment");

    /** Warn once per (shop, listing); re-warns only if the NBT disappears again later. */
    private static final java.util.Set<String> NBT_WARN_EMITTED = ConcurrentHashMap.newKeySet();

    private static void warnIfTagDependentWithoutNbt(String shopId, ItemDef item) {
        String itemId = item.itemId();
        if (itemId == null || !TAG_DEPENDENT_ITEM_IDS.contains(itemId)) {
            return;
        }
        String warnKey = shopId + "|" + item.resolutionKey();
        String nbt = item.nbtJson();
        if (nbt == null || nbt.isBlank()) {
            if (NBT_WARN_EMITTED.add(warnKey)) {
                org.slf4j.LoggerFactory.getLogger(ShopCatalog.class).warn(
                        "Shop '{}' listing '{}' ({}) has no captured NBT — it will render as a missing"
                                + " texture and mint a non-functional item. If this listing is in admin.json,"
                                + " re-capture it while holding the item: /shopadmin items edit <id> ... with"
                                + " the nbt flag on (or remove and re-add it); otherwise add an \"nbt\" SNBT"
                                + " field to the listing in its shop JSON file.",
                        shopId, item.resolutionKey(), itemId);
            }
        } else {
            NBT_WARN_EMITTED.remove(warnKey);
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Clears the current catalog and reloads all shop definitions from disk.
     * Must be called from the server thread (e.g., in {@code ServerStartingEvent}).
     *
     * <p>Live per-listing state survives the reload where it still makes sense: every in-GUI admin
     * edit (and every {@code /shopadmin items ...} write) funnels through here, so naively clearing
     * STOCKS would refill every limited listing to its configured value on each edit — silently
     * undoing all sales — and drop every runtime promo. Instead we snapshot live stock counts and
     * runtime promos before clearing and restore them after the rebuild for keys that still resolve.
     * Stock is only restored when the CONFIGURED stock is unchanged between old and new definitions;
     * if the admin deliberately edited a listing's stock, the freshly seeded configured value wins.
     */
    public static void reload(MinecraftServer server) {
        // Snapshot live state keyed by (shopId, resolutionKey) before wiping.
        Map<String, Map<String, Integer>> oldLiveStocks = new LinkedHashMap<>();
        STOCKS.forEach((shopId, byKey) -> oldLiveStocks.put(shopId, new java.util.HashMap<>(byKey)));
        Map<String, Map<String, ItemDef>> oldItemIndex = CATALOG_BY_ITEM;
        Map<String, Map<String, PromoDef>> oldRuntimePromos = new LinkedHashMap<>();
        RUNTIME_PROMOS.forEach((shopId, byKey) -> oldRuntimePromos.put(shopId, new java.util.HashMap<>(byKey)));
        Map<String, Map<String, RuntimePromoConfig>> oldRuntimePromoConfigs = new LinkedHashMap<>();
        RUNTIME_PROMO_CONFIGS.forEach((shopId, byKey) -> oldRuntimePromoConfigs.put(shopId, new java.util.HashMap<>(byKey)));

        CATALOG.clear();
        STOCKS.clear();
        RUNTIME_PROMOS.clear();
        RUNTIME_PROMO_CONFIGS.clear();

        Map<String, Map<String, ItemDef>> itemIndex = new LinkedHashMap<>();
        Map<String, Set<String>> barterIndex = new LinkedHashMap<>();
        Map<String, Map<String, List<BarterRecipeDef>>> barterByTargetIndex = new LinkedHashMap<>();
        Map<String, Map<String, BarterRecipeDef>> barterByIdIndex = new LinkedHashMap<>();

        for (ShopDefinition def : ShopDefinitionLoader.loadAll()) {
            CATALOG.put(def.shopId(), def);

            ConcurrentHashMap<String, Integer> stockMap = new ConcurrentHashMap<>();
            Map<String, ItemDef> byId = new LinkedHashMap<>(def.items().size() * 2);
            for (ItemDef item : def.items()) {
                // Index by the stable listing resolution key (== itemId for legacy single-variant
                // entries) so two listings that share a registry itemId but differ by NBT no longer
                // collapse onto the first. STOCKS is keyed the same way.
                byId.putIfAbsent(item.resolutionKey(), item);
                if (!item.isUnlimited()) {
                    stockMap.put(item.resolutionKey(), item.stock());
                }
                warnIfTagDependentWithoutNbt(def.shopId(), item);
            }
            STOCKS.put(def.shopId(), stockMap);
            itemIndex.put(def.shopId(), Map.copyOf(byId));

            Set<String> barterTargets = new HashSet<>();
            Map<String, List<BarterRecipeDef>> byTarget = new LinkedHashMap<>();
            Map<String, BarterRecipeDef> byRecipeId = new LinkedHashMap<>();
            for (BarterRecipeDef recipe : def.barterRecipes()) {
                // Recipe targets resolve listingId-first (falling back to the first listing whose
                // registry itemId matches), so index by the resolved listing's resolution key —
                // that is the key stock operations and buildItems' barter flag use. Unresolvable
                // targets keep the raw string (nothing matches, same as the pre-listingId days).
                String targetKey = resolveBarterTargetIn(def, recipe.targetItemId())
                        .map(ItemDef::resolutionKey)
                        .orElse(recipe.targetItemId());
                barterTargets.add(targetKey);
                byTarget.computeIfAbsent(targetKey, ignored -> new java.util.ArrayList<>()).add(recipe);
                byRecipeId.putIfAbsent(recipe.recipeId(), recipe);
            }
            barterIndex.put(def.shopId(), Set.copyOf(barterTargets));
            // Wrap each value list in an unmodifiable view for safe external exposure.
            Map<String, List<BarterRecipeDef>> byTargetImmutable = new LinkedHashMap<>(byTarget.size() * 2);
            byTarget.forEach((key, list) -> byTargetImmutable.put(key, List.copyOf(list)));
            barterByTargetIndex.put(def.shopId(), Map.copyOf(byTargetImmutable));
            barterByIdIndex.put(def.shopId(), Map.copyOf(byRecipeId));
        }

        CATALOG_BY_ITEM = Map.copyOf(itemIndex);
        BARTER_TARGETS = Map.copyOf(barterIndex);
        BARTER_BY_TARGET = Map.copyOf(barterByTargetIndex);
        BARTER_BY_ID = Map.copyOf(barterByIdIndex);

        // Restore live stock counts for listings that survived the reload with an UNCHANGED
        // configured stock. A changed configured value means the admin deliberately re-stocked
        // that listing, so the fresh seed stands.
        CATALOG_BY_ITEM.forEach((shopId, newById) -> {
            Map<String, ItemDef> oldById = oldItemIndex.get(shopId);
            Map<String, Integer> oldLive = oldLiveStocks.get(shopId);
            if (oldById == null || oldLive == null) {
                return;
            }
            ConcurrentHashMap<String, Integer> stockMap = STOCKS.get(shopId);
            if (stockMap == null) {
                return;
            }
            newById.forEach((key, newDef) -> {
                if (newDef.isUnlimited()) {
                    return;
                }
                ItemDef oldDef = oldById.get(key);
                if (oldDef == null || oldDef.stock() != newDef.stock()) {
                    return;
                }
                Integer live = oldLive.get(key);
                if (live != null) {
                    stockMap.put(key, live);
                }
            });
        });

        // Restore runtime promos (and their activation windows) whose listing still resolves.
        oldRuntimePromos.forEach((shopId, byKey) -> {
            Map<String, ItemDef> newById = CATALOG_BY_ITEM.get(shopId);
            if (newById == null) {
                return;
            }
            Map<String, RuntimePromoConfig> oldConfigs = oldRuntimePromoConfigs.get(shopId);
            byKey.forEach((key, promo) -> {
                if (!newById.containsKey(key)) {
                    return;
                }
                RUNTIME_PROMOS.computeIfAbsent(shopId, ignored -> new ConcurrentHashMap<>()).put(key, promo);
                RuntimePromoConfig config = oldConfigs == null ? null : oldConfigs.get(key);
                if (config != null) {
                    RUNTIME_PROMO_CONFIGS.computeIfAbsent(shopId, ignored -> new ConcurrentHashMap<>()).put(key, config);
                }
            });
        });

        // Fire ShopReloadEvent (spec §33)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new com.enviouse.futureshops.event.ShopReloadEvent(CATALOG.size()));
    }

    /**
     * Wipes all in-memory catalog state. Called on server stop so the static maps don't leak into
     * the next world in a single JVM (singleplayer world-switch): {@link #reload} now preserves live
     * stock across reloads, so without this the next world's start-up reload would inherit the prior
     * world's depleted counts for any listing whose configured stock happens to match.
     */
    public static void clear() {
        CATALOG.clear();
        STOCKS.clear();
        RUNTIME_PROMOS.clear();
        RUNTIME_PROMO_CONFIGS.clear();
        STOCK_LOCKS.clear();
        // Reset the once-per-listing NBT warning dedup too, so a genuinely-broken listing in the
        // next world (singleplayer) is warned about afresh rather than suppressed by a stale key.
        NBT_WARN_EMITTED.clear();
        CATALOG_BY_ITEM = Map.of();
        BARTER_TARGETS = Map.of();
        BARTER_BY_TARGET = Map.of();
        BARTER_BY_ID = Map.of();
    }

    // -------------------------------------------------------------------------
    // Lookups
    // -------------------------------------------------------------------------

    /**
     * Returns the shop definition for the given ID, or empty if not found.
     */
    public static Optional<ShopDefinition> get(String shopId) {
        return Optional.ofNullable(CATALOG.get(shopId));
    }

    /** Live view of all loaded shop definitions. Read-only — do not mutate. */
    public static Collection<ShopDefinition> getAllDefinitions() {
        return Collections.unmodifiableCollection(CATALOG.values());
    }

    /**
     * Looks up a bundled (JSON-defined) category across every loaded shop by id or displayName
     * (case-insensitive). Returns the matching {@link CategoryDef} from the first shop where it
     * occurs, or empty if no bundled category matches.
     */
    public static Optional<CategoryDef> findBundledCategory(String idOrName) {
        if (idOrName == null || idOrName.isBlank()) return Optional.empty();
        String trimmed = idOrName.trim();
        String normalizedId = trimmed.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
        for (ShopDefinition def : CATALOG.values()) {
            for (CategoryDef cat : def.categories()) {
                if (cat.id().equalsIgnoreCase(trimmed)
                        || cat.id().equalsIgnoreCase(normalizedId)
                        || cat.displayName().equalsIgnoreCase(trimmed)) {
                    return Optional.of(cat);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the shop definition for the given ID, falling back to "default",
     * or empty if neither exists.
     */
    public static Optional<ShopDefinition> getOrDefault(String shopId) {
        ShopDefinition def = CATALOG.get(shopId);
        if (def == null) {
            def = CATALOG.get("default");
        }
        return Optional.ofNullable(def);
    }

    /**
     * Resolves a single listing by its stable resolution key. NOTE: the {@code itemId} parameter is
     * the listing's {@link ItemDef#resolutionKey()} (== registry itemId for legacy single-variant
     * entries, a distinct stable id for multi-variant NBT listings) — NOT necessarily a registry id.
     * All stock/price/promo lookups below take the same key. To mint/render, read {@link ItemDef#itemId()}
     * off the returned def.
     */
    public static Optional<ItemDef> getItem(String shopId, String itemId) {
        String resolved = resolveShopId(shopId);
        Map<String, ItemDef> byId = CATALOG_BY_ITEM.get(resolved);
        if (byId == null) return Optional.empty();
        return Optional.ofNullable(byId.get(itemId));
    }

    /**
     * Resolves a listing by its registry {@code itemId} rather than its listingId — the first listing
     * whose registry id matches. Used by the barter path, whose recipes target registry ids. For a
     * legacy single-variant item this is the listing itself (listingId == itemId); for a fully
     * multi-variant item it returns the first-loaded variant (barter stamps the returned def's
     * nbtJson onto its reward, so the delivered variant matches what BarterScreen displays).
     * Always drive stock operations off the returned def's {@link ItemDef#resolutionKey()}.
     */
    public static Optional<ItemDef> getItemByRegistryId(String shopId, String registryItemId) {
        if (registryItemId == null) return Optional.empty();
        // Fast path: a legacy entry is indexed under its own itemId.
        Optional<ItemDef> direct = getItem(shopId, registryItemId);
        if (direct.isPresent() && registryItemId.equals(direct.get().itemId())) {
            return direct;
        }
        ShopDefinition def = getOrDefault(shopId).orElse(null);
        if (def == null) return Optional.empty();
        for (ItemDef it : def.items()) {
            if (registryItemId.equals(it.itemId())) return Optional.of(it);
        }
        return Optional.empty();
    }

    /**
     * Resolves a barter recipe's raw {@code target} string against the shop's listings —
     * listingId-FIRST (the listing whose {@link ItemDef#resolutionKey()} equals the target),
     * falling back to the first listing whose registry {@code itemId} matches (the legacy
     * pre-listingId behaviour; for legacy single-variant listings both coincide). The barter
     * service stamps the resolved listing's nbt on the minted reward and keys stock by its
     * resolution key.
     */
    public static Optional<ItemDef> resolveBarterTarget(String shopId, String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) return Optional.empty();
        Optional<ItemDef> byListing = getItem(shopId, rawTarget);
        if (byListing.isPresent()) return byListing;
        return getItemByRegistryId(shopId, rawTarget);
    }

    /**
     * Same listingId-first resolution as {@link #resolveBarterTarget(String, String)}, but against
     * one specific definition — usable during {@link #reload} before the indexes are published.
     * First match wins in both passes, mirroring the index {@code putIfAbsent} semantics.
     */
    // Package-private for unit tests (end-to-end barter-target resolution).
    static Optional<ItemDef> resolveBarterTargetIn(ShopDefinition def, String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) return Optional.empty();
        for (ItemDef it : def.items()) {
            if (rawTarget.equals(it.resolutionKey())) return Optional.of(it);
        }
        for (ItemDef it : def.items()) {
            if (rawTarget.equals(it.itemId())) return Optional.of(it);
        }
        return Optional.empty();
    }

    public static int getCurrentStock(String shopId, String itemId) {
        return getItem(shopId, itemId)
                .map(item -> {
                    if (item.isUnlimited()) {
                        return -1;
                    }
                    return STOCKS.getOrDefault(resolveShopId(shopId), new ConcurrentHashMap<>())
                            .getOrDefault(itemId, item.stock());
                })
                .orElse(-1);
    }

    public static boolean reserveStock(String shopId, String itemId, int quantity) {
        if (quantity <= 0) {
            return false;
        }

        Optional<ItemDef> itemOpt = getItem(shopId, itemId);
        if (itemOpt.isEmpty()) {
            return false;
        }

        ItemDef item = itemOpt.get();
        if (item.isUnlimited()) {
            return true;
        }

        String resolvedShopId = resolveShopId(shopId);
        java.util.concurrent.locks.ReentrantLock lock = stockLockFor(resolvedShopId);
        lock.lock();
        try {
            ConcurrentHashMap<String, Integer> stockMap = STOCKS.computeIfAbsent(resolvedShopId, ignored -> new ConcurrentHashMap<>());
            int available = stockMap.getOrDefault(itemId, item.stock());
            if (available < quantity) {
                return false;
            }
            stockMap.put(itemId, available - quantity);
            return true;
        } finally {
            lock.unlock();
        }
    }

    public static void restoreStock(String shopId, String itemId, int quantity) {
        if (quantity <= 0) {
            return;
        }

        getItem(shopId, itemId).ifPresent(item -> {
            if (item.isUnlimited()) {
                return;
            }

            String resolvedShopId = resolveShopId(shopId);
            java.util.concurrent.locks.ReentrantLock lock = stockLockFor(resolvedShopId);
            lock.lock();
            try {
                ConcurrentHashMap<String, Integer> stockMap = STOCKS.computeIfAbsent(resolvedShopId, ignored -> new ConcurrentHashMap<>());
                stockMap.merge(itemId, quantity, Integer::sum);
            } finally {
                lock.unlock();
            }
        });
    }

    public static boolean incrementStock(String shopId, String itemId, int quantity) {
        if (quantity <= 0) {
            return false;
        }

        Optional<ItemDef> itemOpt = getItem(shopId, itemId);
        if (itemOpt.isEmpty()) {
            return false;
        }

        ItemDef item = itemOpt.get();
        if (item.isUnlimited()) {
            return true;
        }

        String resolvedShopId = resolveShopId(shopId);
        java.util.concurrent.locks.ReentrantLock lock = stockLockFor(resolvedShopId);
        lock.lock();
        try {
            ConcurrentHashMap<String, Integer> stockMap = STOCKS.computeIfAbsent(resolvedShopId, ignored -> new ConcurrentHashMap<>());
            stockMap.merge(itemId, quantity, Integer::sum);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Sets the stock for an item to an exact value. Used by the stock refresh scheduler (spec §31).
     */
    public static void setStock(String shopId, String itemId, int newStock) {
        Optional<ItemDef> itemOpt = getItem(shopId, itemId);
        if (itemOpt.isEmpty()) return;
        ItemDef item = itemOpt.get();
        if (item.isUnlimited()) return;

        String resolvedShopId = resolveShopId(shopId);
        java.util.concurrent.locks.ReentrantLock lock = stockLockFor(resolvedShopId);
        lock.lock();
        try {
            ConcurrentHashMap<String, Integer> stockMap = STOCKS.computeIfAbsent(resolvedShopId, ignored -> new ConcurrentHashMap<>());
            stockMap.put(itemId, newStock);
        } finally {
            lock.unlock();
        }
    }

    private static java.util.concurrent.locks.ReentrantLock stockLockFor(String resolvedShopId) {
        return STOCK_LOCKS.computeIfAbsent(resolvedShopId, ignored -> new java.util.concurrent.locks.ReentrantLock());
    }

    public static List<CatalogCategory> buildCategories(String shopId) {
        return buildCategories(shopId, null);
    }

    /**
     * Builds the category list for the given shop.
     * If a MinecraftServer is provided, admin-defined categories from AdminCategorySavedData
     * are merged in (replacing the old TagDepartmentClassifier auto-generation).
     */
    public static List<CatalogCategory> buildCategories(String shopId, @javax.annotation.Nullable net.minecraft.server.MinecraftServer server) {
        Optional<ShopDefinition> defOpt = getOrDefault(shopId);
        if (defOpt.isEmpty()) return List.of();

        // Drop any legacy "all" category — the shop UI renders a virtual "All" tab at index 0,
        // so a persisted category with id "all" would show up as a duplicate empty tab.
        // Also drop any bundled categories the admin has tombstoned via /shopadmin category remove.
        java.util.Set<String> hiddenBaseIds = server != null
                ? com.enviouse.futureshops.server.shop.AdminCategorySavedData.get(server).getHiddenBaseCategoryIds()
                : java.util.Set.of();
        List<CatalogCategory> base = defOpt.get().toCatalogCategories().stream()
                .filter(c -> !"all".equalsIgnoreCase(c.id()))
                .filter(c -> !hiddenBaseIds.contains(c.id().toLowerCase(java.util.Locale.ROOT)))
                .toList();
        java.util.Set<String> existingIds = base.stream().map(CatalogCategory::id).collect(java.util.stream.Collectors.toSet());

        // Merge admin-defined categories (replaces TagDepartmentClassifier auto-generation)
        List<String> adminCategories = List.of();
        if (server != null) {
            adminCategories = com.enviouse.futureshops.server.shop.AdminCategorySavedData.get(server).getAllSorted();
        }

        if (adminCategories.isEmpty()) {
            return base;
        }

        List<CatalogCategory> merged = new java.util.ArrayList<>(base);
        int sortBase = base.stream().mapToInt(CatalogCategory::sortOrder).max().orElse(0) + 10;
        int idx = 0;
        for (String catName : adminCategories) {
            String catId = catName.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
            if (!existingIds.contains(catId)) {
                merged.add(new CatalogCategory(catId, catName, sortBase + idx));
                existingIds.add(catId);
                idx += 10;
            }
        }
        return merged;
    }

    public static List<CatalogPromo> buildPromos(String shopId) {
        Optional<ShopDefinition> defOpt = getOrDefault(shopId);
        if (defOpt.isEmpty()) {
            return List.of();
        }

        ShopDefinition def = defOpt.get();
        List<CatalogPromo> basePromos = def.toCatalogPromos();
        Map<String, PromoDef> runtime = RUNTIME_PROMOS.getOrDefault(def.shopId(), new ConcurrentHashMap<>());
        if (runtime.isEmpty()) {
            return basePromos;
        }

        List<CatalogPromo> merged = new java.util.ArrayList<>(basePromos);
        runtime.values().forEach(promo -> merged.add(promo.toCatalogPromo()));
        return merged;
    }

    /**
     * Builds the wire barter recipes for the given shop, resolving each recipe's raw target to a
     * listing (listingId-first, registry fallback) so the DTO ships the resolved
     * {@code targetListingId} plus the listing's registry {@code targetItemId} for icon fallback.
     * Unresolvable targets pass the raw string through with a blank {@code targetListingId}.
     */
    public static List<CatalogBarterRecipe> buildBarterRecipes(String shopId) {
        ShopDefinition def = getOrDefault(shopId).orElse(null);
        if (def == null) return List.of();
        return def.barterRecipes().stream()
                .map(recipe -> recipe.toCatalogRecipe(
                        resolveBarterTargetIn(def, recipe.targetItemId()).orElse(null)))
                .toList();
    }

    public static List<CatalogItem> buildItems(String shopId) {
        return buildItems(shopId, null);
    }

    /**
     * Builds catalog items for the given shop.
     * If a MinecraftServer is provided, admin-defined category assignments from AdminCategorySavedData
     * are used to classify items that don't have an explicit categoryId in JSON.
     */
    public static List<CatalogItem> buildItems(String shopId, @javax.annotation.Nullable net.minecraft.server.MinecraftServer server) {
        Optional<ShopDefinition> defOpt = getOrDefault(shopId);
        if (defOpt.isEmpty()) {
            return List.of();
        }

        ShopDefinition def = defOpt.get();
        String resolvedShopId = def.shopId();
        Map<String, PromoDef> promoByItem = def.promos().stream()
                .filter(promo -> !promo.isExpired())
                .filter(promo -> promo.targetItemId() != null && !promo.targetItemId().isBlank())
                .collect(java.util.stream.Collectors.toMap(PromoDef::targetItemId, promo -> promo, (left, right) -> left));
        ConcurrentHashMap<String, PromoDef> runtimePromos = RUNTIME_PROMOS.get(resolvedShopId);
        if (runtimePromos != null && !runtimePromos.isEmpty()) {
            ConcurrentHashMap<String, RuntimePromoConfig> runtimeConfigs = RUNTIME_PROMO_CONFIGS.get(resolvedShopId);
            long now = nowEpochSeconds();
            runtimePromos.forEach((itemId, promo) -> {
                RuntimePromoConfig config = runtimeConfigs == null ? null : runtimeConfigs.get(itemId);
                if (config == null || config.isActive(now)) {
                    promoByItem.put(itemId, promo);
                }
            });
        }

        // Load admin category assignments if server is available
        final Map<String, String> adminAssignments;
        if (server != null) {
            adminAssignments = com.enviouse.futureshops.server.shop.AdminCategorySavedData.get(server).getAllAssignments();
        } else {
            adminAssignments = Map.of();
        }

        // Look up stock map and barter targets once, outside the per-item loop.
        ConcurrentHashMap<String, Integer> stockMap = STOCKS.get(resolvedShopId);
        Set<String> barterTargets = BARTER_TARGETS.getOrDefault(resolvedShopId, Set.of());

        long now = nowEpochSeconds();
        return def.items().stream()
                // Hide listings whose availability window has elapsed (expiresAtEpoch > 0 && now >= it).
                .filter(item -> !item.isExpired(now))
                .map(item -> {
                    // A promo may target the listingId (a runtime sale on one specific variant) or the
                    // registry itemId (a static JSON promo). For legacy entries resolutionKey()==itemId
                    // so both coincide; the dual lookup keeps static JSON promos applying.
                    PromoDef promo = promoByItem.get(item.resolutionKey());
                    if (promo == null) promo = promoByItem.get(item.itemId());
                    boolean hasPromo = promo != null;
                    long promoPrice = hasPromo ? applyPromo(item.buyPriceMinorUnits(), promo) : 0L;
                    // Barter targets are resolved to listings at reload and indexed by their
                    // resolution key — flag exactly the listing the recipe rewards (legacy
                    // single-variant entries have resolutionKey == itemId, so nothing changes).
                    boolean hasBarterRecipes = barterTargets.contains(item.resolutionKey());
                    int stock = item.isUnlimited()
                            ? -1
                            : (stockMap == null ? item.stock() : stockMap.getOrDefault(item.resolutionKey(), item.stock()));
                    CatalogItem catalogItem = item.toCatalogItem(stock, hasPromo, promoPrice, hasBarterRecipes);

                    // Override category from admin assignments if the item has no explicit category.
                    // AdminCategorySavedData is keyed by registry itemId (not rekeyed), so look up by itemId.
                    if ("all".equals(catalogItem.categoryId())) {
                        String adminCat = adminAssignments.get(item.itemId());
                        if (adminCat != null && !adminCat.isBlank()) {
                            String catId = adminCat.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
                            return new CatalogItem(
                                    catalogItem.listingId(), catalogItem.itemId(), catalogItem.displayName(),
                                    catalogItem.buyPrice(), catalogItem.sellPrice(),
                                    catalogItem.stock(), catalogItem.unlimited(),
                                    catalogItem.barterEnabled(), catId,
                                    catalogItem.hasPromo(), catalogItem.promoPrice(),
                                    catalogItem.hasBarterRecipes(),
                                    catalogItem.nbtJson(),
                                    catalogItem.configuredStock());
                        }
                    }
                    return catalogItem;
                })
                .toList();
    }

    public static long getEffectiveBuyPrice(String shopId, String itemId) {
        ItemDef itemDef = getItem(shopId, itemId).orElse(null);
        if (itemDef == null) return 0L;
        long basePrice = itemDef.buyPriceMinorUnits();
        PromoDef promo = findEffectivePromo(shopId, itemId);
        return promo == null ? basePrice : applyPromo(basePrice, promo);
    }

    /**
     * Returns the active promo for {@code itemId} in {@code shopId}, or {@code null} if none applies.
     * Checks runtime overrides first (respecting their active window), then falls back to static
     * non-expired promos from the shop definition.
     */
    private static PromoDef findEffectivePromo(String shopId, String itemId) {
        String resolvedShopId = resolveShopId(shopId);
        ConcurrentHashMap<String, PromoDef> runtime = RUNTIME_PROMOS.get(resolvedShopId);
        if (runtime != null) {
            PromoDef runtimePromo = runtime.get(itemId);
            if (runtimePromo != null) {
                ConcurrentHashMap<String, RuntimePromoConfig> configs = RUNTIME_PROMO_CONFIGS.get(resolvedShopId);
                RuntimePromoConfig config = configs == null ? null : configs.get(itemId);
                if (config == null || config.isActive(nowEpochSeconds())) {
                    return runtimePromo;
                }
            }
        }
        ShopDefinition def = CATALOG.get(resolvedShopId);
        if (def != null) {
            for (PromoDef promo : def.promos()) {
                if (promo.isExpired()) continue;
                if (promo.targetItemId() != null && promo.targetItemId().equals(itemId)) {
                    return promo;
                }
            }
        }
        return null;
    }

    public static long calculateLineCost(String shopId, String itemId, int quantity) {
        return calculateLineCost(shopId, itemId, quantity, null);
    }

    /**
     * Calculates the line cost with optional dynamic pricing from a MinecraftServer context.
     */
    public static long calculateLineCost(String shopId, String itemId, int quantity,
                                         @javax.annotation.Nullable net.minecraft.server.MinecraftServer server) {
        if (quantity <= 0) {
            return 0L;
        }
        ItemDef itemDef = getItem(shopId, itemId).orElse(null);
        if (itemDef == null || itemDef.buyPriceMinorUnits() <= 0L) {
            return 0L;
        }

        String resolvedShopId = resolveShopId(shopId);

        // Apply dynamic pricing adjustment if enabled (spec §30)
        long basePrice = itemDef.buyPriceMinorUnits();
        if (server != null) {
            basePrice = com.enviouse.futureshops.server.pricing.DynamicPricingEngine
                    .getAdjustedPrice(server, resolvedShopId, itemId, basePrice);
        }

        RuntimePromoConfig config = RUNTIME_PROMO_CONFIGS.getOrDefault(resolvedShopId, new ConcurrentHashMap<>()).get(itemId);
        long now = nowEpochSeconds();
        if (config == null || !config.isActive(now)) {
            long unit = getEffectiveBuyPrice(shopId, itemId);
            // If dynamic pricing is active and there's no promo, use the adjusted base
            if (server != null && com.enviouse.futureshops.Config.dynamicPricingEnabled) {
                unit = basePrice;
                PromoDef staticPromo = findEffectivePromo(shopId, itemId);
                if (staticPromo != null) {
                    long promoPrice = applyPromo(itemDef.buyPriceMinorUnits(), staticPromo);
                    if (promoPrice > 0) unit = promoPrice;
                }
            }
            return unit <= 0L ? 0L : unit * quantity;
        }

        String type = config.promoType();
        if ("BUY_X_GET_Y".equals(type) && config.buyX() > 0 && config.buyY() > 0) {
            int group = config.buyX() + config.buyY();
            int fullGroups = quantity / group;
            int remainder = quantity % group;
            int payable = fullGroups * config.buyX() + Math.min(remainder, config.buyX());
            return basePrice * payable;
        }

        long discounted = switch (type) {
            case "PERCENTAGE", "FLASH" -> Math.max(0L, Math.round(basePrice * (1.0 - config.discountValue() / 100.0)));
            case "FLAT" -> {
                long flatMinor = Math.round(config.discountValue() * Math.pow(10, com.enviouse.futureshops.Config.economyCurrencyDecimals));
                yield Math.max(0L, basePrice - flatMinor);
            }
            default -> basePrice;
        };
        return discounted * quantity;
    }

    /**
     * Returns the recipes whose resolved reward listing has the given resolution key (for legacy
     * single-variant listings that key equals the registry itemId). Recipes whose target never
     * resolved to a listing are indexed under their raw target string.
     */
    public static List<BarterRecipeDef> getBarterRecipesForItem(String shopId, String itemId) {
        Map<String, List<BarterRecipeDef>> byTarget = BARTER_BY_TARGET.get(resolveShopId(shopId));
        if (byTarget == null) return List.of();
        List<BarterRecipeDef> recipes = byTarget.get(itemId);
        return recipes == null ? List.of() : recipes;
    }

    public static Optional<BarterRecipeDef> getBarterRecipe(String shopId, String recipeId) {
        Map<String, BarterRecipeDef> byId = BARTER_BY_ID.get(resolveShopId(shopId));
        if (byId == null) return Optional.empty();
        return Optional.ofNullable(byId.get(recipeId));
    }

    /** Returns an unmodifiable view of all loaded shop definitions. */
    public static Collection<ShopDefinition> all() {
        return Collections.unmodifiableCollection(CATALOG.values());
    }

    public static Map<String, ShopDefinition> snapshot() {
        return Collections.unmodifiableMap(new java.util.HashMap<>(CATALOG));
    }

    /** Returns {@code true} if no shops have been loaded yet. */
    public static boolean isEmpty() {
        return CATALOG.isEmpty();
    }

    public static synchronized boolean setRuntimePromo(String shopId, String itemId, String promoType, double discountValue) {
        return setRuntimePromo(shopId, itemId, promoType, discountValue, 0, 0, 0, 0, false);
    }

    public static synchronized boolean setRuntimePromo(String shopId, String itemId, String promoType, double discountValue,
                                                       int buyX, int buyY, int startsInMinutes, int durationMinutes, boolean flash) {
        Optional<ShopDefinition> defOpt = getOrDefault(shopId);
        if (defOpt.isEmpty() || getItem(shopId, itemId).isEmpty()) {
            return false;
        }

        String normalizedType = promoType == null ? "" : promoType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"PERCENTAGE".equals(normalizedType)
                && !"FLAT".equals(normalizedType)
                && !"BUY_X_GET_Y".equals(normalizedType)
                && !"FLASH".equals(normalizedType)) {
            return false;
        }
        if (("PERCENTAGE".equals(normalizedType) || "FLAT".equals(normalizedType) || "FLASH".equals(normalizedType))
                && discountValue <= 0.0D) {
            return false;
        }
        if ("BUY_X_GET_Y".equals(normalizedType) && (buyX <= 0 || buyY <= 0)) {
            return false;
        }

        long now = nowEpochSeconds();
        long startEpoch = startsInMinutes <= 0 ? now : now + (long) startsInMinutes * 60L;
        long endEpoch = durationMinutes <= 0 ? Long.MAX_VALUE : startEpoch + (long) durationMinutes * 60L;
        double storedDiscount = "BUY_X_GET_Y".equals(normalizedType) ? 0.0D : discountValue;
        String storedType = flash ? "FLASH" : normalizedType;

        ShopDefinition def = defOpt.get();
        PromoDef promo = new PromoDef(
                "runtime_" + itemId,
                storedType,
                itemId,
                storedDiscount,
                endEpoch
        );

        String resolvedShopId = def.shopId();
        RUNTIME_PROMOS.computeIfAbsent(resolvedShopId, ignored -> new ConcurrentHashMap<>()).put(itemId, promo);
        RUNTIME_PROMO_CONFIGS.computeIfAbsent(resolvedShopId, ignored -> new ConcurrentHashMap<>())
                .put(itemId, new RuntimePromoConfig(storedType, storedDiscount, buyX, buyY, startEpoch, endEpoch));
        return true;
    }

    public static synchronized boolean clearRuntimePromo(String shopId, String itemId) {
        Optional<ShopDefinition> defOpt = getOrDefault(shopId);
        if (defOpt.isEmpty()) {
            return false;
        }

        ConcurrentHashMap<String, PromoDef> overrides = RUNTIME_PROMOS.get(defOpt.get().shopId());
        if (overrides == null) {
            return false;
        }
        ConcurrentHashMap<String, RuntimePromoConfig> configs = RUNTIME_PROMO_CONFIGS.get(defOpt.get().shopId());
        if (configs != null) {
            configs.remove(itemId);
        }
        return overrides.remove(itemId) != null;
    }

    private static String resolveShopId(String requestedShopId) {
        return getOrDefault(requestedShopId).map(ShopDefinition::shopId).orElse("default");
    }

    private static long applyPromo(long basePriceMinorUnits, PromoDef promo) {
        return switch (promo.promoType()) {
            case "PERCENTAGE", "FLASH" ->
                    Math.max(0L, Math.round(basePriceMinorUnits * (1.0 - promo.discountValue() / 100.0)));
            case "FLAT" -> {
                long flatMinor = Math.round(promo.discountValue() * Math.pow(10, com.enviouse.futureshops.Config.economyCurrencyDecimals));
                yield Math.max(0L, basePriceMinorUnits - flatMinor);
            }
            default -> basePriceMinorUnits;
        };
    }

    private static long nowEpochSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    private record RuntimePromoConfig(
            String promoType,
            double discountValue,
            int buyX,
            int buyY,
            long startEpoch,
            long endEpoch) {
        private boolean isActive(long nowEpochSeconds) {
            return nowEpochSeconds >= startEpoch && nowEpochSeconds <= endEpoch;
        }
    }
}

