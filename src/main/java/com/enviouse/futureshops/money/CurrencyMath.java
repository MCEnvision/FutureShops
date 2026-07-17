package com.enviouse.futureshops.money;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Pure denomination arithmetic + config-string parsing for the physical
 * currency layer. Deliberately free of Minecraft classes so it stays unit
 * testable without registry bootstrap.
 */
public final class CurrencyMath {

    /** One parsed "modid:item=value" config entry. */
    public record ItemValue(String itemId, long valueMinor) {
    }

    /** {@code count} items of the denomination at {@code denominationIndex}. */
    public record Portion(int denominationIndex, int count) {
    }

    /** Greedy breakdown result; {@code remainderMinor > 0} means not representable. */
    public record BreakResult(
            List<Portion> portions,
            long remainderMinor,
            boolean limitExceeded
    ) {
        public BreakResult {
            portions = List.copyOf(portions);
            if (remainderMinor < 0L) {
                throw new IllegalArgumentException(
                        "Currency remainder is invalid");
            }
        }
    }

    public static final int DEFAULT_BREAKDOWN_ITEM_LIMIT = 4096;

    private CurrencyMath() {
    }

    /**
     * Parses config entries of the form {@code "modid:item=value_in_minor_units"}.
     * Malformed entries are reported through {@code onError} and skipped.
     */
    public static List<ItemValue> parseItemValueList(List<? extends String> entries, Consumer<String> onError) {
        List<ItemValue> out = new ArrayList<>();
        if (entries == null) {
            return out;
        }
        for (String raw : entries) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String entry = raw.trim();
            int eq = entry.lastIndexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                onError.accept(entry);
                continue;
            }
            String itemId = entry.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            long value;
            try {
                value = Long.parseLong(entry.substring(eq + 1).trim());
            } catch (NumberFormatException ex) {
                onError.accept(entry);
                continue;
            }
            if (value <= 0L || itemId.indexOf(':') <= 0 || itemId.indexOf(':') == itemId.length() - 1) {
                onError.accept(entry);
                continue;
            }
            out.add(new ItemValue(itemId, value));
        }
        return out;
    }

    /**
     * Largest-first breakdown of {@code amountMinor} into the given denomination
     * values (must be sorted descending), splitting each denomination into
     * portions of at most {@code maxStacks[i]} items so every portion fits one
     * inventory slot. Portions reference denominations by index.
     *
     * <p>Greedy is exact for canonical sets (each denomination a multiple of the
     * next, like the built-in bills and the apocalypsenow preset). For custom
     * non-canonical sets where greedy strands a remainder (e.g. 100 over
     * {70, 25}), a bounded change-making search finds an exact combination
     * before the amount is declared not representable.</p>
     */
    public static BreakResult breakIntoDenominations(long amountMinor, long[] denomValuesDesc, int[] maxStacks) {
        return breakIntoDenominations(amountMinor, denomValuesDesc,
                maxStacks, DEFAULT_BREAKDOWN_ITEM_LIMIT);
    }

    public static BreakResult breakIntoDenominations(
            long amountMinor,
            long[] denomValuesDesc,
            int[] maxStacks,
            int maximumItems
    ) {
        if (denomValuesDesc == null || maxStacks == null
                || denomValuesDesc.length != maxStacks.length
                || maximumItems <= 0) {
            throw new IllegalArgumentException(
                    "Currency breakdown inputs are invalid");
        }
        if (amountMinor <= 0L) {
            return new BreakResult(List.of(), 0L, false);
        }
        CountResult greedy = greedyCounts(amountMinor, denomValuesDesc);
        if (greedy.remainderMinor() == 0L) {
            return materialize(greedy.counts(), 0L, maxStacks,
                    maximumItems);
        }
        long[] exactCounts = exactChangeCounts(
                amountMinor, denomValuesDesc);
        if (exactCounts == null) {
            return materialize(greedy.counts(), greedy.remainderMinor(),
                    maxStacks, maximumItems);
        }
        return materialize(exactCounts, 0L, maxStacks, maximumItems);
    }

    private static BreakResult materialize(
            long[] counts,
            long remainderMinor,
            int[] maxStacks,
            int maximumItems
    ) {
        long totalItems = 0L;
        for (long count : counts) {
            if (count < 0L || count > maximumItems
                    || totalItems > maximumItems - count) {
                return new BreakResult(
                        List.of(), remainderMinor, true);
            }
            totalItems += count;
        }
        List<Portion> portions = new ArrayList<>();
        for (int i = 0; i < counts.length; i++) {
            long count = counts[i];
            int maxStack = Math.max(1, maxStacks[i]);
            while (count > 0L) {
                int batch = (int) Math.min(count, maxStack);
                portions.add(new Portion(i, batch));
                count -= batch;
            }
        }
        return new BreakResult(portions, remainderMinor, false);
    }

    private static CountResult greedyCounts(
            long amountMinor,
            long[] denomValuesDesc
    ) {
        long[] counts = new long[denomValuesDesc.length];
        long remaining = amountMinor;
        for (int i = 0; i < denomValuesDesc.length && remaining > 0L; i++) {
            long denom = denomValuesDesc[i];
            if (denom <= 0L) {
                continue;
            }
            long count = remaining / denom;
            if (count <= 0L) {
                continue;
            }
            remaining -= Math.multiplyExact(count, denom);
            counts[i] = count;
        }
        return new CountResult(counts, remaining);
    }

    /** Search-space bound for the exact change-making fallback (gcd-reduced units). */
    private static final long EXACT_CHANGE_MAX_UNITS = 100_000L;
    private static final long BOUNDED_CHANGE_MAX_UNITS = 5_000_000L;
    private static final int BOUNDED_CHANGE_MAX_ITEMS = 4096;

    /**
     * Exact change-making: per-denomination counts summing to {@code amountMinor},
     * or null when not representable (or the gcd-reduced amount exceeds the
     * search bound). Denomination-indexed DP over gcd-reduced units.
     */
    private static long[] exactChangeCounts(
            long amountMinor,
            long[] denomValuesDesc
    ) {
        if (amountMinor <= 0L || denomValuesDesc.length == 0) {
            return null;
        }
        long gcd = 0L;
        for (long value : denomValuesDesc) {
            if (value > 0L) {
                gcd = gcd(gcd, value);
            }
        }
        if (gcd <= 0L || amountMinor % gcd != 0L) {
            return null;
        }
        long units = amountMinor / gcd;
        if (units > EXACT_CHANGE_MAX_UNITS) {
            return null;
        }
        int target = (int) units;
        int[] choice = new int[target + 1];
        int[] itemCounts = new int[target + 1];
        java.util.Arrays.fill(choice, -1);
        java.util.Arrays.fill(itemCounts, Integer.MAX_VALUE);
        choice[0] = denomValuesDesc.length;
        itemCounts[0] = 0;
        for (int u = 1; u <= target; u++) {
            for (int i = 0; i < denomValuesDesc.length; i++) {
                long v = denomValuesDesc[i] / gcd;
                if (denomValuesDesc[i] > 0L && v <= u
                        && choice[u - (int) v] != -1
                        && itemCounts[u - (int) v] + 1
                        < itemCounts[u]) {
                    choice[u] = i;
                    itemCounts[u] = itemCounts[u - (int) v] + 1;
                }
            }
        }
        if (choice[target] == -1) {
            return null;
        }
        long[] counts = new long[denomValuesDesc.length];
        for (int u = target; u > 0; ) {
            int i = choice[u];
            counts[i]++;
            u -= (int) (denomValuesDesc[i] / gcd);
        }
        return counts;
    }

    private record CountResult(long[] counts, long remainderMinor) {
    }

    public static long[] exactBoundedCounts(long amountMinor, long[] denominationValues,
                                            int[] availableCounts) {
        if (amountMinor < 0L || denominationValues == null
                || availableCounts == null
                || denominationValues.length == 0
                || denominationValues.length != availableCounts.length) {
            return null;
        }
        if (amountMinor == 0L) {
            return new long[denominationValues.length];
        }

        long[] greedy = new long[denominationValues.length];
        long remaining = amountMinor;
        for (int i = 0; i < denominationValues.length; i++) {
            long value = denominationValues[i];
            if (value <= 0L || availableCounts[i] <= 0) {
                continue;
            }
            long count = Math.min((long) availableCounts[i], remaining / value);
            greedy[i] = count;
            remaining -= count * value;
        }
        if (remaining == 0L) {
            return greedy;
        }

        long common = 0L;
        int totalAvailable = 0;
        for (int i = 0; i < denominationValues.length; i++) {
            if (availableCounts[i] < 0) {
                return null;
            }
            if (denominationValues[i] > 0L && availableCounts[i] > 0) {
                common = gcd(common, denominationValues[i]);
                try {
                    totalAvailable = Math.addExact(
                            totalAvailable, availableCounts[i]);
                } catch (ArithmeticException exception) {
                    return null;
                }
            }
        }
        if (common <= 0L || amountMinor % common != 0L
                || totalAvailable > BOUNDED_CHANGE_MAX_ITEMS) {
            return null;
        }
        long targetUnits = amountMinor / common;
        if (targetUnits > BOUNDED_CHANGE_MAX_UNITS) {
            return null;
        }

        record Group(int denominationIndex, int count, int units) {
        }
        List<Group> groups = new ArrayList<>();
        int target = (int) targetUnits;
        for (int i = 0; i < denominationValues.length; i++) {
            if (denominationValues[i] <= 0L || availableCounts[i] <= 0) {
                continue;
            }
            long unitValue = denominationValues[i] / common;
            int left = availableCounts[i];
            int batch = 1;
            while (left > 0) {
                int count = Math.min(batch, left);
                if (unitValue <= target / (long) count) {
                    groups.add(new Group(i, count,
                            Math.toIntExact(unitValue * count)));
                }
                left -= count;
                if (batch <= Integer.MAX_VALUE / 2) {
                    batch *= 2;
                }
            }
        }

        long[] reachable = new long[(target >>> 6) + 1];
        int[] previous = new int[target + 1];
        int[] chosenGroup = new int[target + 1];
        java.util.Arrays.fill(previous, -1);
        java.util.Arrays.fill(chosenGroup, -1);
        reachable[0] = 1L;
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            Group group = groups.get(groupIndex);
            addReachableGroup(reachable, target, group.units(),
                    previous, chosenGroup, groupIndex);
        }
        if ((reachable[target >>> 6]
                & (1L << (target & 63))) == 0L) {
            return null;
        }

        long[] counts = new long[denominationValues.length];
        for (int units = target; units > 0; units = previous[units]) {
            Group group = groups.get(chosenGroup[units]);
            counts[group.denominationIndex()] += group.count();
        }
        return counts;
    }

    private static void addReachableGroup(
            long[] reachable,
            int target,
            int weight,
            int[] previous,
            int[] chosenGroup,
            int groupIndex
    ) {
        int wordOffset = weight >>> 6;
        int bitOffset = weight & 63;
        int finalWord = target >>> 6;
        long finalMask = (target & 63) == 63
                ? -1L : (1L << ((target & 63) + 1)) - 1L;
        for (int destination = finalWord;
             destination >= wordOffset; destination--) {
            int source = destination - wordOffset;
            long shifted = reachable[source] << bitOffset;
            if (bitOffset != 0 && source > 0) {
                shifted |= reachable[source - 1] >>> (64 - bitOffset);
            }
            if (destination == finalWord) {
                shifted &= finalMask;
            }
            long added = shifted & ~reachable[destination];
            reachable[destination] |= shifted;
            while (added != 0L) {
                int bit = Long.numberOfTrailingZeros(added);
                int units = (destination << 6) + bit;
                previous[units] = units - weight;
                chosenGroup[units] = groupIndex;
                added &= added - 1L;
            }
        }
    }

    private static long gcd(long a, long b) {
        while (b != 0L) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }
}
