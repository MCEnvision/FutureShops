package com.enviouse.futureshops.client;

public final class ShopClientState {
    private static volatile String activeShopId = "";
    private static volatile long currentBalanceMinorUnits = 0L;
    private static volatile String currencyName = "Coins";
    private static volatile int currencyDecimals = 2;

    private ShopClientState() {
    }

    public static void applyShopData(String shopId, long balanceMinorUnits, String currency, int decimals) {
        activeShopId = shopId;
        currentBalanceMinorUnits = balanceMinorUnits;
        currencyName = currency;
        currencyDecimals = decimals;
    }

    public static String getActiveShopId() {
        return activeShopId;
    }

    public static long getCurrentBalanceMinorUnits() {
        return currentBalanceMinorUnits;
    }

    public static String getCurrencyName() {
        return currencyName;
    }

    public static int getCurrencyDecimals() {
        return currencyDecimals;
    }
}

