package com.enviouse.futureshops.money;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

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
    private final String depositConfigurationSignature;

    /**
     * @param mintable     denominations handed out by /withdraw, sorted by value descending
     * @param acceptValues deposit value per item — mintable items plus accept-only items
     */
    public ItemValueCurrencyAdapter(String id, List<Denomination> mintable, Map<Item, Long> acceptValues) {
        this.id = id;
        this.mintable = List.copyOf(mintable);
        this.acceptValues = Map.copyOf(acceptValues);
        this.depositConfigurationSignature = signature(id,
                this.acceptValues);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String depositConfigurationSignature() {
        return depositConfigurationSignature;
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

    @Override
    public ExactPayment consumeExact(ServerPlayer player, long targetMinor) {
        if (targetMinor < 0L) {
            return ExactPayment.failed();
        }
        if (targetMinor == 0L) {
            return new ExactPayment(true, 0L, 0, List.of());
        }

        List<ItemStack> stacks = new ArrayList<>(currencyStacks(player));
        stacks.sort(Comparator.comparingLong(this::unitValueMinor).reversed());
        List<Long> values = new ArrayList<>();
        List<Integer> available = new ArrayList<>();
        for (ItemStack stack : stacks) {
            long value = unitValueMinor(stack);
            int index = values.indexOf(value);
            if (index < 0) {
                values.add(value);
                available.add(stack.getCount());
            } else {
                available.set(index, available.get(index) + stack.getCount());
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

        int itemsConsumed = 0;
        List<PaymentPortion> portions = new ArrayList<>();
        for (int i = 0; i < selected.length; i++) {
            int remaining = Math.toIntExact(selected[i]);
            if (remaining <= 0) {
                continue;
            }
            for (ItemStack stack : stacks) {
                if (remaining <= 0) {
                    break;
                }
                if (unitValueMinor(stack) != valueArray[i]) {
                    continue;
                }
                int taken = Math.min(remaining, stack.getCount());
                if (taken <= 0) {
                    continue;
                }
                ItemStack refundable = stack.copyWithCount(taken);
                stack.shrink(taken);
                portions.add(new PaymentPortion(valueArray[i], taken, refundable));
                itemsConsumed += taken;
                remaining -= taken;
            }
        }
        return new ExactPayment(true, targetMinor, itemsConsumed, List.copyOf(portions));
    }

    @Override
    public void refundExact(ServerPlayer player, ExactPayment payment) {
        for (PaymentPortion portion : payment.portions()) {
            ItemStack stack = portion.refundableStack().copy();
            if (!stack.isEmpty()) {
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
        player.containerMenu.broadcastChanges();
    }

    private List<ItemStack> currencyStacks(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        java.util.stream.Stream
                .concat(player.getInventory().items.stream(), player.getInventory().offhand.stream())
                .filter(stack -> unitValueMinor(stack) > 0L)
                .forEach(out::add);
        return out;
    }

    private static String signature(String provider, Map<Item, Long> values) {
        String material = values.entrySet().stream()
                .map(entry -> Map.entry(
                        String.valueOf(net.minecraftforge.registries
                                .ForgeRegistries.ITEMS.getKey(
                                        entry.getKey())),
                        entry.getValue()))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(",",
                        provider + "|", "|"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Foreign currency signature algorithm is unavailable",
                    exception);
        }
    }
}
