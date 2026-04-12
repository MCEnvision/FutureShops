package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.data.CatalogBarterRecipe;
import com.enviouse.futureshops.data.CatalogCategory;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.data.CatalogPromo;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private ShopCatalog() {
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Clears the current catalog and reloads all shop definitions from disk.
     * Must be called from the server thread (e.g., in {@code ServerStartingEvent}).
     */
    public static void reload(MinecraftServer server) {
        CATALOG.clear();
        STOCKS.clear();
        RUNTIME_PROMOS.clear();
        RUNTIME_PROMO_CONFIGS.clear();
        for (ShopDefinition def : ShopDefinitionLoader.loadAll()) {
            CATALOG.put(def.shopId(), def);

            ConcurrentHashMap<String, Integer> stockMap = new ConcurrentHashMap<>();
            for (ItemDef item : def.items()) {
                if (!item.isUnlimited()) {
                    stockMap.put(item.itemId(), item.stock());
                }
            }
            STOCKS.put(def.shopId(), stockMap);
        }
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

    public static Optional<ItemDef> getItem(String shopId, String itemId) {
        return getOrDefault(shopId)
                .flatMap(def -> def.items().stream().filter(item -> item.itemId().equals(itemId)).findFirst());
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

    public static synchronized boolean reserveStock(String shopId, String itemId, int quantity) {
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
        ConcurrentHashMap<String, Integer> stockMap = STOCKS.computeIfAbsent(resolvedShopId, ignored -> new ConcurrentHashMap<>());
        int available = stockMap.getOrDefault(itemId, item.stock());
        if (available < quantity) {
            return false;
        }

        stockMap.put(itemId, available - quantity);
        return true;
    }

    public static synchronized void restoreStock(String shopId, String itemId, int quantity) {
        if (quantity <= 0) {
            return;
        }

        getItem(shopId, itemId).ifPresent(item -> {
            if (item.isUnlimited()) {
                return;
            }

            String resolvedShopId = resolveShopId(shopId);
            ConcurrentHashMap<String, Integer> stockMap = STOCKS.computeIfAbsent(resolvedShopId, ignored -> new ConcurrentHashMap<>());
            stockMap.merge(itemId, quantity, Integer::sum);
        });
    }

    public static synchronized boolean incrementStock(String shopId, String itemId, int quantity) {
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
        ConcurrentHashMap<String, Integer> stockMap = STOCKS.computeIfAbsent(resolvedShopId, ignored -> new ConcurrentHashMap<>());
        stockMap.merge(itemId, quantity, Integer::sum);
        return true;
    }

    public static List<CatalogCategory> buildCategories(String shopId) {
        return getOrDefault(shopId).map(ShopDefinition::toCatalogCategories).orElse(List.of());
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

    public static List<CatalogBarterRecipe> buildBarterRecipes(String shopId) {
        return getOrDefault(shopId).map(ShopDefinition::toCatalogBarterRecipes).orElse(List.of());
    }

    public static List<CatalogItem> buildItems(String shopId) {
        Optional<ShopDefinition> defOpt = getOrDefault(shopId);
        if (defOpt.isEmpty()) {
            return List.of();
        }

        ShopDefinition def = defOpt.get();
        Map<String, PromoDef> promoByItem = def.promos().stream()
                .filter(promo -> !promo.isExpired())
                .filter(promo -> promo.targetItemId() != null && !promo.targetItemId().isBlank())
                .collect(java.util.stream.Collectors.toMap(PromoDef::targetItemId, promo -> promo, (left, right) -> left));
        RUNTIME_PROMOS.getOrDefault(def.shopId(), new ConcurrentHashMap<>()).forEach((itemId, promo) -> {
            RuntimePromoConfig config = RUNTIME_PROMO_CONFIGS.getOrDefault(def.shopId(), new ConcurrentHashMap<>()).get(itemId);
            if (config == null || config.isActive(nowEpochSeconds())) {
                promoByItem.put(itemId, promo);
            }
        });

        return def.items().stream()
                .map(item -> {
                    PromoDef promo = promoByItem.get(item.itemId());
                    boolean hasPromo = promo != null;
                    long promoPrice = hasPromo ? applyPromo(item.buyPriceMinorUnits(), promo) : 0L;
                    boolean hasBarterRecipes = def.barterRecipes().stream().anyMatch(recipe -> recipe.targetItemId().equals(item.itemId()));
                    return item.toCatalogItem(getCurrentStock(def.shopId(), item.itemId()), hasPromo, promoPrice, hasBarterRecipes);
                })
                .toList();
    }

    public static long getEffectiveBuyPrice(String shopId, String itemId) {
        return buildItems(shopId).stream()
                .filter(item -> item.itemId().equals(itemId))
                .findFirst()
                .map(item -> item.hasPromo() ? item.promoPrice() : item.buyPrice())
                .orElse(0L);
    }

    public static long calculateLineCost(String shopId, String itemId, int quantity) {
        if (quantity <= 0) {
            return 0L;
        }
        ItemDef itemDef = getItem(shopId, itemId).orElse(null);
        if (itemDef == null || itemDef.buyPriceMinorUnits() <= 0L) {
            return 0L;
        }

        String resolvedShopId = resolveShopId(shopId);
        RuntimePromoConfig config = RUNTIME_PROMO_CONFIGS.getOrDefault(resolvedShopId, new ConcurrentHashMap<>()).get(itemId);
        long now = nowEpochSeconds();
        if (config == null || !config.isActive(now)) {
            long unit = getEffectiveBuyPrice(shopId, itemId);
            return unit <= 0L ? 0L : unit * quantity;
        }

        String type = config.promoType();
        if ("BUY_X_GET_Y".equals(type) && config.buyX() > 0 && config.buyY() > 0) {
            int group = config.buyX() + config.buyY();
            int fullGroups = quantity / group;
            int remainder = quantity % group;
            int payable = fullGroups * config.buyX() + Math.min(remainder, config.buyX());
            return itemDef.buyPriceMinorUnits() * payable;
        }

        long baseUnit = itemDef.buyPriceMinorUnits();
        long discounted = switch (type) {
            case "PERCENTAGE", "FLASH" -> Math.max(1L, Math.round(baseUnit * (1.0 - config.discountValue() / 100.0)));
            case "FLAT" -> Math.max(1L, baseUnit - (long) config.discountValue());
            default -> baseUnit;
        };
        return discounted * quantity;
    }

    public static List<BarterRecipeDef> getBarterRecipesForItem(String shopId, String itemId) {
        return getOrDefault(shopId)
                .map(def -> def.barterRecipes().stream().filter(recipe -> recipe.targetItemId().equals(itemId)).toList())
                .orElse(List.of());
    }

    public static Optional<BarterRecipeDef> getBarterRecipe(String shopId, String recipeId) {
        return getOrDefault(shopId)
                .flatMap(def -> def.barterRecipes().stream().filter(recipe -> recipe.recipeId().equals(recipeId)).findFirst());
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
                    Math.max(1L, Math.round(basePriceMinorUnits * (1.0 - promo.discountValue() / 100.0)));
            case "FLAT" ->
                    Math.max(1L, basePriceMinorUnits - (long) promo.discountValue());
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

