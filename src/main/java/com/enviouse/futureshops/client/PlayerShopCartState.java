package com.enviouse.futureshops.client;

import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * Items 34+35: Client-side cart for player shops.
 * Keyed by (shopPos, listingIndex). Cleared on disconnect.
 * No server-side persistence needed — purely ephemeral.
 */
public final class PlayerShopCartState {
    public static final long CART_CHECKOUT_TIMEOUT_MILLIS = 15_000L;

    /**
     * A single cart entry: which shop, which listing, and how many units.
     * LGB#2/#3/#4: Includes trade mode and barter info for proper cart display.
     * LGB#4: chosenPayment tracks user's money/barter preference for BOTH-mode entries.
     */
    public record CartEntry(BlockPos shopPos, int listingIndex, int quantity,
                            String itemId, String shopName, long unitPriceMinor, int baseQuantity,
                            String tradeMode, String barterItemId, int barterItemCount, String barterNbtJson,
                            String nbtJson, String chosenPayment, boolean nbtAware) {
        public long totalPrice() {
            return unitPriceMinor * quantity;
        }

        public int totalItems() {
            return baseQuantity * quantity;
        }
    }

    private static final List<CartEntry> entries = new ArrayList<>();
    private static final CartResponsePolicy cartResponsePolicy = new CartResponsePolicy();
    private static CheckoutSubmission trackedCheckout;

    private PlayerShopCartState() {
    }

    /**
     * Adds an item to the cart. If an entry for the same shop+listing already exists, increments quantity.
     */
    public static void addToCart(BlockPos shopPos, int listingIndex, int quantity,
                                 String itemId, String shopName, long unitPriceMinor, int baseQuantity,
                                 String tradeMode, String barterItemId, int barterItemCount, String barterNbtJson,
                                 String nbtJson, boolean nbtAware) {
        if (checkoutBlocksMutation()) return;
        if (quantity <= 0) return;
        // Reject blank / air listings outright so they can't enter the cart or the
        // transaction history. Modded bundles sometimes expose a primary item as "air"
        // (e.g. Create's metal-block variants) — add-to-cart should no-op in that case.
        if (itemId == null || itemId.isBlank() || "minecraft:air".equals(itemId)) return;
        // LGB#4: Default chosen payment per mode.
        //  - BOTH             → "MONEY" (toggleable to "BARTER" via togglePayment)
        //  - MONEY_AND_BARTER → "MONEY_AND_BARTER" (locked; compound trade takes both)
        //  - anything else    → "" (server enforces the single valid mode)
        String defaultPayment;
        if ("BOTH".equalsIgnoreCase(tradeMode)) {
            defaultPayment = "MONEY";
        } else if ("MONEY_AND_BARTER".equalsIgnoreCase(tradeMode)) {
            defaultPayment = "MONEY_AND_BARTER";
        } else {
            defaultPayment = "";
        }
        synchronized (entries) {
            for (CartEntry entry : entries) {
                if (entry.shopPos().equals(shopPos) && entry.listingIndex() == listingIndex) {
                    int newQty = entry.quantity() + quantity;
                    entries.set(entries.indexOf(entry),
                            new CartEntry(shopPos, listingIndex, newQty, itemId, shopName, unitPriceMinor, baseQuantity,
                                    tradeMode, barterItemId, barterItemCount, barterNbtJson, nbtJson, entry.chosenPayment(), nbtAware));
                    return;
                }
            }
            entries.add(new CartEntry(shopPos, listingIndex, quantity, itemId, shopName, unitPriceMinor, baseQuantity,
                    tradeMode, barterItemId, barterItemCount, barterNbtJson, nbtJson, defaultPayment, nbtAware));
        }
    }

    /**
     * Sets the quantity of a specific cart entry. Removes if quantity <= 0.
     */
    public static void setQuantity(int cartIndex, int quantity) {
        if (checkoutBlocksMutation()) return;
        synchronized (entries) {
            if (cartIndex < 0 || cartIndex >= entries.size()) return;
            if (quantity <= 0) {
                entries.remove(cartIndex);
            } else {
                CartEntry old = entries.get(cartIndex);
                entries.set(cartIndex, new CartEntry(old.shopPos(), old.listingIndex(), quantity,
                        old.itemId(), old.shopName(), old.unitPriceMinor(), old.baseQuantity(),
                        old.tradeMode(), old.barterItemId(), old.barterItemCount(), old.barterNbtJson(), old.nbtJson(), old.chosenPayment(), old.nbtAware()));
            }
        }
    }

    /**
     * LGB#4: Toggles the chosen payment method for a BOTH-mode entry between MONEY and BARTER.
     * Intentionally a no-op for MONEY_AND_BARTER (compound) entries — those always take both
     * and the cart row surfaces a locked "M+B" badge rather than a toggle.
     */
    public static void togglePayment(int cartIndex) {
        if (checkoutBlocksMutation()) return;
        synchronized (entries) {
            if (cartIndex < 0 || cartIndex >= entries.size()) return;
            CartEntry old = entries.get(cartIndex);
            if (!"BOTH".equalsIgnoreCase(old.tradeMode())) return;
            String newPayment = "BARTER".equalsIgnoreCase(old.chosenPayment()) ? "MONEY" : "BARTER";
            entries.set(cartIndex, new CartEntry(old.shopPos(), old.listingIndex(), old.quantity(),
                    old.itemId(), old.shopName(), old.unitPriceMinor(), old.baseQuantity(),
                    old.tradeMode(), old.barterItemId(), old.barterItemCount(), old.barterNbtJson(), old.nbtJson(), newPayment, old.nbtAware()));
        }
    }

    /**
     * Removes a specific entry by index.
     */
    public static void remove(int cartIndex) {
        if (checkoutBlocksMutation()) return;
        synchronized (entries) {
            if (cartIndex >= 0 && cartIndex < entries.size()) {
                entries.remove(cartIndex);
            }
        }
    }

    /**
     * Clears all cart entries.
     */
    public static void clear() {
        cartResponsePolicy.reset();
        trackedCheckout = null;
        clearEntries();
    }

    public static void clearEntries() {
        if (checkoutBlocksMutation()) return;
        synchronized (entries) {
            entries.clear();
        }
    }

    public static CartResponsePolicy.BeginDecision beginCheckout(
            UUID requestId,
            List<CartEntry> checkoutEntries,
            String paymentSource,
            long nowMillis
    ) {
        List<CartResponsePolicy.Line> lines = new ArrayList<>(checkoutEntries.size());
        for (int index = 0; index < checkoutEntries.size(); index++) {
            CartEntry entry = checkoutEntries.get(index);
            lines.add(new CartResponsePolicy.Line(
                    index, checkoutKey(entry.shopPos(), entry.listingIndex()), entry.quantity()));
        }
        CartResponsePolicy.BeginDecision decision = cartResponsePolicy.begin(
                requestId, lines, nowMillis, CART_CHECKOUT_TIMEOUT_MILLIS);
        if (decision == CartResponsePolicy.BeginDecision.STARTED) {
            trackedCheckout = new CheckoutSubmission(
                    requestId, List.copyOf(checkoutEntries), paymentSource);
        }
        return decision;
    }

    public static Optional<CheckoutSubmission> retryCheckout(long nowMillis) {
        if (cartResponsePolicy.retry(nowMillis, CART_CHECKOUT_TIMEOUT_MILLIS)
                != CartResponsePolicy.RetryDecision.RETRIED) {
            return Optional.empty();
        }
        return Optional.ofNullable(trackedCheckout);
    }

    public static CartResponsePolicy.ResponseResult applyCheckoutResponse(
            UUID requestId,
            int responseToken,
            boolean success
    ) {
        CartResponsePolicy.ResponseResult result =
                cartResponsePolicy.onResponse(
                        requestId, responseToken, success, true, System.currentTimeMillis());
        if (!result.linesToClear().isEmpty()) {
            synchronized (entries) {
                for (CartResponsePolicy.Line line : result.linesToClear()) {
                    removeAcknowledgedQuantityLocked(line.key(), line.quantity());
                }
            }
        }
        if (result.checkoutComplete()) {
            trackedCheckout = null;
        }
        return result;
    }

    public static CartResponsePolicy.TimeoutDecision expireCheckout(long nowMillis) {
        return cartResponsePolicy.expire(nowMillis);
    }

    public static boolean isCheckoutPending() {
        return cartResponsePolicy.isPending();
    }

    public static boolean hasTrackedCheckout() {
        return cartResponsePolicy.hasTrackedRequest();
    }

    private static boolean checkoutBlocksMutation() {
        return cartResponsePolicy.isPending();
    }

    private static void removeAcknowledgedQuantityLocked(String key, int quantity) {
        for (int index = 0; index < entries.size(); index++) {
            CartEntry entry = entries.get(index);
            if (!checkoutKey(entry.shopPos(), entry.listingIndex()).equals(key)) {
                continue;
            }
            if (entry.quantity() <= quantity) {
                entries.remove(index);
            } else {
                entries.set(index, withQuantity(entry, entry.quantity() - quantity));
            }
            return;
        }
    }

    private static CartEntry withQuantity(CartEntry entry, int quantity) {
        return new CartEntry(
                entry.shopPos(), entry.listingIndex(), quantity,
                entry.itemId(), entry.shopName(), entry.unitPriceMinor(), entry.baseQuantity(),
                entry.tradeMode(), entry.barterItemId(), entry.barterItemCount(),
                entry.barterNbtJson(), entry.nbtJson(), entry.chosenPayment(), entry.nbtAware());
    }

    private static String checkoutKey(BlockPos shopPos, int listingIndex) {
        return shopPos.asLong() + "." + listingIndex;
    }

    /**
     * Returns a snapshot of all cart entries.
     */
    public static List<CartEntry> getEntries() {
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }

    public static int size() {
        synchronized (entries) {
            return entries.size();
        }
    }

    public static boolean isEmpty() {
        synchronized (entries) {
            return entries.isEmpty();
        }
    }

    public static int totalQuantity() {
        synchronized (entries) {
            return entries.stream().mapToInt(CartEntry::quantity).sum();
        }
    }

    public static long totalPrice() {
        synchronized (entries) {
            return entries.stream().mapToLong(CartEntry::totalPrice).sum();
        }
    }

    public record CheckoutSubmission(
            UUID requestId,
            List<CartEntry> entries,
            String paymentSource
    ) {
        public CheckoutSubmission {
            entries = List.copyOf(entries);
        }
    }

    /**
     * Summary DTO for the cart totals: money portion + per-barter-item aggregates.
     */
    public record CartSummary(int itemCount, long moneyTotal, java.util.Map<String, Integer> barterTotals) {
    }

    /**
     * Builds an aggregated summary of the cart, splitting money and barter costs
     * based on each entry's trade mode / chosen payment.
     */
    public static CartSummary buildSummary() {
        synchronized (entries) {
            int count = entries.size();
            long money = 0L;
            java.util.LinkedHashMap<String, Integer> barter = new java.util.LinkedHashMap<>();
            for (CartEntry e : entries) {
                String mode = e.tradeMode().toUpperCase(java.util.Locale.ROOT);
                boolean paysBarter;
                if ("BOTH".equals(mode)) {
                    paysBarter = "BARTER".equalsIgnoreCase(e.chosenPayment());
                } else {
                    paysBarter = "BARTER".equals(mode);
                }

                if ("MONEY_AND_BARTER".equals(mode)) {
                    // Compound: always money + barter
                    money += e.totalPrice();
                    addBarter(barter, e);
                } else if (paysBarter) {
                    addBarter(barter, e);
                } else {
                    money += e.totalPrice();
                }
            }
            return new CartSummary(count, money, barter);
        }
    }

    private static void addBarter(java.util.Map<String, Integer> map, CartEntry e) {
        if (e.barterItemId() == null || e.barterItemId().isBlank()) return;
        int total = e.barterItemCount() * e.quantity();
        map.merge(e.barterItemId(), total, Integer::sum);
    }
}
