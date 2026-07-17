package com.enviouse.futureshops.money;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmCurrencyCatalogTest {
    private static final String REVISION = "0".repeat(64);

    @Test
    void signatureChangesForEveryCatalogSecurityInput() {
        List<AtmCurrencyCatalog.Denomination> denominations = List.of(
                new AtmCurrencyCatalog.Denomination(
                        0, "futureshops:money", 100L, 64),
                new AtmCurrencyCatalog.Denomination(
                        1, "futureshops:money", 25L, 32));
        AtmCurrencyCatalog baseline = protectedCatalog(
                "Coins", 2, REVISION, denominations);

        assertNotEquals(baseline.signature(), protectedCatalog(
                "Credits", 2, REVISION, denominations).signature());
        assertNotEquals(baseline.signature(), protectedCatalog(
                "Coins", 3, REVISION, denominations).signature());
        assertNotEquals(baseline.signature(), protectedCatalog(
                "Coins", 2, "1".repeat(64), denominations).signature());
        assertNotEquals(baseline.signature(), protectedCatalog(
                "Coins", 2, REVISION, List.of(
                        denominations.get(0),
                        new AtmCurrencyCatalog.Denomination(
                                1, "futureshops:money", 20L, 32)))
                .signature());
        assertNotEquals(baseline.signature(), protectedCatalog(
                "Coins", 2, REVISION, List.of(
                        denominations.get(0),
                        new AtmCurrencyCatalog.Denomination(
                                1, "futureshops:money", 25L, 64)))
                .signature());
    }

    @Test
    void manualPlanValidatesExactShapeBoundsAndCheckedArithmetic() {
        AtmCurrencyCatalog catalog = protectedCatalog(
                "Coins", 2, REVISION, List.of(
                        new AtmCurrencyCatalog.Denomination(
                                0, "futureshops:money", 100L, 64),
                        new AtmCurrencyCatalog.Denomination(
                                1, "futureshops:money", 25L, 64)));

        AtmSelectionPlan valid = catalog.plan(List.of(2, 4));
        assertTrue(valid.valid());
        assertEquals(300L, valid.amountMinorUnits());
        assertEquals(6, valid.billCount());
        assertEquals(2, valid.selections().size());

        assertEquals(AtmSelectionPlan.Failure.INVALID_PLAN,
                catalog.plan(List.of(1)).failure());
        assertEquals(AtmSelectionPlan.Failure.INVALID_PLAN,
                catalog.plan(List.of(-1, 0)).failure());
        assertEquals(AtmSelectionPlan.Failure.INVALID_AMOUNT,
                catalog.plan(List.of(0, 0)).failure());
        assertEquals(AtmSelectionPlan.Failure.INVALID_PLAN,
                catalog.plan(List.of(4096, 1)).failure());

        AtmCurrencyCatalog unstackable = AtmCurrencyCatalog.create(
                "custom", AtmCurrencyRoute.FOREIGN_UNPROTECTED,
                "Coins", 2, REVISION, List.of(
                        new AtmCurrencyCatalog.Denomination(
                                0, "example:coin", 1L, 1)));
        assertTrue(unstackable.plan(List.of(
                AtmCurrencyCatalog.MAXIMUM_CLAIM_STACKS)).valid());
        assertEquals(AtmSelectionPlan.Failure.INVALID_PLAN,
                unstackable.plan(List.of(
                        AtmCurrencyCatalog.MAXIMUM_CLAIM_STACKS + 1))
                        .failure());

        AtmCurrencyCatalog overflowing = protectedCatalog(
                "Coins", 2, REVISION, List.of(
                        new AtmCurrencyCatalog.Denomination(
                                0, "futureshops:money",
                                Long.MAX_VALUE, 64)));
        assertEquals(AtmSelectionPlan.Failure.INVALID_PLAN,
                overflowing.plan(List.of(2)).failure());
    }

    @Test
    void constructorRejectsTamperedSignaturesAndForeignDuplicateItems() {
        AtmCurrencyCatalog valid = AtmCurrencyCatalog.create(
                "custom", AtmCurrencyRoute.FOREIGN_UNPROTECTED,
                "Coins", 2, REVISION, List.of(
                        new AtmCurrencyCatalog.Denomination(
                                0, "example:coin", 25L, 64)));
        assertFalse(valid.signature().isBlank());
        assertThrows(IllegalArgumentException.class,
                () -> new AtmCurrencyCatalog(
                        valid.schemaVersion(), valid.providerId(), valid.route(),
                        valid.currencyName(), valid.decimalPlaces(),
                        valid.protectedConfigurationRevision(),
                        valid.denominations(), "f".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> AtmCurrencyCatalog.create(
                        "custom", AtmCurrencyRoute.FOREIGN_UNPROTECTED,
                        "Coins", 2, REVISION, List.of(
                                new AtmCurrencyCatalog.Denomination(
                                        0, "example:coin", 100L, 64),
                                new AtmCurrencyCatalog.Denomination(
                                        1, "example:coin", 25L, 64))));
        assertThrows(IllegalArgumentException.class,
                () -> AtmCurrencyCatalog.create(
                        "custom", AtmCurrencyRoute.FOREIGN_UNPROTECTED,
                        "Coins", 2, REVISION, List.of(
                                new AtmCurrencyCatalog.Denomination(
                                        0, "futureshops:money", 25L, 64))));

        assertThrows(IllegalArgumentException.class, () -> {
            List<AtmCurrencyCatalog.Denomination> tooMany =
                    new ArrayList<>();
            for (int index = 0;
                 index <= AtmCurrencyCatalog.MAXIMUM_DENOMINATIONS;
                 index++) {
                tooMany.add(new AtmCurrencyCatalog.Denomination(
                        index, "example:coin" + index, 1L, 64));
            }
            AtmCurrencyCatalog.create(
                    "custom", AtmCurrencyRoute.FOREIGN_UNPROTECTED,
                    "Coins", 2, REVISION, tooMany);
        });
    }

    private static AtmCurrencyCatalog protectedCatalog(
            String name,
            int decimals,
            String revision,
            List<AtmCurrencyCatalog.Denomination> denominations
    ) {
        return AtmCurrencyCatalog.create(
                "futureshops", AtmCurrencyRoute.PROTECTED_ESCROW,
                name, decimals, revision, denominations);
    }
}
