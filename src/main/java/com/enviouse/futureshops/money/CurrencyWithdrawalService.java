package com.enviouse.futureshops.money;

import com.enviouse.futureshops.server.economy.BalanceManager;
import com.enviouse.futureshops.server.economy.EconomyProvider;
import com.enviouse.futureshops.server.economy.TransactionResult;
import com.enviouse.futureshops.server.shop.ShopResultCode;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Server-authoritative physical-currency withdrawal engine shared by /withdraw
 * and the ATM. The balance debit and item delivery are planned as one operation;
 * unexpected partial inventory insertion is explicitly removed before the debit
 * is refunded, closing the old "some bills + refunded balance" failure window.
 */
public final class CurrencyWithdrawalService {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int MAX_DENOMINATIONS = 32;
    public static final int MAX_SELECTED_ITEMS = 4096;

    public enum Code {
        SUCCESS,
        INVALID_AMOUNT,
        BELOW_MINIMUM,
        NOT_REPRESENTABLE,
        INVALID_PLAN,
        CURRENCY_CHANGED,
        NO_INVENTORY_SPACE,
        INSUFFICIENT_FUNDS,
        CANCELLED,
        SERVER_ERROR
    }

    /** One inventory-stack-sized mint instruction. */
    public record BillPortion(int denominationIndex, long valueMinor, int count) {
    }

    public record Result(boolean success, Code code, long amountMinor, long resultingBalance,
                         List<BillPortion> bills, ShopResultCode providerError) {
        private static Result error(Code code, long balance) {
            return new Result(false, code, 0L, balance, List.of(), ShopResultCode.OK);
        }

        private static Result providerError(Code code, long balance, ShopResultCode providerError) {
            return new Result(false, code, 0L, balance, List.of(), providerError);
        }
    }

    private CurrencyWithdrawalService() {
    }

    /** Automatic largest-first plan used by the legacy command. */
    public static Result withdrawAutomatic(ServerPlayer player, long amountMinor, boolean multipleBills) {
        PhysicalCurrencyAdapter currency = CurrencyManager.get();
        EconomyProvider provider = BalanceManager.getProvider();
        long balance = provider.getBalance(player.getUUID());
        List<PhysicalCurrencyAdapter.Denomination> denominations = currency.denominations();
        if (amountMinor <= 0L) {
            return Result.error(Code.INVALID_AMOUNT, balance);
        }
        long smallest = denominations.get(denominations.size() - 1).valueMinor();
        if (amountMinor < smallest) {
            return Result.error(Code.BELOW_MINIMUM, balance);
        }

        long[] values = new long[denominations.size()];
        int[] maxStacks = new int[denominations.size()];
        for (int i = 0; i < denominations.size(); i++) {
            PhysicalCurrencyAdapter.Denomination denomination = denominations.get(i);
            values[i] = denomination.valueMinor();
            maxStacks[i] = Math.max(1, new ItemStack(denomination.item()).getMaxStackSize());
        }
        CurrencyMath.BreakResult breakdown = CurrencyMath.breakIntoDenominations(amountMinor, values, maxStacks);
        if (breakdown.remainderMinor() != 0L) {
            return Result.error(Code.NOT_REPRESENTABLE, balance);
        }
        // Internal bills store their face value in protected NBT, so legacy
        // single-bill mode may combine an otherwise-valid denomination plan
        // into one protected bill. Representability is checked first to retain
        // /withdraw's existing whole-smallest-unit contract.
        if (!multipleBills && currency.supportsSingleBill()) {
            List<BillPortion> portions = List.of(new BillPortion(-1, amountMinor, 1));
            return execute(player, currency, provider, amountMinor, portions);
        }
        List<BillPortion> portions = breakdown.portions().stream()
                .map(p -> new BillPortion(p.denominationIndex(), values[p.denominationIndex()], p.count()))
                .toList();
        return execute(player, currency, provider, amountMinor, portions);
    }

    /** Exact manual denomination plan submitted by the ATM. */
    public static Result withdrawSelected(ServerPlayer player, String expectedSignature, List<Integer> counts) {
        PhysicalCurrencyAdapter currency = CurrencyManager.get();
        EconomyProvider provider = BalanceManager.getProvider();
        long balance = provider.getBalance(player.getUUID());
        List<PhysicalCurrencyAdapter.Denomination> denominations = currency.denominations();
        if (!signature(currency).equals(expectedSignature)) {
            return Result.error(Code.CURRENCY_CHANGED, balance);
        }
        int advertisedCount = Math.min(denominations.size(), MAX_DENOMINATIONS);
        if (counts == null || counts.size() != advertisedCount) {
            return Result.error(Code.INVALID_PLAN, balance);
        }

        long amount = 0L;
        int totalItems = 0;
        List<BillPortion> portions = new ArrayList<>();
        try {
            for (int i = 0; i < counts.size(); i++) {
                int count = counts.get(i) == null ? -1 : counts.get(i);
                if (count < 0) {
                    return Result.error(Code.INVALID_PLAN, balance);
                }
                totalItems = Math.addExact(totalItems, count);
                if (totalItems > MAX_SELECTED_ITEMS) {
                    return Result.error(Code.INVALID_PLAN, balance);
                }
                if (count == 0) continue;
                PhysicalCurrencyAdapter.Denomination denomination = denominations.get(i);
                amount = Math.addExact(amount, Math.multiplyExact(denomination.valueMinor(), (long) count));
                int maxStack = Math.max(1, new ItemStack(denomination.item()).getMaxStackSize());
                int remaining = count;
                while (remaining > 0) {
                    int portion = Math.min(remaining, maxStack);
                    portions.add(new BillPortion(i, denomination.valueMinor(), portion));
                    remaining -= portion;
                }
            }
        } catch (ArithmeticException ex) {
            return Result.error(Code.INVALID_PLAN, balance);
        }
        if (amount <= 0L || portions.isEmpty()) {
            return Result.error(Code.INVALID_AMOUNT, balance);
        }
        return execute(player, currency, provider, amount, portions);
    }

    /** Stable fingerprint echoed by the client so a hot-reloaded currency config fails closed. */
    public static String signature(PhysicalCurrencyAdapter currency) {
        StringBuilder raw = new StringBuilder(currency.id()).append('|').append(currency.isInternal());
        for (PhysicalCurrencyAdapter.Denomination denomination : currency.denominations()) {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(denomination.item());
            raw.append('|').append(key == null ? "unknown" : key)
                    .append('=').append(denomination.valueMinor());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM has no SHA-256 provider", impossible);
        }
    }

    private static Result execute(ServerPlayer player, PhysicalCurrencyAdapter currency,
                                  EconomyProvider provider, long amount, List<BillPortion> portions) {
        long balance = provider.getBalance(player.getUUID());
        if (!hasInventorySpace(player, currency, portions)) {
            return Result.error(Code.NO_INVENTORY_SPACE, balance);
        }

        TransactionResult debit = provider.withdraw(player.getUUID(), amount, "WITHDRAW");
        if (!debit.success()) {
            Code code = switch (debit.errorCode()) {
                case INSUFFICIENT_FUNDS -> Code.INSUFFICIENT_FUNDS;
                case CANCELLED_BY_EVENT -> Code.CANCELLED;
                default -> Code.SERVER_ERROR;
            };
            return Result.providerError(code, debit.resultingBalance(), debit.errorCode());
        }

        List<ItemStack> inserted = new ArrayList<>();
        List<PhysicalCurrencyAdapter.Denomination> denominations = currency.denominations();
        for (BillPortion portion : portions) {
            ItemStack minted;
            if (portion.denominationIndex() < 0) {
                minted = currency.mint(player, portion.valueMinor(), portion.count());
            } else if (portion.denominationIndex() < denominations.size()) {
                minted = currency.mint(player, denominations.get(portion.denominationIndex()), portion.count());
            } else {
                minted = ItemStack.EMPTY;
            }
            if (minted.isEmpty()) {
                rollback(player, provider, amount, inserted);
                return Result.error(Code.SERVER_ERROR, provider.getBalance(player.getUUID()));
            }

            ItemStack exactCopy = minted.copy();
            int before = minted.getCount();
            boolean fullyAdded = player.getInventory().add(minted);
            int insertedCount = before - minted.getCount();
            if (insertedCount > 0) {
                inserted.add(exactCopy.copyWithCount(insertedCount));
            }
            if (!fullyAdded || !minted.isEmpty()) {
                rollback(player, provider, amount, inserted);
                return Result.error(Code.SERVER_ERROR, provider.getBalance(player.getUUID()));
            }
        }
        player.containerMenu.broadcastChanges();
        return new Result(true, Code.SUCCESS, amount, debit.resultingBalance(), List.copyOf(portions), ShopResultCode.OK);
    }

    private static boolean hasInventorySpace(ServerPlayer player, PhysicalCurrencyAdapter currency,
                                             List<BillPortion> portions) {
        int emptySlots = (int) player.getInventory().items.stream().filter(ItemStack::isEmpty).count();
        if (currency.isInternal()) {
            // Every protected mint portion receives a unique mint id and cannot
            // merge with existing bills or another newly minted portion.
            return portions.size() <= emptySlots;
        }

        Map<Item, Integer> remainingByItem = new HashMap<>();
        List<PhysicalCurrencyAdapter.Denomination> denominations = currency.denominations();
        for (BillPortion portion : portions) {
            if (portion.denominationIndex() < 0 || portion.denominationIndex() >= denominations.size()) {
                return false;
            }
            remainingByItem.merge(denominations.get(portion.denominationIndex()).item(), portion.count(), Integer::sum);
        }
        int slotsNeeded = 0;
        for (Map.Entry<Item, Integer> entry : remainingByItem.entrySet()) {
            ItemStack prototype = new ItemStack(entry.getKey());
            int remaining = entry.getValue();
            for (ItemStack existing : player.getInventory().items) {
                if (existing.isEmpty() || !ItemStack.isSameItemSameTags(existing, prototype)) continue;
                remaining -= Math.max(0, existing.getMaxStackSize() - existing.getCount());
                if (remaining <= 0) break;
            }
            if (remaining > 0) {
                int maxStack = Math.max(1, prototype.getMaxStackSize());
                slotsNeeded += (remaining + maxStack - 1) / maxStack;
            }
        }
        return slotsNeeded <= emptySlots;
    }

    private static void rollback(ServerPlayer player, EconomyProvider provider, long amount,
                                 List<ItemStack> inserted) {
        for (ItemStack template : inserted) {
            int remaining = template.getCount();
            for (ItemStack stack : player.getInventory().items) {
                if (remaining <= 0) break;
                if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, template)) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                }
            }
            if (remaining > 0) {
                LOGGER.error("ATM rollback could not remove {} item(s) matching {} from {}",
                        remaining, template.getItem(), player.getGameProfile().getName());
            }
        }
        TransactionResult refund = provider.deposit(player.getUUID(), amount, "WITHDRAW_ROLLBACK");
        if (!refund.success()) {
            LOGGER.error("ATM rollback could not refund {} minor units to {}: {}",
                    amount, player.getGameProfile().getName(), refund.errorCode());
        }
        player.containerMenu.broadcastChanges();
    }
}
