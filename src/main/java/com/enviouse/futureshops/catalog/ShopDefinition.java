package com.enviouse.futureshops.catalog;

import com.enviouse.futureshops.catalog.offer.LegacyServerShopOfferCompiler;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.data.CatalogCategory;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.data.CatalogPromo;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable server-side representation of a complete shop definition (one {@code .json} config file).
 * <p>
 * Call the {@code toCatalog*()} methods to produce the three network-sendable lists
 * that populate {@link com.enviouse.futureshops.network.packets.S2CShopDataPacket}.
 */
public record ShopDefinition(
        int schemaVersion,
        String shopId,
        String displayName,
        List<CategoryDef> categories,
        List<ItemDef> items,
        List<PromoDef> promos,
        List<BarterRecipeDef> barterRecipes,
        List<ServerShopOfferListing> offers) {

    public ShopDefinition {
        if (schemaVersion < 1 || schemaVersion > 2) {
            throw new IllegalArgumentException(
                    "Unsupported server shop schema version");
        }
        shopId = Objects.requireNonNull(shopId, "shopId");
        displayName = Objects.requireNonNull(displayName, "displayName");
        categories = List.copyOf(Objects.requireNonNull(categories,
                "categories"));
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        promos = List.copyOf(Objects.requireNonNull(promos, "promos"));
        barterRecipes = List.copyOf(Objects.requireNonNull(barterRecipes,
                "barterRecipes"));
        offers = List.copyOf(Objects.requireNonNull(offers, "offers"));
    }

    public ShopDefinition(
            String shopId,
            String displayName,
            List<CategoryDef> categories,
            List<ItemDef> items,
            List<PromoDef> promos,
            List<BarterRecipeDef> barterRecipes
    ) {
        this(1, shopId, displayName, categories, items, promos,
                barterRecipes, LegacyServerShopOfferCompiler.compile(
                        items, barterRecipes));
    }

    // -------------------------------------------------------------------------
    // Conversion to packet DTOs
    // -------------------------------------------------------------------------

    public List<CatalogCategory> toCatalogCategories() {
        return categories.stream()
                .sorted(java.util.Comparator.comparingInt(CategoryDef::sortOrder))
                .map(c -> new CatalogCategory(c.id(), c.displayName(), c.sortOrder()))
                .collect(Collectors.toList());
    }

    public List<CatalogItem> toCatalogItems() {
        // Build item-id → PromoDef map (first matching, non-expired promo per item)
        Map<String, PromoDef> promoByItem = promos.stream()
                .filter(p -> !p.isExpired())
                .filter(p -> p.targetItemId() != null && !p.targetItemId().isBlank())
                .collect(Collectors.toMap(
                        PromoDef::targetItemId,
                        p -> p,
                        (a, b) -> a)); // keep first promo per item

        return items.stream()
                .map(item -> {
                    PromoDef promo = promoByItem.get(item.itemId());
                    boolean hasPromo = promo != null;
                    long promoPrice = hasPromo ? applyPromo(item.buyPriceMinorUnits(), promo) : 0L;
                    // Match the listing's resolutionKey (id when present, else itemId) — a recipe
                    // targeting a generated listing id ("diamond_1") would never equal the bare
                    // itemId. Kept consistent with the LIVE path (ShopCatalog.buildItems) so this
                    // helper can't silently mis-flag barter if ever wired into the send path.
                    boolean hasBarterRecipes = barterRecipes.stream()
                            .anyMatch(recipe -> recipe.targetItemId().equals(item.resolutionKey())
                                    || recipe.targetItemId().equals(item.itemId()));
                    return item.toCatalogItem(item.stock(), hasPromo, promoPrice, hasBarterRecipes);
                })
                .collect(Collectors.toList());
    }

    public List<CatalogPromo> toCatalogPromos() {
        return promos.stream()
                .filter(p -> !p.isExpired())
                .map(PromoDef::toCatalogPromo)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Internal promo math
    // -------------------------------------------------------------------------

    private static long applyPromo(long basePriceMinorUnits, PromoDef promo) {
        return switch (promo.promoType()) {
            case "PERCENTAGE" ->
                    Math.max(0L, Math.round(basePriceMinorUnits * (1.0 - promo.discountValue() / 100.0)));
            case "FLAT" -> {
                long flatMinor = Math.round(promo.discountValue() * Math.pow(10, com.enviouse.futureshops.Config.economyCurrencyDecimals));
                yield Math.max(0L, basePriceMinorUnits - flatMinor);
            }
            default -> basePriceMinorUnits;
        };
    }
}
