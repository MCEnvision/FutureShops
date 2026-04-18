package com.enviouse.futureshops.client;

import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * Items 34+35: Client-side cart for player shops.
 * Keyed by (shopPos, listingIndex). Cleared on disconnect.
 * No server-side persistence needed — purely ephemeral.
 */
public final class PlayerShopCartState {

    /**
     * A single cart entry: which shop, which listing, and how many units.
     * LGB#2/#3/#4: Includes trade mode and barter info for proper cart display.
     * LGB#4: chosenPayment tracks user's money/barter preference for BOTH-mode entries.
     */
    public record CartEntry(BlockPos shopPos, int listingIndex, int quantity,
                            String itemId, String shopName, long unitPriceMinor, int baseQuantity,
                            String tradeMode, String barterItemId, int barterItemCount, String nbtJson,
                            String chosenPayment, boolean nbtAware) {
        public long totalPrice() {
            return unitPriceMinor * quantity;
        }

        public int totalItems() {
            return baseQuantity * quantity;
        }
    }

    private static final List<CartEntry> entries = new ArrayList<>();

    private PlayerShopCartState() {
    }

    /**
     * Adds an item to the cart. If an entry for the same shop+listing already exists, increments quantity.
     */
    public static void addToCart(BlockPos shopPos, int listingIndex, int quantity,
                                 String itemId, String shopName, long unitPriceMinor, int baseQuantity,
                                 String tradeMode, String barterItemId, int barterItemCount, String nbtJson,
                                 boolean nbtAware) {
        if (quantity <= 0) return;
        // LGB#4: Default chosen payment for BOTH = MONEY
        String defaultPayment = "BOTH".equalsIgnoreCase(tradeMode) ? "MONEY" : "";
        synchronized (entries) {
            for (CartEntry entry : entries) {
                if (entry.shopPos().equals(shopPos) && entry.listingIndex() == listingIndex) {
                    int newQty = entry.quantity() + quantity;
                    entries.set(entries.indexOf(entry),
                            new CartEntry(shopPos, listingIndex, newQty, itemId, shopName, unitPriceMinor, baseQuantity,
                                    tradeMode, barterItemId, barterItemCount, nbtJson, entry.chosenPayment(), nbtAware));
                    return;
                }
            }
            entries.add(new CartEntry(shopPos, listingIndex, quantity, itemId, shopName, unitPriceMinor, baseQuantity,
                    tradeMode, barterItemId, barterItemCount, nbtJson, defaultPayment, nbtAware));
        }
    }

    /**
     * Sets the quantity of a specific cart entry. Removes if quantity <= 0.
     */
    public static void setQuantity(int cartIndex, int quantity) {
        synchronized (entries) {
            if (cartIndex < 0 || cartIndex >= entries.size()) return;
            if (quantity <= 0) {
                entries.remove(cartIndex);
            } else {
                CartEntry old = entries.get(cartIndex);
                entries.set(cartIndex, new CartEntry(old.shopPos(), old.listingIndex(), quantity,
                        old.itemId(), old.shopName(), old.unitPriceMinor(), old.baseQuantity(),
                        old.tradeMode(), old.barterItemId(), old.barterItemCount(), old.nbtJson(), old.chosenPayment(), old.nbtAware()));
            }
        }
    }

    /**
     * LGB#4: Toggles the chosen payment method for a BOTH-mode entry between MONEY and BARTER.
     */
    public static void togglePayment(int cartIndex) {
        synchronized (entries) {
            if (cartIndex < 0 || cartIndex >= entries.size()) return;
            CartEntry old = entries.get(cartIndex);
            if (!"BOTH".equalsIgnoreCase(old.tradeMode())) return;
            String newPayment = "BARTER".equalsIgnoreCase(old.chosenPayment()) ? "MONEY" : "BARTER";
            entries.set(cartIndex, new CartEntry(old.shopPos(), old.listingIndex(), old.quantity(),
                    old.itemId(), old.shopName(), old.unitPriceMinor(), old.baseQuantity(),
                    old.tradeMode(), old.barterItemId(), old.barterItemCount(), old.nbtJson(), newPayment, old.nbtAware()));
        }
    }

    /**
     * Removes a specific entry by index.
     */
    public static void remove(int cartIndex) {
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
        synchronized (entries) {
            entries.clear();
        }
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

