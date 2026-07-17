package com.enviouse.futureshops.money;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A foreign physical currency made of another mod's items, each worth a fixed
 * configured value (face value). There is no checksum or mint ledger — the
 * currency supply is whatever the source mod's loot and recipes make
 * obtainable, so anti-dupe protection is forfeited by design.
 *
 * <p>Deliberately tag-agnostic and it NEVER stamps NBT onto foreign items:
 * stamped stacks would stop merging with loot-obtained ones (vanilla NBT
 * stack-merge), which is worse than any bookkeeping it could buy us.</p>
 */
public final class ItemValueCurrencyAdapter implements PhysicalCurrencyAdapter {
    private final String id;
    private final List<Denomination> mintable;
    private final Map<Item, Long> acceptValues;

    /**
     * @param mintable     denominations handed out by /withdraw, sorted by value descending
     * @param acceptValues deposit value per item — mintable items plus accept-only items
     */
    public ItemValueCurrencyAdapter(String id, List<Denomination> mintable, Map<Item, Long> acceptValues) {
        this.id = id;
        this.mintable = List.copyOf(mintable);
        this.acceptValues = Map.copyOf(acceptValues);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public List<Denomination> denominations() {
        return mintable;
    }

    @Override
    public long unitValueMinor(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0L;
        }
        return acceptValues.getOrDefault(stack.getItem(), 0L);
    }

    @Override
    public ItemStack mint(ServerPlayer player, long valueMinor, int count) {
        for (Denomination denom : mintable) {
            if (denom.valueMinor() == valueMinor) {
                return new ItemStack(denom.item(), count);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack mint(ServerPlayer player, Denomination denomination, int count) {
        if (count <= 0 || !mintable.contains(denomination)) {
            return ItemStack.EMPTY;
        }
        // Deliberately plain: no FutureShops tag, checksum, mint id, or ledger.
        return new ItemStack(denomination.item(), count);
    }

    @Override
    public int destroyCounterfeit(ServerPlayer player) {
        return 0;
    }

    @Override
    public String acceptedItemsSummary(int decimalPlaces) {
        return acceptValues.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(e.getKey())
                        + "=" + com.enviouse.futureshops.command.EconomyCommandUtil
                                .formatMinorUnits(e.getValue(), decimalPlaces))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    @Override
    public long availableValueMinor(ServerPlayer player) {
        long total = 0L;
        for (ItemStack stack : currencyStacks(player)) {
            total += unitValueMinor(stack) * stack.getCount();
        }
        return total;
    }

    @Override
    public ConsumeSummary consumeUpTo(ServerPlayer player, long targetMinor) {
        // Largest units first, and never consume an item worth more than what is
        // still owed — foreign deposits never overshoot the requested amount.
        List<ItemStack> stacks = new ArrayList<>(currencyStacks(player));
        stacks.sort(Comparator.comparingLong(this::unitValueMinor).reversed());

        long remaining = Math.max(0L, targetMinor);
        long credited = 0L;
        int itemsConsumed = 0;
        List<ItemStack> refundable = new ArrayList<>();
        for (ItemStack stack : stacks) {
            long unit = unitValueMinor(stack);
            if (unit <= 0L || unit > remaining) {
                continue;
            }
            int toTake = (int) Math.min(stack.getCount(), remaining / unit);
            if (toTake <= 0) {
                continue;
            }
            // Copy taken BEFORE shrink: preserves the consumed stack's item AND tag
            // (getItem() on an emptied stack returns AIR, which would void the refund).
            refundable.add(stack.copyWithCount(toTake));
            stack.shrink(toTake);
            credited += unit * toTake;
            remaining -= unit * toTake;
            itemsConsumed += toTake;
            if (remaining <= 0L) {
                break;
            }
        }
        return new ConsumeSummary(credited, itemsConsumed, refundable);
    }

    private List<ItemStack> currencyStacks(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        java.util.stream.Stream
                .concat(player.getInventory().items.stream(), player.getInventory().offhand.stream())
                .filter(stack -> unitValueMinor(stack) > 0L)
                .forEach(out::add);
        return out;
    }
}
