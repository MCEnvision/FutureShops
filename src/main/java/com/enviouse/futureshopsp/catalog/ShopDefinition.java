package com.enviouse.futureshopsp.catalog;

import com.enviouse.futureshopsp.data.CatalogBarterRecipe;
import com.enviouse.futureshopsp.data.CatalogCategory;
import com.enviouse.futureshopsp.data.CatalogItem;
import com.enviouse.futureshopsp.data.CatalogPromo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Immutable server-side representation of a complete shop definition (one {@code .json} config file).
 * <p>
 * Call the {@code toCatalog*()} methods to produce the three network-sendable lists
 * that populate {@link com.enviouse.futureshopsp.network.packets.S2CShopDataPacket}.
 */
public record ShopDefinition(
        String shopId,
        String displayName,
        List<CategoryDef> categories,
        List<ItemDef> items,
        List<PromoDef> promos,
        List<BarterRecipeDef> barterRecipes) {

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
                    boolean hasBarterRecipes = barterRecipes.stream().anyMatch(recipe -> recipe.targetItemId().equals(item.itemId()));
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

    public List<CatalogBarterRecipe> toCatalogBarterRecipes() {
        return barterRecipes.stream().map(BarterRecipeDef::toCatalogRecipe).toList();
    }

    // -------------------------------------------------------------------------
    // Internal promo math
    // -------------------------------------------------------------------------

    private static long applyPromo(long basePriceMinorUnits, PromoDef promo) {
        return switch (promo.promoType()) {
            case "PERCENTAGE" ->
                    Math.max(0L, Math.round(basePriceMinorUnits * (1.0 - promo.discountValue() / 100.0)));
            case "FLAT" -> {
                long flatMinor = Math.round(promo.discountValue() * Math.pow(10, com.enviouse.futureshopsp.Config.economyCurrencyDecimals));
                yield Math.max(0L, basePriceMinorUnits - flatMinor);
            }
            default -> basePriceMinorUnits;
        };
    }
}

