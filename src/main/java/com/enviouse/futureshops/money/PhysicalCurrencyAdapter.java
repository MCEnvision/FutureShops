package com.enviouse.futureshops.money;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The physical-item side of the economy: what {@code /withdraw} mints and what
 * {@code /deposit} (and right-click depositing) accepts. The balance ledger
 * (EconomyProvider) is untouched by this abstraction — adapters only decide
 * which ItemStacks represent money and what they are worth. Selected via the
 * {@code currency.provider} config and resolved once per server start by
 * {@link CurrencyManager}.
 */
public interface PhysicalCurrencyAdapter {

    /** Config id of this adapter ({@code futureshops}, {@code apocalypsenow}, {@code custom}). */
    String id();

    default String depositConfigurationSignature() {
        return id();
    }

    /** True only for the built-in checksum+ledger money item. */
    default boolean isInternal() {
        return false;
    }

    /**
     * True when {@code /withdraw <amount> no} may mint one bill holding the full
     * amount. Only the internal currency supports this — its denomination is NBT
     * data, whereas foreign denominations are distinct fixed-value items.
     */
    default boolean supportsSingleBill() {
        return isInternal();
    }

    /** Mintable denominations, sorted by value descending. Never empty. */
    List<Denomination> denominations();

    /**
     * Value of ONE item of this stack when deposited; 0 = not accepted currency.
     * Foreign adapters are deliberately tag-agnostic so minted stacks keep
     * stacking with loot-obtained ones.
     */
    long unitValueMinor(ItemStack stack);

    /**
     * Mints one stack of {@code count} units worth {@code valueMinor} each.
     * The internal adapter also registers the mint ledger record and fires
     * {@code MoneyMintEvent}. Returns {@link ItemStack#EMPTY} on failure.
     */
    ItemStack mint(ServerPlayer player, long valueMinor, int count);

    /**
     * Mints a specific advertised denomination. Foreign providers may expose two
     * different items with the same face value, so ATM withdrawals retain the
     * item identity instead of resolving by value alone. The built-in provider
     * can use the value-based implementation because all denominations share one
     * registry item and differ by protected NBT.
     */
    default ItemStack mint(ServerPlayer player, Denomination denomination, int count) {
        return mint(player, denomination.valueMinor(), count);
    }

    /**
     * Pre-deposit sweep destroying counterfeit currency (checksum failures).
     * Foreign currency has no checksum concept, so foreign adapters return 0
     * and destroy nothing.
     */
    int destroyCounterfeit(ServerPlayer player);

    /** Total value currently depositable from the player's main inventory + offhand. */
    long availableValueMinor(ServerPlayer player);

    /**
     * Human-readable list of accepted items and their values, e.g.
     * {@code "apocalypsenow:money=1.00, apocalypsenow:coins=0.25"} — used in
     * the /deposit "nothing accepted" message so admins can see at a glance
     * what the active provider actually takes. Null for the internal currency
     * (its message already says "coins").
     */
    default String acceptedItemsSummary(int decimalPlaces) {
        return null;
    }

    /**
     * Consumes currency from the player's inventory worth up to {@code targetMinor}
     * and returns what was actually consumed; the caller credits the balance
     * afterwards. The internal adapter may overshoot the target by less than one
     * coin when an indivisible coin has to be consumed (long-standing behavior);
     * foreign adapters never overshoot.
     */
    ConsumeSummary consumeUpTo(ServerPlayer player, long targetMinor);

    ExactPayment consumeExact(ServerPlayer player, long targetMinor);

    void refundExact(ServerPlayer player, ExactPayment payment);

    record Denomination(Item item, long valueMinor) {
    }

    /**
     * {@code refundableStacks} holds copies of what was consumed so the caller
     * can restore them if the balance credit fails afterwards. Empty for the
     * internal currency: its ledger consume is intentionally irreversible
     * (pre-2.2 behavior), whereas foreign face-value items are trivially
     * restorable.
     */
    record ConsumeSummary(long creditedMinor, int itemsConsumed, List<ItemStack> refundableStacks) {
    }

    record PaymentPortion(long valueMinor, int count, ItemStack refundableStack) {
    }

    record ExactPayment(boolean success, long amountMinor, int itemsConsumed, List<PaymentPortion> portions) {
        public static ExactPayment failed() {
            return new ExactPayment(false, 0L, 0, List.of());
        }
    }
}
