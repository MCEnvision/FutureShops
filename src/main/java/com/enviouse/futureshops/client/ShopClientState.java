package com.enviouse.futureshops.client;

import com.enviouse.futureshops.data.CatalogBarterRecipe;
import com.enviouse.futureshops.data.CatalogCategory;
import com.enviouse.futureshops.data.CatalogItem;
import com.enviouse.futureshops.data.CatalogPromo;
import com.enviouse.futureshops.data.LocalShopOwnerEntry;
import com.enviouse.futureshops.data.NearbyShopEntry;
import com.enviouse.futureshops.data.TransactionHistoryEntry;
import com.enviouse.futureshops.network.packets.S2CVerifyCartResponsePacket;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Client-side singleton holding the most recently received shop state.
 * All fields are set exclusively by incoming S2C packets.
 */
public final class ShopClientState {

    private static volatile String activeShopId = "";
    private static volatile long currentBalanceMinorUnits = 0L;
    private static volatile String currencyName = "Coins";
    private static volatile int currencyDecimals = 2;

    // Catalog data — set by S2CShopDataPacket.
    private static volatile List<CatalogCategory> catalogCategories = List.of();
    private static volatile List<CatalogItem> catalogItems = List.of();
    private static volatile List<CatalogPromo> catalogPromos = List.of();
    private static volatile List<CatalogBarterRecipe> catalogBarterRecipes = List.of();
    private static volatile List<TransactionHistoryEntry> transactionHistory = List.of();
    private static volatile boolean adminShopEnabled = true;
    // Whether the server says this player may use the in-GUI admin editor (permission level 2).
    // Display gate only — AdminShopEditService re-validates every action server-side.
    private static volatile boolean canEditAdminShop = false;
    private static volatile List<NearbyShopEntry> nearbyShops = List.of();
    private static volatile List<LocalShopOwnerEntry> localShopOwners = List.of();
    // Precomputed department summary strings (avoids per-frame stream+reduce in ShopMainScreen).
    private static volatile Map<UUID, String> localShopDeptSummaries = Map.of();
    private static volatile List<S2CVerifyCartResponsePacket.CartWarning> cartWarnings = List.of();
    private static volatile boolean cartVerified = false;

    // Cart is keyed by listingId (the catalog resolution key), NOT registry itemId — two NBT
    // variants of one base item are distinct cart lines. ownedItemCounts stays keyed by registry
    // itemId (InventorySyncService reports registry-id counts).
    private static final Map<String, Integer> cart = new LinkedHashMap<>();
    private static final Map<String, Integer> ownedItemCounts = new HashMap<>();
    private static volatile ShopStatus status = null;

    private ShopClientState() {
    }

    // -------------------------------------------------------------------------
    // Writers
    // -------------------------------------------------------------------------

    public static void applyShopData(String shopId, long balanceMinorUnits, String currency, int decimals,
                                     List<CatalogCategory> categories, List<CatalogItem> items,
                                     List<CatalogPromo> promos, List<CatalogBarterRecipe> barterRecipes,
                                     boolean adminEnabled, List<NearbyShopEntry> nearby, boolean canEdit) {
        activeShopId = shopId;
        currentBalanceMinorUnits = balanceMinorUnits;
        currencyName = currency;
        currencyDecimals = decimals;
        catalogCategories = List.copyOf(categories);
        catalogItems = List.copyOf(items);
        catalogPromos = List.copyOf(promos);
        catalogBarterRecipes = List.copyOf(barterRecipes);
        adminShopEnabled = adminEnabled;
        canEditAdminShop = canEdit;
        nearbyShops = List.copyOf(nearby);
        transactionHistory = List.of();
        synchronized (ownedItemCounts) {
            ownedItemCounts.clear();
        }
        sanitizeCart();
    }

    /**
     * Clears all state.  Called by {@code S2CForceClosePacket} to ensure
     * the client does not display stale catalog data after a forced close.
     */
    public static void reset() {
        activeShopId = "";
        currentBalanceMinorUnits = 0L;
        catalogCategories = List.of();
        catalogItems = List.of();
        catalogPromos = List.of();
        catalogBarterRecipes = List.of();
        adminShopEnabled = true;
        canEditAdminShop = false;
        nearbyShops = List.of();
        transactionHistory = List.of();
        status = null;
        synchronized (cart) {
            cart.clear();
        }
        synchronized (ownedItemCounts) {
            ownedItemCounts.clear();
        }
    }

    public static void setCurrentBalanceMinorUnits(long balanceMinorUnits) {
        currentBalanceMinorUnits = balanceMinorUnits;
    }

    // Listing tag snapshots taken at ADD-to-cart time, keyed by listingId — verify-cart
    // sends these so the server can flag a variant swap. Snapshotting at add time matters:
    // /shopadmin items edit resends the catalog, so a verify-time lookup would always
    // "see" the new tag and the warning could never fire.
    private static final Map<String, String> cartNbtSnapshots = new HashMap<>();

    public static void addToCart(String listingId, int quantity) {
        if (quantity <= 0) {
            return;
        }

        int addedQuantity;
        String itemName;
        synchronized (cart) {
            int previousQuantity = cart.getOrDefault(listingId, 0);
            int newQuantity = clampCartQuantity(listingId, previousQuantity + quantity);
            cart.put(listingId, newQuantity);
            cartNbtSnapshots.putIfAbsent(listingId,
                    getCatalogItem(listingId).map(CatalogItem::nbtJson).orElse(""));
            sanitizeCartLocked();
            addedQuantity = Math.max(0, cart.getOrDefault(listingId, 0) - previousQuantity);
            itemName = getCatalogItem(listingId).map(CatalogItem::displayName).orElse(listingId);
        }
        if (addedQuantity > 0) {
            setStatus(Component.translatable("gui.futureshops.status.cart.added", addedQuantity, itemName), true);
        }
    }

    /** Listing tag as it looked when first added to the cart; "" when unknown/absent. */
    public static String getCartNbtSnapshot(String listingId) {
        synchronized (cart) {
            return cartNbtSnapshots.getOrDefault(listingId, "");
        }
    }

    public static void setCartQuantity(String listingId, int quantity) {
        synchronized (cart) {
            if (quantity <= 0) {
                cart.remove(listingId);
            } else {
                cart.put(listingId, clampCartQuantity(listingId, quantity));
            }
            sanitizeCartLocked();
        }
    }

    public static void removeFromCart(String listingId) {
        synchronized (cart) {
            cart.remove(listingId);
            cartNbtSnapshots.remove(listingId);
        }
    }

    public static void clearCart() {
        synchronized (cart) {
            cart.clear();
            cartNbtSnapshots.clear();
        }
    }

    // -------------------------------------------------------------------------
    // Readers
    // -------------------------------------------------------------------------

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

    public static List<CatalogCategory> getCatalogCategories() {
        return catalogCategories;
    }

    public static List<CatalogItem> getCatalogItems() {
        return catalogItems;
    }

    public static List<CatalogPromo> getCatalogPromos() {
        return catalogPromos;
    }

    public static List<CatalogBarterRecipe> getCatalogBarterRecipes() {
        return catalogBarterRecipes;
    }

    public static List<TransactionHistoryEntry> getTransactionHistory() {
        return transactionHistory;
    }

    public static boolean isAdminShopEnabled() {
        return adminShopEnabled;
    }

    /** Whether the server granted this player the in-GUI admin editor (see S2CShopDataPacket.canEdit). */
    public static boolean canEditAdminShop() {
        return canEditAdminShop;
    }

    public static List<NearbyShopEntry> getNearbyShops() {
        return nearbyShops;
    }

    /**
     * Recipes rewarding the given item. Matches the registry {@code targetItemId} (legacy surfaces
     * such as the barter screen are opened with a registry id) OR the resolved
     * {@code targetListingId}, so callers holding a specific listing key also find their recipes.
     * Per-recipe target resolution (icon/name NBT, cart key) should go through the recipe's own
     * {@code targetListingId} — see BarterScreen.
     */
    public static List<CatalogBarterRecipe> getBarterRecipesForItem(String itemId) {
        return catalogBarterRecipes.stream()
                .filter(recipe -> recipe.targetItemId().equals(itemId)
                        || (!recipe.targetListingId().isBlank() && recipe.targetListingId().equals(itemId)))
                .toList();
    }

    /** Resolves a catalog row by its listingId (the cart/buy/sell key), NOT its registry itemId. */
    public static Optional<CatalogItem> getCatalogItem(String listingId) {
        return catalogItems.stream().filter(item -> item.listingId().equals(listingId)).findFirst();
    }

    /**
     * Resolves a catalog row by registry itemId — first match wins, so this is
     * only well-defined for single-variant items (barter recipes target registry
     * ids, which is exactly that legacy case). Used to recover display NBT for
     * surfaces whose wire data carries only an itemId.
     */
    public static Optional<CatalogItem> getCatalogItemByRegistryId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        return catalogItems.stream().filter(item -> itemId.equals(item.itemId())).findFirst();
    }

    public static int getOwnedCount(String itemId) {
        synchronized (ownedItemCounts) {
            return ownedItemCounts.getOrDefault(itemId, 0);
        }
    }

    public static void applyOwnedCounts(Map<String, Integer> counts) {
        synchronized (ownedItemCounts) {
            ownedItemCounts.clear();
            ownedItemCounts.putAll(counts);
        }
    }

    public static void applyHistoryPage(List<TransactionHistoryEntry> entries) {
        transactionHistory = List.copyOf(entries);
    }

    public static List<CartEntry> getCartEntries() {
        synchronized (cart) {
            return cart.entrySet().stream()
                    .map(entry -> new CartEntry(entry.getKey(), entry.getValue()))
                    .toList();
        }
    }

    public static int getCartLineCount() {
        synchronized (cart) {
            return cart.size();
        }
    }

    public static int getCartTotalQuantity() {
        synchronized (cart) {
            return cart.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    public static long getCartTotalMinorUnits() {
        synchronized (cart) {
            long total = 0L;
            for (Map.Entry<String, Integer> entry : cart.entrySet()) {
                CatalogItem item = getCatalogItem(entry.getKey()).orElse(null);
                if (item == null) {
                    continue;
                }
                long unitPrice = item.hasPromo() ? item.promoPrice() : item.buyPrice();
                total += unitPrice * entry.getValue();
            }
            return total;
        }
    }

    public static void setStatus(Component message, boolean success) {
        status = new ShopStatus(message, success, System.currentTimeMillis() + 4500L);
    }

    public static ShopStatus getStatus() {
        ShopStatus current = status;
        if (current != null && current.expiresAtMillis() < System.currentTimeMillis()) {
            status = null;
            return null;
        }
        return current;
    }

    public static void clearStatus() {
        status = null;
    }

    private static void sanitizeCart() {
        synchronized (cart) {
            sanitizeCartLocked();
        }
    }

    private static void sanitizeCartLocked() {
        cart.entrySet().removeIf(entry -> {
            CatalogItem item = getCatalogItem(entry.getKey()).orElse(null);
            if (item == null || item.buyPrice() <= 0L) {
                return true;
            }

            int clamped = clampCartQuantity(item.listingId(), entry.getValue());
            if (clamped <= 0) {
                return true;
            }
            entry.setValue(clamped);
            return false;
        });
    }

    private static int clampCartQuantity(String listingId, int quantity) {
        CatalogItem item = getCatalogItem(listingId).orElse(null);
        if (item == null) {
            return 0;
        }

        int clamped = Math.max(1, Math.min(2304, quantity));
        if (!item.unlimited()) {
            clamped = Math.min(clamped, item.stock());
        }
        return clamped;
    }

    /** {@code listingId} is the catalog resolution key for this cart line (see {@link CatalogItem}). */
    public record CartEntry(String listingId, int quantity) {
    }

    public record ShopStatus(Component message, boolean success, long expiresAtMillis) {
    }

    // -------------------------------------------------------------------------
    // Local shops aggregation
    // -------------------------------------------------------------------------

    public static void applyLocalShops(List<LocalShopOwnerEntry> owners) {
        localShopOwners = List.copyOf(owners);
        // Rebuild the department-summary cache once per data update.
        Map<UUID, String> summaries = new HashMap<>(owners.size() * 2);
        for (LocalShopOwnerEntry owner : owners) {
            if (owner.departments().isEmpty()) {
                summaries.put(owner.ownerUuid(),
                        Component.translatable("gui.futureshops.shop_main.no_depts").getString());
                continue;
            }
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (LocalShopOwnerEntry.LocalDepartment dept : owner.departments()) {
                if (!first) sb.append(", ");
                sb.append(dept.name());
                first = false;
            }
            summaries.put(owner.ownerUuid(), sb.toString());
        }
        localShopDeptSummaries = Map.copyOf(summaries);
    }

    public static List<LocalShopOwnerEntry> getLocalShopOwners() {
        return localShopOwners;
    }

    /** Returns the cached comma-joined department-name summary for {@code ownerUuid}. */
    public static String getLocalShopDeptSummary(UUID ownerUuid) {
        String cached = localShopDeptSummaries.get(ownerUuid);
        return cached != null ? cached
                : Component.translatable("gui.futureshops.shop_main.no_depts").getString();
    }

    // -------------------------------------------------------------------------
    // Cart verification
    // -------------------------------------------------------------------------

    public static void applyCartVerification(boolean allOk, List<S2CVerifyCartResponsePacket.CartWarning> warnings) {
        cartVerified = true;
        cartWarnings = List.copyOf(warnings);
    }

    public static List<S2CVerifyCartResponsePacket.CartWarning> getCartWarnings() {
        return cartWarnings;
    }

    public static boolean isCartVerified() {
        return cartVerified;
    }

    public static void clearCartVerification() {
        cartVerified = false;
        cartWarnings = List.of();
    }
}
