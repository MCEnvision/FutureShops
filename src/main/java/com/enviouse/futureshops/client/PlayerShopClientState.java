package com.enviouse.futureshops.client;

import com.enviouse.futureshops.data.PlayerShopListingData;
import com.enviouse.futureshops.data.PlayerShopStorageEntry;
import com.enviouse.futureshops.data.SettlementHistoryRow;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.UUID;

public final class PlayerShopClientState {
    private static BlockPos shopPos = BlockPos.ZERO;
    private static boolean owner = false;
    private static UUID ownerUuid = new UUID(0L, 0L);
    private static String ownerName = "";
    private static String shopName = "";
    private static String description = "";
    private static String franchiseName = "";
    private static boolean singleItemMode = false;
    private static boolean barterStorageSame = true;
    private static boolean placedByCreative = false;
    private static boolean adminShopMode = false;
    private static List<PlayerShopListingData> listings = List.of();
    private static int selectedListingIndex = 0;
    private static boolean linked = false;
    private static long pendingSettlementMinor = 0L;
    private static long lifetimeRevenueMinor = 0L;
    private static List<String> recentRevenueRows = List.of();
    private static String floatingIconMode = "CYCLE";
    private static String floatingIconItem = "";
    private static List<PlayerShopStorageEntry> linkedStorages = List.of();
    private static List<String> savedConfigNames = List.of();
    private static List<SettlementHistoryRow> settlementHistoryRows = List.of();
    private static int settlementHistoryPage = 1;
    private static int settlementHistoryTotalPages = 1;
    private static String resultCode = "";

    // Pending-visit target: set by UI sites (e.g. LocalShopBrowserScreen) that want the
    // next incoming shop-data payload for a specific BlockPos to land on a specific
    // listing instead of whatever the last-selected index happened to be.  Cleared in
    // apply() once the matching payload arrives so it can't leak into an unrelated shop.
    private static BlockPos pendingVisitShopPos = null;
    private static int pendingVisitListingIndex = -1;

    private PlayerShopClientState() {
    }

    public static void apply(BlockPos pos, boolean ownerFlag, UUID ownerUuidValue, String ownerNameValue,
                             List<PlayerShopListingData> listingsValue, boolean linkedValue,
                             long pendingSettlementMinorValue, long lifetimeRevenueMinorValue,
                             List<String> recentRevenueRowsValue,
                             String shopNameValue, boolean singleItemModeValue, boolean barterStorageSameValue,
                             String descriptionValue, String franchiseNameValue,
                             boolean placedByCreativeValue, boolean adminShopModeValue,
                             String floatingIconModeValue, String floatingIconItemValue,
                             List<PlayerShopStorageEntry> linkedStoragesValue,
                             List<String> savedConfigNamesValue) {
        shopPos = pos;
        owner = ownerFlag;
        ownerUuid = ownerUuidValue;
        ownerName = ownerNameValue;
        shopName = shopNameValue == null ? "" : shopNameValue;
        description = descriptionValue == null ? "" : descriptionValue;
        franchiseName = franchiseNameValue == null ? "" : franchiseNameValue;
        singleItemMode = singleItemModeValue;
        barterStorageSame = barterStorageSameValue;
        placedByCreative = placedByCreativeValue;
        adminShopMode = adminShopModeValue;
        listings = List.copyOf(listingsValue);
        // Honor an outstanding "visit this specific listing" request when the incoming
        // payload is for the matching shop; otherwise fall back to clamping the previous
        // selection into range so stale indices don't crash access.
        if (pendingVisitShopPos != null && pendingVisitShopPos.equals(pos) && pendingVisitListingIndex >= 0) {
            selectedListingIndex = Math.max(0, Math.min(pendingVisitListingIndex, Math.max(0, listings.size() - 1)));
            pendingVisitShopPos = null;
            pendingVisitListingIndex = -1;
        } else {
            selectedListingIndex = Math.max(0, Math.min(selectedListingIndex, Math.max(0, listings.size() - 1)));
        }
        linked = linkedValue;
        pendingSettlementMinor = pendingSettlementMinorValue;
        lifetimeRevenueMinor = lifetimeRevenueMinorValue;
        recentRevenueRows = List.copyOf(recentRevenueRowsValue);
        floatingIconMode = floatingIconModeValue == null ? "CYCLE" : floatingIconModeValue;
        floatingIconItem = floatingIconItemValue == null ? "" : floatingIconItemValue;
        linkedStorages = List.copyOf(linkedStoragesValue);
        savedConfigNames = List.copyOf(savedConfigNamesValue);
    }

    public static BlockPos shopPos() { return shopPos; }
    public static boolean owner() { return owner; }
    public static UUID ownerUuid() { return ownerUuid; }
    public static String ownerName() { return ownerName; }
    public static String shopName() { return shopName; }
    public static String description() { return description; }
    public static String franchiseName() { return franchiseName; }
    public static boolean singleItemMode() { return singleItemMode; }
    public static boolean barterStorageSame() { return barterStorageSame; }
    public static boolean placedByCreative() { return placedByCreative; }
    public static boolean adminShopMode() { return adminShopMode; }
    public static List<PlayerShopListingData> listings() { return listings; }
    public static boolean linked() { return linked; }
    public static long pendingSettlementMinor() { return pendingSettlementMinor; }
    public static long lifetimeRevenueMinor() { return lifetimeRevenueMinor; }
    public static List<String> recentRevenueRows() { return recentRevenueRows; }
    public static String floatingIconMode() { return floatingIconMode; }
    public static String floatingIconItem() { return floatingIconItem; }
    public static List<PlayerShopStorageEntry> linkedStorages() { return linkedStorages; }
    public static List<String> savedConfigNames() { return savedConfigNames; }
    public static List<SettlementHistoryRow> settlementHistoryRows() { return settlementHistoryRows; }
    public static int settlementHistoryPage() { return settlementHistoryPage; }
    public static int settlementHistoryTotalPages() { return settlementHistoryTotalPages; }
    public static int selectedListingIndex() { return selectedListingIndex; }

    public static PlayerShopListingData selectedListing() {
        return selectedListingIndex >= 0 && selectedListingIndex < listings.size() ? listings.get(selectedListingIndex) : null;
    }

    public static void setSelectedListingIndex(int index) {
        selectedListingIndex = Math.max(0, Math.min(index, Math.max(0, listings.size() - 1)));
    }

    /**
     * Marks the next shop-data payload for {@code pos} to preselect {@code listingIndex}.
     * Used by listing-row clicks in browser screens so the visitor lands on the trade they
     * actually clicked instead of their previously-selected listing.
     */
    public static void requestVisit(BlockPos pos, int listingIndex) {
        pendingVisitShopPos = pos;
        pendingVisitListingIndex = Math.max(0, listingIndex);
    }

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
