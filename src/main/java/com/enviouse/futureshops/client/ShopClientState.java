package com.enviouse.futureshops.client;

import com.enviouse.futureshops.catalog.offer.AcquireOfferOption;
import com.enviouse.futureshops.catalog.offer.ServerShopOfferListing;
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
    public static final long CART_CHECKOUT_TIMEOUT_MILLIS = 15_000L;

    private static volatile String activeShopId = "";
    private static volatile long currentBalanceMinorUnits = 0L;
    private static volatile boolean currentBalanceKnown;
    private static volatile String currencyName = "Coins";
    private static volatile int currencyDecimals = 2;

    // Catalog data — set by S2CShopDataPacket.
    private static volatile List<CatalogCategory> catalogCategories = List.of();
    private static volatile List<CatalogItem> catalogItems = List.of();
    private static volatile List<CatalogPromo> catalogPromos = List.of();
    private static volatile List<CatalogBarterRecipe> catalogBarterRecipes = List.of();
    private static volatile List<ServerShopOfferListing> catalogOffers =
            List.of();
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
    private static final Map<OfferCartKey, Integer> offerCart =
            new LinkedHashMap<>();
    private static final CartResponsePolicy cartResponsePolicy = new CartResponsePolicy();
    private static CartCheckoutSubmission trackedCartCheckout;
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
                                     boolean adminEnabled, List<NearbyShopEntry> nearby, boolean canEdit,
                                     List<ServerShopOfferListing> offers) {
        activeShopId = shopId;
        currentBalanceMinorUnits = balanceMinorUnits;
        currentBalanceKnown = true;
        currencyName = currency;
        currencyDecimals = decimals;
        catalogCategories = List.copyOf(categories);
        catalogItems = List.copyOf(items);
        catalogPromos = List.copyOf(promos);
        catalogBarterRecipes = List.copyOf(barterRecipes);
        catalogOffers = List.copyOf(offers);
        adminShopEnabled = adminEnabled;
        canEditAdminShop = canEdit;
        nearbyShops = List.copyOf(nearby);
        transactionHistory = List.of();
        synchronized (ownedItemCounts) {
            ownedItemCounts.clear();
        }
        sanitizeCart();
    }

    public static void applyShopData(String shopId, long balanceMinorUnits, String currency, int decimals,
                                     List<CatalogCategory> categories, List<CatalogItem> items,
                                     List<CatalogPromo> promos, List<CatalogBarterRecipe> barterRecipes,
                                     boolean adminEnabled, List<NearbyShopEntry> nearby, boolean canEdit) {
        applyShopData(shopId, balanceMinorUnits, currency, decimals,
                categories, items, promos, barterRecipes, adminEnabled,
                nearby, canEdit, List.of());
    }

    /**
     * Clears all state.  Called by {@code S2CForceClosePacket} to ensure
     * the client does not display stale catalog data after a forced close.
     */
    public static void reset() {
        activeShopId = "";
        currentBalanceMinorUnits = 0L;
        currentBalanceKnown = false;
        catalogCategories = List.of();
        catalogItems = List.of();
        catalogPromos = List.of();
        catalogBarterRecipes = List.of();
        catalogOffers = List.of();
        adminShopEnabled = true;
        canEditAdminShop = false;
        nearbyShops = List.of();
        transactionHistory = List.of();
        status = null;
        synchronized (cart) {
            cart.clear();
            offerCart.clear();
            cartNbtSnapshots.clear();
        }
        cartResponsePolicy.reset();
        trackedCartCheckout = null;
        synchronized (ownedItemCounts) {
            ownedItemCounts.clear();
        }
    }

    public static void setCurrentBalanceMinorUnits(long balanceMinorUnits) {
        currentBalanceMinorUnits = balanceMinorUnits;
        currentBalanceKnown = true;
    }

    public static void applyMarketWalletSnapshot(
            long balanceMinorUnits,
            boolean balanceKnown,
            String currency,
            int decimals
    ) {
        String name = java.util.Objects.requireNonNull(
                currency, "currency");
        if (name.isEmpty() || name.length() > 64
                || !name.equals(name.strip())
                || decimals < 0 || decimals > 6
                || !balanceKnown && balanceMinorUnits != 0L
                || !validCurrencyName(name)) {
            throw new IllegalArgumentException(
                    "Market wallet snapshot is invalid");
        }
        currentBalanceMinorUnits = balanceKnown
                ? balanceMinorUnits : 0L;
        currentBalanceKnown = balanceKnown;
        currencyName = name;
        currencyDecimals = decimals;
    }

    public static void clearMarketWalletSnapshot() {
        currentBalanceMinorUnits = 0L;
        currentBalanceKnown = false;
        currencyName = "Coins";
        currencyDecimals = 2;
    }

    private static boolean validCurrencyName(String name) {
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= name.length()
                        || !Character.isLowSurrogate(
                        name.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(character)
                    || Character.isISOControl(character)) {
                return false;
            }
        }
        return true;
    }

    // Listing tag snapshots taken at ADD-to-cart time, keyed by listingId — verify-cart
    // sends these so the server can flag a variant swap. Snapshotting at add time matters:
    // /shopadmin items edit resends the catalog, so a verify-time lookup would always
    // "see" the new tag and the warning could never fire.
    private static final Map<String, String> cartNbtSnapshots = new HashMap<>();

    public static void addToCart(String listingId, int quantity) {
        if (cartCheckoutBlocksMutation()) {
            return;
        }
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

    public static void addOfferToCart(
            String listingId,
            String optionId,
            int quantity,
            long observedRevision
    ) {
        if (cartCheckoutBlocksMutation() || quantity <= 0) {
            return;
        }
        ServerShopOfferListing listing =
                getCatalogOffer(listingId).orElse(null);
        if (listing == null || listing.acquireOptions().stream()
                .noneMatch(option -> option.optionId()
                        .equals(optionId))) {
            return;
        }
        OfferCartKey key = new OfferCartKey(
                listingId, optionId, observedRevision);
        int previous;
        int current;
        synchronized (cart) {
            previous = offerCart.getOrDefault(key, 0);
            current = Math.min(2304,
                    Math.addExact(previous, quantity));
            CatalogItem catalogItem = getCatalogItem(
                    listingId).orElse(null);
            if (catalogItem != null && !catalogItem.unlimited()) {
                current = Math.min(current, catalogItem.stock());
            }
            current = Math.min(current,
                    listing.limits().maximumPerRequest());
            AcquireOfferOption option = listing.acquireOptions().stream()
                    .filter(candidate -> candidate.optionId()
                            .equals(optionId))
                    .findFirst().orElseThrow();
            current = Math.min(current,
                    option.limits().maximumPerRequest());
            if (current > 0) {
                offerCart.put(key, current);
            }
        }
        if (current > previous) {
            setStatus(Component.translatable(
                    "gui.futureshops.status.cart.added",
                    current - previous, listing.displayName()), true);
        }
    }

    /** Listing tag as it looked when first added to the cart; "" when unknown/absent. */
    public static String getCartNbtSnapshot(String listingId) {
        synchronized (cart) {
            return cartNbtSnapshots.getOrDefault(listingId, "");
        }
    }

    public static void setCartQuantity(String listingId, int quantity) {
        if (cartCheckoutBlocksMutation()) {
            return;
        }
        synchronized (cart) {
            if (quantity <= 0) {
                cart.remove(listingId);
            } else {
                cart.put(listingId, clampCartQuantity(listingId, quantity));
            }
            sanitizeCartLocked();
        }
    }

    public static void setCartQuantity(CartEntry entry, int quantity) {
        if (!entry.normalized()) {
            setCartQuantity(entry.listingId(), quantity);
            return;
        }
        if (cartCheckoutBlocksMutation()) {
            return;
        }
        synchronized (cart) {
            OfferCartKey key = new OfferCartKey(
                    entry.listingId(), entry.optionId(),
                    entry.observedRevision());
            if (quantity <= 0) {
                offerCart.remove(key);
            } else {
                int clamped = clampOfferCartQuantity(key, quantity);
                if (clamped <= 0) {
                    offerCart.remove(key);
                } else {
                    offerCart.put(key, clamped);
                }
            }
        }
    }

    public static void removeFromCart(String listingId) {
        if (cartCheckoutBlocksMutation()) {
            return;
        }
        synchronized (cart) {
            cart.remove(listingId);
            cartNbtSnapshots.remove(listingId);
        }
    }

    public static void removeFromCart(CartEntry entry) {
        if (!entry.normalized()) {
            removeFromCart(entry.listingId());
            return;
        }
        if (cartCheckoutBlocksMutation()) {
            return;
        }
        synchronized (cart) {
            offerCart.remove(new OfferCartKey(
                    entry.listingId(), entry.optionId(),
                    entry.observedRevision()));
        }
    }

    public static void clearCart() {
        cartResponsePolicy.reset();
        trackedCartCheckout = null;
        clearCartContents();
    }

    public static void clearCartContents() {
        if (cartCheckoutBlocksMutation()) {
            return;
        }
        synchronized (cart) {
            cart.clear();
            offerCart.clear();
            cartNbtSnapshots.clear();
        }
    }

    public static CartResponsePolicy.BeginDecision beginCartCheckout(
            UUID requestId,
            List<CartEntry> entries,
            String paymentSource,
            long nowMillis
    ) {
        List<CartResponsePolicy.Line> lines = entries.stream()
                .map(entry -> new CartResponsePolicy.Line(
                        0, entry.cartKey(), entry.quantity()))
                .toList();
        CartResponsePolicy.BeginDecision decision = cartResponsePolicy.begin(
                requestId, lines, nowMillis, CART_CHECKOUT_TIMEOUT_MILLIS);
        if (decision == CartResponsePolicy.BeginDecision.STARTED) {
            trackedCartCheckout = new CartCheckoutSubmission(
                    requestId, activeShopId, List.copyOf(entries), paymentSource);
        }
        return decision;
    }

    public static Optional<CartCheckoutSubmission> retryCartCheckout(long nowMillis) {
        if (cartResponsePolicy.retry(nowMillis, CART_CHECKOUT_TIMEOUT_MILLIS)
                != CartResponsePolicy.RetryDecision.RETRIED) {
            return Optional.empty();
        }
        return Optional.ofNullable(trackedCartCheckout);
    }

    public static CartResponsePolicy.ResponseResult applyCartCheckoutResponse(
            UUID requestId,
            boolean success
    ) {
        return applyCartCheckoutResponse(requestId, success, true);
    }

    public static CartResponsePolicy.ResponseResult applyCartCheckoutResponse(
            UUID requestId,
            boolean success,
            boolean terminal
    ) {
        CartResponsePolicy.ResponseResult result =
                cartResponsePolicy.onResponse(
                        requestId, 0, success, terminal,
                        System.currentTimeMillis());
        if (!result.linesToClear().isEmpty()) {
            synchronized (cart) {
                for (CartResponsePolicy.Line line : result.linesToClear()) {
                    removeAcknowledgedCartQuantityLocked(line.key(), line.quantity());
                }
            }
        }
        if (result.checkoutComplete()) {
            trackedCartCheckout = null;
        }
        return result;
    }

    public static CartResponsePolicy.TimeoutDecision expireCartCheckout(long nowMillis) {
        return cartResponsePolicy.expire(nowMillis);
    }

    public static boolean isCartCheckoutPending() {
        return cartResponsePolicy.isPending();
    }

    public static boolean hasTrackedCartCheckout() {
        return cartResponsePolicy.hasTrackedRequest();
    }

    private static boolean cartCheckoutBlocksMutation() {
        return cartResponsePolicy.isPending();
    }

    private static void removeAcknowledgedCartQuantityLocked(
            String listingId,
            int quantity
    ) {
        OfferCartKey normalized = offerCart.keySet().stream()
                .filter(key -> key.cartKey().equals(listingId))
                .findFirst().orElse(null);
        if (normalized != null) {
            Integer current = offerCart.get(normalized);
            if (current == null || current <= quantity) {
                offerCart.remove(normalized);
            } else {
                offerCart.put(normalized, current - quantity);
            }
            return;
        }
        Integer current = cart.get(listingId);
        if (current == null) {
            return;
        }
        if (current <= quantity) {
            cart.remove(listingId);
            cartNbtSnapshots.remove(listingId);
        } else {
            cart.put(listingId, current - quantity);
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

    public static boolean isCurrentBalanceKnown() {
        return currentBalanceKnown;
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

    public static List<ServerShopOfferListing> getCatalogOffers() {
        return catalogOffers;
    }

    public static Optional<ServerShopOfferListing> getCatalogOffer(
            String listingId
    ) {
        return catalogOffers.stream().filter(offer ->
                offer.listingId().equals(listingId)).findFirst();
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
            List<CartEntry> values = new java.util.ArrayList<>();
            values.addAll(cart.entrySet().stream()
                    .map(entry -> new CartEntry(entry.getKey(), entry.getValue()))
                    .toList());
            values.addAll(offerCart.entrySet().stream()
                    .map(entry -> new CartEntry(
                            entry.getKey().listingId(),
                            entry.getKey().optionId(),
                            entry.getValue(),
                            entry.getKey().observedRevision(), true))
                    .toList());
            return List.copyOf(values);
        }
    }

    public static int getCartLineCount() {
        synchronized (cart) {
            return cart.size() + offerCart.size();
        }
    }

    public static int getCartTotalQuantity() {
        synchronized (cart) {
            return Math.addExact(
                    cart.values().stream()
                            .mapToInt(Integer::intValue).sum(),
                    offerCart.values().stream()
                            .mapToInt(Integer::intValue).sum());
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
            for (Map.Entry<OfferCartKey, Integer> entry
                    : offerCart.entrySet()) {
                ServerShopOfferListing listing = getCatalogOffer(
                        entry.getKey().listingId()).orElse(null);
                if (listing == null) {
                    continue;
                }
                com.enviouse.futureshops.catalog.offer.AcquireOfferOption
                        option = listing.acquireOptions().stream()
                        .filter(value -> value.optionId().equals(
                                entry.getKey().optionId()))
                        .findFirst().orElse(null);
                if (option != null && option.moneyCostPresent()) {
                    total = Math.addExact(total, Math.multiplyExact(
                            option.moneyCostMinorUnits(),
                            entry.getValue()));
                }
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
            return item == null || item.buyPrice() <= 0L || entry.getValue() <= 0;
        });
        cartNbtSnapshots.keySet().retainAll(cart.keySet());
        var iterator = offerCart.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            int clamped = clampOfferCartQuantity(
                    entry.getKey(), entry.getValue());
            if (clamped <= 0) {
                iterator.remove();
            } else {
                entry.setValue(clamped);
            }
        }
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

    private static int clampOfferCartQuantity(
            OfferCartKey key,
            int quantity
    ) {
        ServerShopOfferListing listing = getCatalogOffer(
                key.listingId()).orElse(null);
        if (listing == null) {
            return 0;
        }
        AcquireOfferOption option = listing.acquireOptions().stream()
                .filter(candidate -> candidate.optionId()
                        .equals(key.optionId()))
                .findFirst().orElse(null);
        if (option == null) {
            return 0;
        }
        int clamped = Math.max(1, Math.min(2304, quantity));
        clamped = Math.min(clamped,
                listing.limits().maximumPerRequest());
        clamped = Math.min(clamped,
                option.limits().maximumPerRequest());
        CatalogItem item = getCatalogItem(key.listingId()).orElse(null);
        if (item != null && !item.unlimited()) {
            clamped = Math.min(clamped,
                    item.stock() / option.outputMultiplier());
        }
        return clamped;
    }

    /** {@code listingId} is the catalog resolution key for this cart line (see {@link CatalogItem}). */
    public record CartEntry(
            String listingId,
            String optionId,
            int quantity,
            long observedRevision,
            boolean normalized
    ) {
        public CartEntry(String listingId, int quantity) {
            this(listingId, "", quantity, 0L, false);
        }

        public String cartKey() {
            return normalized
                    ? "offer\u0000" + listingId + "\u0000"
                    + optionId + "\u0000" + observedRevision
                    : listingId;
        }
    }

    public record CartCheckoutSubmission(
            UUID requestId,
            String shopId,
            List<CartEntry> entries,
            String paymentSource
    ) {
        public CartCheckoutSubmission {
            entries = List.copyOf(entries);
        }
    }

    public record ShopStatus(Component message, boolean success, long expiresAtMillis) {
    }

    private record OfferCartKey(
            String listingId,
            String optionId,
            long observedRevision
    ) {
        private String cartKey() {
            return "offer\u0000" + listingId + "\u0000"
                    + optionId + "\u0000" + observedRevision;
        }
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
