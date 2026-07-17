package com.enviouse.futureshops.money;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the pure denomination arithmetic behind the pluggable physical-currency
 * layer (currency.provider config): config-entry parsing and the greedy
 * largest-first withdrawal breakdown, including stack splitting and the
 * not-representable remainder that /withdraw rejects.
 */
class CurrencyMathTest {

    // ── parseItemValueList ──────────────────────────────────────────────

    @Test
    void parsesWellFormedEntriesAndNormalizesCase() {
        List<String> errors = new ArrayList<>();
        List<CurrencyMath.ItemValue> parsed = CurrencyMath.parseItemValueList(
                List.of("Apocalypsenow:Money=100", " apocalypsenow:coins = 25 "), errors::add);

        assertEquals(List.of(
                new CurrencyMath.ItemValue("apocalypsenow:money", 100L),
                new CurrencyMath.ItemValue("apocalypsenow:coins", 25L)), parsed);
        assertTrue(errors.isEmpty(), "well-formed entries must not report errors");
    }

    @Test
    void reportsAndSkipsMalformedEntries() {
        List<String> errors = new ArrayList<>();
        List<CurrencyMath.ItemValue> parsed = CurrencyMath.parseItemValueList(
                List.of(
                        "no_equals_sign",          // missing =
                        "modid:item=",             // missing value
                        "=100",                    // missing item id
                        "modid:item=abc",          // non-numeric value
                        "modid:item=0",            // non-positive value
                        "modid:item=-5",           // negative value
                        "no_namespace=100",        // item id without ':'
                        "",                        // blank: silently ignored, not an error
                        "modid:item=100"           // the one valid entry
                ), errors::add);

        assertEquals(List.of(new CurrencyMath.ItemValue("modid:item", 100L)), parsed);
        assertEquals(7, errors.size(), "each malformed (non-blank) entry must be reported once");
    }

    // ── breakIntoDenominations ──────────────────────────────────────────

    private static final long[] INTERNAL_BILLS = {100_000L, 10_000L, 5_000L, 2_000L, 1_000L, 500L, 100L};

    private static int[] stacksOf64(int length) {
        int[] out = new int[length];
        java.util.Arrays.fill(out, 64);
        return out;
    }

    @Test
    void breaksExactAmountGreedilyLargestFirst() {
        // $1,234 → 1×$1000 + 2×$100 + 1×$20 + 1×$10 + 4×$1 (fewest bills)
        CurrencyMath.BreakResult result = CurrencyMath.breakIntoDenominations(
                123_400L, INTERNAL_BILLS, stacksOf64(INTERNAL_BILLS.length));

        assertEquals(0L, result.remainderMinor());
        assertEquals(List.of(
                new CurrencyMath.Portion(0, 1),
                new CurrencyMath.Portion(1, 2),
                new CurrencyMath.Portion(3, 1),
                new CurrencyMath.Portion(4, 1),
                new CurrencyMath.Portion(6, 4)), result.portions());
    }

    @Test
    void reportsRemainderWhenNotRepresentable() {
        // 30 minor units with a 25-minor smallest denomination → 5 left over
        CurrencyMath.BreakResult result = CurrencyMath.breakIntoDenominations(
                30L, new long[]{100L, 25L}, new int[]{64, 64});

        assertEquals(List.of(new CurrencyMath.Portion(1, 1)), result.portions());
        assertEquals(5L, result.remainderMinor());
    }

    @Test
    void splitsLargeCountsIntoMaxStackPortions() {
        // 130 × $1 with stack size 64 → 64 + 64 + 2
        CurrencyMath.BreakResult result = CurrencyMath.breakIntoDenominations(
                13_000L, new long[]{100L}, new int[]{64});

        assertEquals(0L, result.remainderMinor());
        assertEquals(List.of(
                new CurrencyMath.Portion(0, 64),
                new CurrencyMath.Portion(0, 64),
                new CurrencyMath.Portion(0, 2)), result.portions());
    }

    @Test
    void nonCanonicalSetsFallBackToExactChangeMaking() {
        // Greedy fails on 100 over {70, 25} (70 + 25 strands 5), but 4×25 is exact.
        CurrencyMath.BreakResult result = CurrencyMath.breakIntoDenominations(
                100L, new long[]{70L, 25L}, new int[]{64, 64});

        assertEquals(0L, result.remainderMinor());
        assertEquals(List.of(new CurrencyMath.Portion(1, 4)), result.portions());

        // {50, 30} over 90 → 3×30 (greedy would strand 40 after taking one 50)
        CurrencyMath.BreakResult result2 = CurrencyMath.breakIntoDenominations(
                90L, new long[]{50L, 30L}, new int[]{64, 64});
        assertEquals(0L, result2.remainderMinor());
        assertEquals(List.of(new CurrencyMath.Portion(1, 3)), result2.portions());
    }

    @Test
    void zeroAndNegativeAmountsYieldNothing() {
        assertEquals(0L, CurrencyMath.breakIntoDenominations(0L, INTERNAL_BILLS, stacksOf64(INTERNAL_BILLS.length)).remainderMinor());
        assertTrue(CurrencyMath.breakIntoDenominations(0L, INTERNAL_BILLS, stacksOf64(INTERNAL_BILLS.length)).portions().isEmpty());
        assertTrue(CurrencyMath.breakIntoDenominations(-500L, INTERNAL_BILLS, stacksOf64(INTERNAL_BILLS.length)).portions().isEmpty());
    }

    @Test
    void hugeBreakdownsFailBeforeMaterializingItems() {
        CurrencyMath.BreakResult result =
                CurrencyMath.breakIntoDenominations(
                        Long.MAX_VALUE, new long[]{1L},
                        new int[]{64}, 4096);

        assertTrue(result.limitExceeded());
        assertTrue(result.portions().isEmpty());
    }

    @Test
    void exactFallbackHonorsTheTotalItemBudget() {
        CurrencyMath.BreakResult result =
                CurrencyMath.breakIntoDenominations(
                        100L, new long[]{70L, 25L},
                        new int[]{64, 64}, 3);

        assertTrue(result.limitExceeded());
        assertTrue(result.portions().isEmpty());
    }

    @Test
    void apocalypseNowPresetValuesAreArbitrageSafe() {
        // The apocalypsenow preset must keep 8 × coins >= 1 × money, otherwise the
        // mod's cointomoney recipe (8 coins + paper → 1 money) mints value from thin
        // air. Mirrors the preset constants in CurrencyManager.
        long money = 100L;
        long coins = 25L;
        assertTrue(8 * coins >= money, "8 coins must be worth at least 1 money or crafting prints money");
    }

    @Test
    void boundedExactSelectionNeverOverpays() {
        assertArrayEquals(new long[]{0L, 4L},
                CurrencyMath.exactBoundedCounts(100L,
                        new long[]{70L, 25L}, new int[]{1, 4}));
        assertNull(CurrencyMath.exactBoundedCounts(100L,
                new long[]{70L, 25L}, new int[]{1, 3}));
    }

    @Test
    void boundedExactSelectionHonorsInventoryCounts() {
        assertArrayEquals(new long[]{1L, 1L, 1L},
                CurrencyMath.exactBoundedCounts(160L,
                        new long[]{100L, 50L, 10L}, new int[]{1, 1, 6}));
        assertNull(CurrencyMath.exactBoundedCounts(160L,
                new long[]{100L, 50L, 10L}, new int[]{1, 0, 5}));
    }

    @Test
    void boundedExactSelectionHandlesLargeNoncanonicalTargets() {
        assertArrayEquals(new long[]{1000L, 500L},
                CurrencyMath.exactBoundedCounts(1_498_500L,
                        new long[]{1000L, 997L},
                        new int[]{2000, 2000}));
    }
}
