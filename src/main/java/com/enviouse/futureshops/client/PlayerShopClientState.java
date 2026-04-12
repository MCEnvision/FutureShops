package com.enviouse.futureshops.client;

import com.enviouse.futureshops.data.SettlementHistoryRow;
import net.minecraft.core.BlockPos;

import java.util.List;

public final class PlayerShopClientState {
    private static BlockPos shopPos = BlockPos.ZERO;
    private static boolean owner = false;
    private static String ownerName = "";
    private static String listedItemId = "";
    private static String tradeMode = "MONEY";
    private static long moneyPriceMinor = 100L;
    private static String barterItemId = "";
    private static int barterItemCount = 1;
    private static int stock = 0;
    private static boolean linked = false;
    private static long pendingSettlementMinor = 0L;
    private static long lifetimeRevenueMinor = 0L;
    private static List<String> recentRevenueRows = List.of();
    private static List<SettlementHistoryRow> settlementHistoryRows = List.of();
    private static int settlementHistoryPage = 1;
    private static int settlementHistoryTotalPages = 1;
    private static String resultCode = "";

    private PlayerShopClientState() {
    }

    public static void apply(BlockPos pos, boolean ownerFlag, String ownerNameValue, String listedItemIdValue,
                             String tradeModeValue, long moneyPriceMinorValue, String barterItemIdValue,
                             int barterItemCountValue, int stockValue, boolean linkedValue,
                             long pendingSettlementMinorValue, long lifetimeRevenueMinorValue,
                             List<String> recentRevenueRowsValue) {
        shopPos = pos;
        owner = ownerFlag;
        ownerName = ownerNameValue;
        listedItemId = listedItemIdValue;
        tradeMode = tradeModeValue;
        moneyPriceMinor = moneyPriceMinorValue;
        barterItemId = barterItemIdValue;
        barterItemCount = barterItemCountValue;
        stock = stockValue;
        linked = linkedValue;
        pendingSettlementMinor = pendingSettlementMinorValue;
        lifetimeRevenueMinor = lifetimeRevenueMinorValue;
        recentRevenueRows = List.copyOf(recentRevenueRowsValue);
    }

    public static BlockPos shopPos() { return shopPos; }
    public static boolean owner() { return owner; }
    public static String ownerName() { return ownerName; }
    public static String listedItemId() { return listedItemId; }
    public static String tradeMode() { return tradeMode; }
    public static long moneyPriceMinor() { return moneyPriceMinor; }
    public static String barterItemId() { return barterItemId; }
    public static int barterItemCount() { return barterItemCount; }
    public static int stock() { return stock; }
    public static boolean linked() { return linked; }
    public static long pendingSettlementMinor() { return pendingSettlementMinor; }
    public static long lifetimeRevenueMinor() { return lifetimeRevenueMinor; }
    public static List<String> recentRevenueRows() { return recentRevenueRows; }
    public static List<SettlementHistoryRow> settlementHistoryRows() { return settlementHistoryRows; }
    public static int settlementHistoryPage() { return settlementHistoryPage; }
    public static int settlementHistoryTotalPages() { return settlementHistoryTotalPages; }

    public static void applySettlementHistory(int page, int totalPages, List<SettlementHistoryRow> rows) {
        settlementHistoryPage = Math.max(1, page);
        settlementHistoryTotalPages = Math.max(1, totalPages);
        settlementHistoryRows = List.copyOf(rows);
    }

    public static String resultCode() {
        return resultCode;
    }

    public static void setResultCode(String resultCodeValue) {
        resultCode = resultCodeValue;
    }
}

