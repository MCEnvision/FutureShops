package com.enviouse.futureshops.catalog.offer;

import com.enviouse.futureshops.catalog.BarterIngredientDef;
import com.enviouse.futureshops.catalog.BarterRecipeDef;
import com.enviouse.futureshops.catalog.ItemDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class LegacyServerShopOfferCompiler {
    private LegacyServerShopOfferCompiler() {
    }

    public static List<ServerShopOfferListing> compile(
            List<ItemDef> items,
            List<BarterRecipeDef> recipes
    ) {
        List<ServerShopOfferListing> offers = new ArrayList<>();
        for (ItemDef item : items) {
            List<BarterRecipeDef> matching = recipes.stream()
                    .filter(recipe -> recipe.targetItemId()
                            .equals(item.resolutionKey())
                            || recipe.targetItemId().equals(item.itemId()))
                    .toList();
            ServerShopOfferListing listing = compile(item, matching);
            if (!listing.acquireOptions().isEmpty()
                    || !listing.sellOptions().isEmpty()) {
                offers.add(listing);
            }
        }
        return List.copyOf(offers);
    }

    public static ServerShopOfferListing compile(
            ItemDef item,
            List<BarterRecipeDef> recipes
    ) {
        List<AcquireOfferOption> acquire = new ArrayList<>();
        if (item.buyPriceMinorUnits() > 0L) {
            acquire.add(new AcquireOfferOption("money", "Money", false,
                    true, item.buyPriceMinorUnits(), List.of(), 1,
                    OfferLimitPolicy.defaults(), OfferSchedule.always(), ""));
        }
        for (BarterRecipeDef recipe : recipes) {
            List<OfferItemComponent> costs = new ArrayList<>();
            for (int index = 0; index < recipe.ingredients().size();
                 index++) {
                BarterIngredientDef ingredient = recipe.ingredients()
                        .get(index);
                costs.add(new OfferItemComponent(
                        componentId(ingredient.itemId(), index),
                        ingredient.itemId(), ingredient.count(),
                        ingredient.nbtJson()));
            }
            acquire.add(new AcquireOfferOption(
                    normalizeId(recipe.recipeId()), "Barter", false,
                    false, 0L,
                    OfferComponentNormalizer.normalize(costs),
                    recipe.outputCount(), OfferLimitPolicy.defaults(),
                    OfferSchedule.always(), ""));
        }
        List<SellOfferOption> sell = new ArrayList<>();
        if (item.sellPriceMinorUnits() > 0L) {
            sell.add(new SellOfferOption("sell", "Sell to Shop",
                    List.of(new OfferItemComponent("input", item.itemId(),
                            1, item.nbtJson())),
                    item.sellPriceMinorUnits(), 0L,
                    OfferLimitPolicy.defaults(), OfferSchedule.always(), ""));
        }
        OfferStockPolicy stock = item.stock() < 0
                ? OfferStockPolicy.unlimited()
                : OfferStockPolicy.limited(item.stock(),
                item.stockRefreshSeconds());
        String name = item.displayName() == null
                || item.displayName().isBlank()
                ? item.itemId() : item.displayName();
        ServerShopOfferListing unversioned = new ServerShopOfferListing(
                normalizeId(item.resolutionKey()), 0L, name, "",
                normalizeId(item.categoryId()), item.itemId(), item.nbtJson(),
                true, item.expiresAtEpoch(), "",
                List.of(new OfferItemComponent("output", item.itemId(), 1,
                        item.nbtJson())),
                acquire, sell, stock, OfferLimitPolicy.defaults(),
                OfferSchedule.always(), List.of());
        return unversioned.withRevision(
                ServerShopOfferRevision.compute(unversioned));
    }

    private static String componentId(String itemId, int index) {
        String path = itemId.substring(itemId.indexOf(':') + 1)
                .replace('/', '_').replace('.', '_');
        return normalizeId(path + "_" + index);
    }

    private static String normalizeId(String value) {
        if (value == null || value.isBlank()) {
            return "all";
        }
        return value.strip().toLowerCase(Locale.ROOT)
                .replace(' ', '_');
    }
}
