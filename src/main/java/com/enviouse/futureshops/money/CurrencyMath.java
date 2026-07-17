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
    public record BreakResult(List<Portion> portions, long remainderMinor) {
    }

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
        BreakResult greedy = greedyBreak(amountMinor, denomValuesDesc, maxStacks);
        if (greedy.remainderMinor() == 0L) {
            return greedy;
        }
        long[] exactCounts = exactChangeCounts(amountMinor, denomValuesDesc);
        if (exactCounts == null) {
            return greedy;
        }
        List<Portion> portions = new ArrayList<>();
        for (int i = 0; i < exactCounts.length; i++) {
            long count = exactCounts[i];
            int maxStack = Math.max(1, maxStacks[i]);
            while (count > 0L) {
                int batch = (int) Math.min(count, maxStack);
                portions.add(new Portion(i, batch));
                count -= batch;
            }
        }
        return new BreakResult(portions, 0L);
    }

    private static BreakResult greedyBreak(long amountMinor, long[] denomValuesDesc, int[] maxStacks) {
        List<Portion> portions = new ArrayList<>();
        long remaining = Math.max(0L, amountMinor);
        for (int i = 0; i < denomValuesDesc.length && remaining > 0L; i++) {
            long denom = denomValuesDesc[i];
            if (denom <= 0L) {
                continue;
            }
            long count = remaining / denom;
            if (count <= 0L) {
                continue;
            }
            remaining -= count * denom;
            int maxStack = Math.max(1, maxStacks[i]);
            while (count > 0L) {
                int batch = (int) Math.min(count, maxStack);
                portions.add(new Portion(i, batch));
                count -= batch;
            }
        }
        return new BreakResult(portions, remaining);
    }

    /** Search-space bound for the exact change-making fallback (gcd-reduced units). */
    private static final long EXACT_CHANGE_MAX_UNITS = 1_000_000L;

    /**
     * Exact change-making: per-denomination counts summing to {@code amountMinor},
     * or null when not representable (or the gcd-reduced amount exceeds the
     * search bound). Denomination-indexed DP over gcd-reduced units.
     */
    private static long[] exactChangeCounts(long amountMinor, long[] denomValuesDesc) {
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
        // choice[u] = denomination index used to reach u; -1 = unreachable
        int[] choice = new int[target + 1];
        java.util.Arrays.fill(choice, -1);
        choice[0] = denomValuesDesc.length; // sentinel: reachable start
        for (int u = 1; u <= target; u++) {
            for (int i = 0; i < denomValuesDesc.length; i++) {
                long v = denomValuesDesc[i] / gcd;
                if (denomValuesDesc[i] > 0L && v <= u && choice[u - (int) v] != -1) {
                    choice[u] = i;
                    break;
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

    public static long[] exactBoundedCounts(long amountMinor, long[] denominationValues,
                                            int[] availableCounts) {
        if (amountMinor < 0L || denominationValues.length == 0
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
        for (int i = 0; i < denominationValues.length; i++) {
            if (denominationValues[i] > 0L && availableCounts[i] > 0) {
                common = gcd(common, denominationValues[i]);
            }
        }
        if (common <= 0L || amountMinor % common != 0L) {
            return null;
        }
        long targetUnits = amountMinor / common;
        if (targetUnits > EXACT_CHANGE_MAX_UNITS) {
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
                long weight = unitValue * count;
                if (weight <= target) {
                    groups.add(new Group(i, count, (int) weight));
                }
                left -= count;
                if (batch <= Integer.MAX_VALUE / 2) {
                    batch *= 2;
                }
            }
        }

        boolean[] reachable = new boolean[target + 1];
        int[] previous = new int[target + 1];
        int[] chosenGroup = new int[target + 1];
        java.util.Arrays.fill(previous, -1);
        java.util.Arrays.fill(chosenGroup, -1);
        reachable[0] = true;
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            Group group = groups.get(groupIndex);
            for (int units = target; units >= group.units(); units--) {
                if (!reachable[units] && reachable[units - group.units()]) {
                    reachable[units] = true;
                    previous[units] = units - group.units();
                    chosenGroup[units] = groupIndex;
                }
            }
        }
        if (!reachable[target]) {
            return null;
        }

        long[] counts = new long[denominationValues.length];
        for (int units = target; units > 0; units = previous[units]) {
            Group group = groups.get(chosenGroup[units]);
            counts[group.denominationIndex()] += group.count();
        }
        return counts;
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
