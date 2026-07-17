package com.enviouse.futureshops.client.screen;

import java.util.Locale;

public final class PurchaseQuantityPolicy {
    public static final int MAX_SCREEN_QUANTITY = 2304;

    private PurchaseQuantityPolicy() {
    }

    public static int serverShopMaximum(boolean unlimited, int stock, int ownedForSale) {
        int buyMaximum = unlimited
                ? MAX_SCREEN_QUANTITY
                : Math.max(1, Math.min(MAX_SCREEN_QUANTITY, stock));
        int sellMaximum = Math.max(0, Math.min(MAX_SCREEN_QUANTITY, ownedForSale));
        return Math.max(1, Math.max(buyMaximum, sellMaximum));
    }

    public static int playerShopStockMaximum(boolean adminShop, int stock) {
        return adminShop
                ? MAX_SCREEN_QUANTITY
                : Math.max(1, Math.min(MAX_SCREEN_QUANTITY, stock));
    }

    public static int playerShopMaximum(String tradeMode, int stockMaximum,
                                        int barterAffordable) {
        int boundedStock = Math.max(1, Math.min(MAX_SCREEN_QUANTITY, stockMaximum));
        String mode = tradeMode == null ? "" : tradeMode.toUpperCase(Locale.ROOT);
        if ("BARTER".equals(mode) || "MONEY_AND_BARTER".equals(mode)) {
            return Math.max(1, Math.min(boundedStock, Math.max(0, barterAffordable)));
        }
        return boundedStock;
    }
}
