package com.enviouse.futureshops.money;

import com.enviouse.futureshops.event.MoneyMintEvent;
import com.enviouse.futureshops.init.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The built-in physical currency: the {@code futureshops:money} item with
 * per-stack denomination NBT, SHA-256 checksums and the SpentMints ledger.
 * The mint/plan/consume logic here is a straight move from WithdrawCommand /
 * DepositCommand so the anti-dupe semantics are byte-identical to pre-2.2.
 */
public final class InternalCurrencyAdapter implements PhysicalCurrencyAdapter {
    public static final String ID = "futureshops";

    /**
     * Bill denominations in minor units (cents), sorted largest-first.
     * Corresponds to $1000, $100, $50, $20, $10, $5, $1 bills.
     */
    private static final long[] DENOMINATION_VALUES = {
            100_000L, // $1000
            10_000L,  // $100
            5_000L,   // $50
            2_000L,   // $20
            1_000L,   // $10
            500L,     // $5
            100L      // $1
    };

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public List<Denomination> denominations() {
        Item money = ModItems.MONEY_ITEM.get();
        List<Denomination> out = new ArrayList<>(DENOMINATION_VALUES.length);
        for (long value : DENOMINATION_VALUES) {
            out.add(new Denomination(money, value));
        }
        return out;
    }

    @Override
    public long unitValueMinor(ItemStack stack) {
        MoneyValidationResult result = MoneyValidationService.validate(stack);
        return result.valid() ? result.denominationMinorUnits() : 0L;
    }

    @Override
    public ItemStack mint(ServerPlayer player, long valueMinor, int count) {
        ItemStack stack = MoneyMintService.mintStack(player, count, valueMinor);

        CompoundTag moneyData = stack.getOrCreateTag().getCompound(MoneyNbtKeys.ROOT);
        // authorizedCount == batch size; the entire stack shares one mint ID so
        // it remains stackable with itself across splits.
        SpentMintsSavedData.get(player.getServer()).registerMint(
                moneyData.getString(MoneyNbtKeys.MINT_ID),
                player.getUUID(),
                valueMinor,
                count,
                moneyData.getLong(MoneyNbtKeys.MINT_TIMESTAMP),
                moneyData.getString(MoneyNbtKeys.MINT_SERVER));

        // Fire MoneyMintEvent (spec §33)
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new MoneyMintEvent(player.getUUID(), valueMinor, count,
                        moneyData.getString(MoneyNbtKeys.MINT_ID)));
        return stack;
    }

    @Override
    public int destroyCounterfeit(ServerPlayer player) {
        Item coinItem = ModItems.MONEY_ITEM.get();
        int destroyed = 0;
        for (ItemStack stack : allCoinContainers(player)) {
            if (stack.getItem() != coinItem) continue;
            MoneyValidationResult validation = MoneyValidationService.validate(stack);
            if (!validation.valid()) {
                destroyed += stack.getCount();
                stack.setCount(0);
            }
        }
        return destroyed;
    }

    @Override
    public long availableValueMinor(ServerPlayer player) {
        return planValidStacks(player).stream()
                .mapToLong(p -> p.denomination * p.available)
                .sum();
    }

    @Override
    public ConsumeSummary consumeUpTo(ServerPlayer player, long targetMinor) {
        SpentMintsSavedData mintData = SpentMintsSavedData.get(player.getServer());
        List<PlannedStack> planned = planValidStacks(player);
        int itemsConsumed = consumeCoinsForAmount(mintData, planned, targetMinor);
        long creditedMinor = 0L;
        for (PlannedStack p : planned) {
            creditedMinor += p.denomination * p.taken;
        }
        // No refundable stacks: the ledger consume is irreversible by design.
        return new ConsumeSummary(creditedMinor, itemsConsumed, List.of());
    }

    @Override
    public ExactPayment consumeExact(ServerPlayer player, long targetMinor) {
        if (targetMinor < 0L) {
            return ExactPayment.failed();
        }
        if (targetMinor == 0L) {
            return new ExactPayment(true, 0L, 0, List.of());
        }

        List<PlannedStack> planned = planValidStacks(player);
        List<Long> values = new ArrayList<>();
        List<Integer> available = new ArrayList<>();
        for (PlannedStack stack : planned) {
            int index = values.indexOf(stack.denomination);
            if (index < 0) {
                values.add(stack.denomination);
                available.add(stack.available);
            } else {
                available.set(index, available.get(index) + stack.available);
            }
        }
        long[] valueArray = new long[values.size()];
        int[] availableArray = new int[available.size()];
        for (int i = 0; i < values.size(); i++) {
            valueArray[i] = values.get(i);
            availableArray[i] = available.get(i);
        }
        long[] selected = CurrencyMath.exactBoundedCounts(targetMinor, valueArray, availableArray);
        if (selected == null) {
            return ExactPayment.failed();
        }

        SpentMintsSavedData mintData = SpentMintsSavedData.get(player.getServer());
        List<PaymentPortion> portions = new ArrayList<>();
        int itemsConsumed = 0;
        for (int i = 0; i < selected.length; i++) {
            int remaining = Math.toIntExact(selected[i]);
            int consumedAtValue = 0;
            for (PlannedStack stack : planned) {
                if (remaining <= 0) {
                    break;
                }
                if (stack.denomination != valueArray[i]) {
                    continue;
                }
                int requested = Math.min(remaining, stack.available);
                SpentMintsSavedData.ConsumeResult result = mintData.consume(
                        stack.mintId, requested, stack.denomination, stack.authorizedCount);
                if (result.accepted() <= 0) {
                    continue;
                }
                stack.stack.shrink(result.accepted());
                remaining -= result.accepted();
                consumedAtValue += result.accepted();
                itemsConsumed += result.accepted();
            }
            if (consumedAtValue > 0) {
                portions.add(new PaymentPortion(valueArray[i], consumedAtValue, ItemStack.EMPTY));
            }
            if (remaining > 0) {
                ExactPayment partial = new ExactPayment(true, 0L, itemsConsumed, List.copyOf(portions));
                refundExact(player, partial);
                return ExactPayment.failed();
            }
        }
        player.containerMenu.broadcastChanges();
        return new ExactPayment(true, targetMinor, itemsConsumed, List.copyOf(portions));
    }

    @Override
    public void refundExact(ServerPlayer player, ExactPayment payment) {
        for (PaymentPortion portion : payment.portions()) {
            int remaining = portion.count();
            int maxStack = Math.max(1, ModItems.MONEY_ITEM.get().getMaxStackSize());
            while (remaining > 0) {
                int count = Math.min(remaining, maxStack);
                ItemStack replacement = mint(player, portion.valueMinor(), count);
                player.getInventory().placeItemBackInInventory(replacement);
                remaining -= count;
            }
        }
        player.containerMenu.broadcastChanges();
    }

    // -------------------------------------------------------------------------
    // Helpers (moved verbatim from DepositCommand)
    // -------------------------------------------------------------------------

    /**
     * Collects valid coin stacks and allocates each a per-stack "available" count
     * capped by the mint record's remaining balance. When multiple stacks share
     * a mint ID, earlier stacks get first dibs on the remaining balance.
     */
    private static List<PlannedStack> planValidStacks(ServerPlayer player) {
        Item coinItem = ModItems.MONEY_ITEM.get();
        SpentMintsSavedData mintData = SpentMintsSavedData.get(player.getServer());
        List<PlannedStack> result = new ArrayList<>();
        java.util.Map<String, Integer> budget = new java.util.HashMap<>();
        for (ItemStack stack : allCoinContainers(player)) {
            if (stack.getItem() != coinItem) continue;
            MoneyValidationResult validation = MoneyValidationService.validate(stack);
            if (!validation.valid()) continue;
            CompoundTag moneyData = stack.getTag().getCompound(MoneyNbtKeys.ROOT);
            String mintId = moneyData.getString(MoneyNbtKeys.MINT_ID);
            long denomination = moneyData.getLong(MoneyNbtKeys.DENOMINATION);

            int remaining = budget.computeIfAbsent(mintId, mintData::remainingCount);
            if (remaining <= 0) continue;
            int available = Math.min(stack.getCount(), remaining);
            if (available <= 0) continue;
            budget.put(mintId, remaining - available);
            result.add(new PlannedStack(stack, mintId, denomination, validation.authorizedCount(), available));
        }
        // Sort by denomination descending so we consume largest bills first.
        result.sort(Comparator.comparingLong((PlannedStack p) -> p.denomination).reversed());
        return result;
    }

    /**
     * Greedily consumes coins to cover {@code targetMinor}. Mutates {@link PlannedStack#taken}
     * and physically shrinks the underlying stacks + decrements the mint ledger.
     * Returns the total number of coins actually consumed.
     */
    private static int consumeCoinsForAmount(SpentMintsSavedData mintData,
                                             List<PlannedStack> planned, long targetMinor) {
        long remaining = targetMinor;
        int totalAccepted = 0;
        for (PlannedStack p : planned) {
            if (remaining <= 0L) break;
            if (p.available <= 0) continue;

            long coinsNeeded = (remaining + p.denomination - 1L) / p.denomination;
            int toTake = (int) Math.min(coinsNeeded, p.available);
            long valueRemoved = p.denomination * toTake;
            if (valueRemoved > remaining && toTake > 1) {
                toTake = (int) (remaining / p.denomination);
                valueRemoved = p.denomination * toTake;
            }
            if (toTake <= 0) continue;

            SpentMintsSavedData.ConsumeResult r = mintData.consume(p.mintId, toTake, p.denomination, p.authorizedCount);
            if (r.accepted() <= 0) {
                continue; // Mint race: already drained by another stack this tick.
            }
            p.stack.shrink(r.accepted());
            p.taken += r.accepted();
            remaining -= p.denomination * r.accepted();
            totalAccepted += r.accepted();
        }
        return totalAccepted;
    }

    private static List<ItemStack> allCoinContainers(ServerPlayer player) {
        return java.util.stream.Stream
                .concat(player.getInventory().items.stream(), player.getInventory().offhand.stream())
                .toList();
    }

    private static final class PlannedStack {
        final ItemStack stack;
        final String mintId;
        final long denomination;
        final int authorizedCount;
        final int available;
        int taken;

        PlannedStack(ItemStack stack, String mintId, long denomination, int authorizedCount, int available) {
            this.stack = stack;
            this.mintId = mintId;
            this.denomination = denomination;
            this.authorizedCount = authorizedCount;
            this.available = available;
        }
    }
}
