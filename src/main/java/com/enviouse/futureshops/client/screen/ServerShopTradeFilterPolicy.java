package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
import com.enviouse.futureshops.data.CatalogItem;

import java.util.Objects;
import java.util.Optional;

public final class ServerShopTradeFilterPolicy {
    public enum Filter {
        ALL,
        BUY,
        SELL,
        BARTER,
        BUNDLES
    }

    private ServerShopTradeFilterPolicy() {
    }

    public static Filter defaultFilter() {
        return Filter.ALL;
    }

    public static Filter fromIndex(int index) {
        return switch (index) {
            case 1 -> Filter.BUY;
            case 2 -> Filter.SELL;
            case 3 -> Filter.BARTER;
            case 4 -> Filter.BUNDLES;
            default -> Filter.ALL;
        };
    }

    public static boolean matches(Filter filter, CatalogItem item) {
        return matches(filter, item, Optional.empty());
    }

    public static boolean matches(
            Filter filter,
            CatalogItem item,
            Optional<ServerShopOfferListing> offer
    ) {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(offer, "offer");
        if (offer.isPresent()) {
            ServerShopOfferListing listing = offer.orElseThrow();
            return switch (filter) {
                case ALL -> listing.active()
                        && (!listing.acquireOptions().isEmpty()
                        || !listing.sellOptions().isEmpty());
                case BUY -> !listing.acquireOptions().isEmpty();
                case SELL -> !listing.sellOptions().isEmpty();
                case BARTER -> listing.acquireOptions().stream()
                        .anyMatch(option -> !option.itemCosts().isEmpty());
                case BUNDLES -> listing.bundle();
            };
        }
        boolean buy = item.buyPrice() > 0L;
        boolean sell = item.sellPrice() > 0L;
        boolean barter = item.hasBarterRecipes();
        return switch (filter) {
            case ALL -> buy || sell || barter;
            case BUY -> buy;
            case SELL -> sell;
            case BARTER -> barter;
            case BUNDLES -> false;
        };
    }
}
