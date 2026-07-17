package com.enviouse.futureshops.client.screen;

import com.enviouse.futureshops.data.CatalogItem;

import java.util.Objects;

public final class ServerShopTradeFilterPolicy {
    public enum Filter {
        ALL,
        BUY,
        BARTER
    }

    private ServerShopTradeFilterPolicy() {
    }

    public static Filter defaultFilter() {
        return Filter.ALL;
    }

    public static Filter fromIndex(int index) {
        return switch (index) {
            case 1 -> Filter.BUY;
            case 2 -> Filter.BARTER;
            default -> Filter.ALL;
        };
    }

    public static boolean matches(Filter filter, CatalogItem item) {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(item, "item");
        boolean money = item.buyPrice() > 0L || item.sellPrice() > 0L;
        boolean barter = item.hasBarterRecipes();
        return switch (filter) {
            case ALL -> money || barter;
            case BUY -> money;
            case BARTER -> barter;
        };
    }
}
