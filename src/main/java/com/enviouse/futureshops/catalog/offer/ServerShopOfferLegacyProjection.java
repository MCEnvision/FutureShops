package com.enviouse.futureshops.catalog.offer;

import com.enviouse.futureshops.catalog.BarterIngredientDef;
import com.enviouse.futureshops.catalog.BarterRecipeDef;
import com.enviouse.futureshops.catalog.ItemDef;

import java.util.ArrayList;
import java.util.List;

public final class ServerShopOfferLegacyProjection {
    private ServerShopOfferLegacyProjection() {
    }

    public static List<ItemDef> items(
            List<ServerShopOfferListing> listings
    ) {
        List<ItemDef> items = new ArrayList<>();
        for (ServerShopOfferListing listing : listings) {
            OfferItemComponent representative = representative(listing);
            if (representative == null) {
                continue;
            }
            long buyPrice = listing.acquireOptions().stream()
                    .filter(AcquireOfferOption::moneyCostPresent)
                    .mapToLong(AcquireOfferOption::moneyCostMinorUnits)
                    .findFirst().orElse(0L);
            long sellPrice = listing.sellOptions().stream()
                    .mapToLong(SellOfferOption::moneyPayoutMinorUnits)
                    .findFirst().orElse(0L);
            int stock = listing.stockPolicy().type()
                    == OfferStockPolicy.Type.UNLIMITED ? -1
                    : Math.toIntExact(Math.min(Integer.MAX_VALUE,
                    listing.stockPolicy().quantity()));
            items.add(new ItemDef(listing.listingId(),
                    representative.itemId(),
                    listing.displayName(), buyPrice, sellPrice, stock,
                    listing.acquireOptions().stream()
                            .anyMatch(AcquireOfferOption::hasItemCosts),
                    listing.categoryId(),
                    Math.toIntExact(Math.min(Integer.MAX_VALUE,
                            listing.stockPolicy().refreshSeconds())),
                    representative.exactNbt(),
                    listing.expiresAtEpoch()));
        }
        return List.copyOf(items);
    }

    private static OfferItemComponent representative(
            ServerShopOfferListing listing
    ) {
        if (!listing.outputs().isEmpty()) {
            return listing.outputs().get(0);
        }
        return listing.sellOptions().stream()
                .flatMap(option -> option.itemInputs().stream())
                .findFirst().orElse(null);
    }

    public static List<BarterRecipeDef> barterRecipes(
            List<ServerShopOfferListing> listings
    ) {
        List<BarterRecipeDef> recipes = new ArrayList<>();
        for (ServerShopOfferListing listing : listings) {
            for (AcquireOfferOption option : listing.acquireOptions()) {
                if (!option.hasItemCosts()) {
                    continue;
                }
                List<BarterIngredientDef> ingredients = option.itemCosts()
                        .stream()
                        .map(component -> new BarterIngredientDef(
                                component.itemId(), component.count(),
                                component.exactNbt()))
                        .toList();
                recipes.add(new BarterRecipeDef(option.optionId(),
                        listing.listingId(), option.outputMultiplier(),
                        ingredients));
            }
        }
        return List.copyOf(recipes);
    }
}
