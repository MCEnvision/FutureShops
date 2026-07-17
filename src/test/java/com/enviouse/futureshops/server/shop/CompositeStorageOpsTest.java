package com.enviouse.futureshops.server.shop;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit coverage for the money-critical composite-storage algorithms that back the
 * multiple-storage-linking feature. These drive the REAL production routines in
 * {@link CompositeStorageOps} (the same code {@code PlayerShopBlockService} calls with
 * {@code IItemHandler}/adapter lambdas), using an in-memory fake list of storages so the
 * summation / all-or-nothing / spill / rollback logic is exercised without a live server.
 *
 * <p>The fake models one fungible item: each {@link FakeStore} holds an int of units and has
 * a capacity. {@code U} (an insertable/extracted unit) is an {@link Integer} count.
 */
public class CompositeStorageOpsTest {

    /** A single fake storage: a count of held units and a capacity. */
    static final class FakeStore {
        int contents;
        final int capacity;

        FakeStore(int contents, int capacity) {
            this.contents = contents;
            this.capacity = capacity;
        }

        int room() {
            return capacity - contents;
        }
    }

    // ── Lambdas mirroring the production per-storage operations ───────────────

    /** Best-effort extract: pulls up to `remaining`, mutates the store, returns the chunk pulled. */
    private static List<Integer> take(FakeStore s, int remaining) {
        int amount = Math.min(s.contents, remaining);
        if (amount <= 0) return List.of();
        s.contents -= amount;
        return new ArrayList<>(List.of(amount));
    }

    /** Rollback: hand a pulled chunk back to its origin store. */
    private static void reinsert(FakeStore s, Integer unit) {
        s.contents += unit;
    }

    /** Real spill-insert into one store: absorbs what fits, returns leftover (empty if all fit). */
    private static List<Integer> insertInto(FakeStore s, List<Integer> units) {
        int total = units.stream().mapToInt(Integer::intValue).sum();
        int fit = Math.max(0, Math.min(s.room(), total));
        s.contents += fit;
        int leftover = total - fit;
        return leftover > 0 ? new ArrayList<>(List.of(leftover)) : new ArrayList<>();
    }

    private static List<Integer> units(int... chunks) {
        List<Integer> list = new ArrayList<>();
        for (int c : chunks) list.add(c);
        return list;
    }

    // ── countAcross ──────────────────────────────────────────────────────────

    @Test
    void countAcrossSumsEveryStorage() {
        List<FakeStore> stores = List.of(new FakeStore(5, 64), new FakeStore(0, 64), new FakeStore(9, 64));
        assertEquals(14, CompositeStorageOps.countAcross(stores, s -> s.contents));
    }

    @Test
    void countAcrossSaturatesInsteadOfOverflowing() {
        List<FakeStore> stores = List.of(new FakeStore(Integer.MAX_VALUE, Integer.MAX_VALUE),
                new FakeStore(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, CompositeStorageOps.countAcross(stores, s -> s.contents),
                "summing two MAX_VALUE storages must saturate, not overflow to a negative");
    }

    @Test
    void countAcrossOfEmptyListIsZero() {
        assertEquals(0, CompositeStorageOps.countAcross(List.<FakeStore>of(), s -> s.contents));
    }

    // ── canExtractAcross ─────────────────────────────────────────────────────

    @Test
    void canExtractAcrossTrueWhenCompositeCovers() {
        List<FakeStore> stores = List.of(new FakeStore(3, 64), new FakeStore(4, 64));
        assertTrue(CompositeStorageOps.canExtractAcross(stores, 7, s -> s.contents));
        assertTrue(CompositeStorageOps.canExtractAcross(stores, 6, s -> s.contents));
    }

    @Test
    void canExtractAcrossFalseWhenShort() {
        List<FakeStore> stores = List.of(new FakeStore(3, 64), new FakeStore(2, 64));
        assertFalse(CompositeStorageOps.canExtractAcross(stores, 6, s -> s.contents));
    }

    @Test
    void canExtractAcrossZeroOrNegativeIsTrivial() {
        assertTrue(CompositeStorageOps.canExtractAcross(List.<FakeStore>of(), 0, s -> s.contents));
        assertTrue(CompositeStorageOps.canExtractAcross(List.<FakeStore>of(), -4, s -> s.contents));
    }

    // ── extractAcross (all-or-nothing) ───────────────────────────────────────

    @Test
    void extractAcrossPullsInOrderUntilSatisfied() {
        FakeStore a = new FakeStore(3, 64);
        FakeStore b = new FakeStore(10, 64);
        List<FakeStore> stores = List.of(a, b);

        List<Integer> got = CompositeStorageOps.extractAcross(
                stores, 5, CompositeStorageOpsTest::take, Integer::intValue, CompositeStorageOpsTest::reinsert);

        assertEquals(5, got.stream().mapToInt(Integer::intValue).sum(), "must extract exactly the requested total");
        assertEquals(0, a.contents, "first storage is drained first");
        assertEquals(8, b.contents, "remainder comes from the second storage");
    }

    @Test
    void extractAcrossIsAllOrNothingAndRestoresOnShortfall() {
        FakeStore a = new FakeStore(3, 64);
        FakeStore b = new FakeStore(1, 64);
        List<FakeStore> stores = List.of(a, b);

        List<Integer> got = CompositeStorageOps.extractAcross(
                stores, 5, CompositeStorageOpsTest::take, Integer::intValue, CompositeStorageOpsTest::reinsert);

        assertTrue(got.isEmpty(), "a composite shortfall extracts nothing");
        assertEquals(3, a.contents, "shortfall must roll back the first storage to its origin count");
        assertEquals(1, b.contents, "shortfall must roll back the second storage — no item loss");
    }

    @Test
    void extractAcrossExactlyDrainsWhenTotalEqualsCount() {
        FakeStore a = new FakeStore(2, 64);
        FakeStore b = new FakeStore(2, 64);
        List<FakeStore> stores = List.of(a, b);

        List<Integer> got = CompositeStorageOps.extractAcross(
                stores, 4, CompositeStorageOpsTest::take, Integer::intValue, CompositeStorageOpsTest::reinsert);

        assertEquals(4, got.stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, a.contents);
        assertEquals(0, b.contents);
    }

    @Test
    void extractAcrossZeroCountTakesNothing() {
        FakeStore a = new FakeStore(5, 64);
        List<Integer> got = CompositeStorageOps.extractAcross(
                List.of(a), 0, CompositeStorageOpsTest::take, Integer::intValue, CompositeStorageOpsTest::reinsert);
        assertTrue(got.isEmpty());
        assertEquals(5, a.contents, "a zero-count extract must not touch storage");
    }

    // ── spillInsert / insertAllAcross ────────────────────────────────────────

    @Test
    void insertSpillsFromFullStorageToTheNext() {
        FakeStore a = new FakeStore(60, 64); // room 4
        FakeStore b = new FakeStore(0, 64);  // room 64
        List<FakeStore> stores = List.of(a, b);

        boolean ok = CompositeStorageOps.insertAllAcross(stores, units(10), CompositeStorageOpsTest::insertInto);

        assertTrue(ok, "10 units fit across the two storages");
        assertEquals(64, a.contents, "first storage fills to capacity");
        assertEquals(6, b.contents, "the 6-unit overflow spills into the second storage");
    }

    @Test
    void insertReportsLeftoverWhenCompositeIsFull() {
        FakeStore a = new FakeStore(62, 64); // room 2
        FakeStore b = new FakeStore(63, 64); // room 1
        List<FakeStore> stores = List.of(a, b);

        List<Integer> leftover = CompositeStorageOps.spillInsert(stores, units(10), CompositeStorageOpsTest::insertInto);

        assertEquals(7, leftover.stream().mapToInt(Integer::intValue).sum(),
                "only 3 of 10 units fit (room 2 + room 1); 7 remain");
        assertEquals(64, a.contents);
        assertEquals(64, b.contents);
        assertFalse(CompositeStorageOps.insertAllAcross(
                        List.of(new FakeStore(62, 64), new FakeStore(63, 64)), units(10),
                        CompositeStorageOpsTest::insertInto),
                "insertAllAcross is false when the composite cannot absorb everything");
    }

    @Test
    void insertIntoSingleStorageIsByteIdenticalToOneElementComposite() {
        // The single-storage path is just the 1-element composite case.
        FakeStore single = new FakeStore(0, 64);
        boolean ok = CompositeStorageOps.insertAllAcross(List.of(single), units(50), CompositeStorageOpsTest::insertInto);
        assertTrue(ok);
        assertEquals(50, single.contents);
    }

    @Test
    void extractFromSingleStorageMatchesLegacyAllOrNothing() {
        // 1-element composite reproduces the legacy single-storage all-or-nothing contract.
        FakeStore single = new FakeStore(4, 64);
        List<Integer> shortfall = CompositeStorageOps.extractAcross(
                List.of(single), 10, CompositeStorageOpsTest::take, Integer::intValue, CompositeStorageOpsTest::reinsert);
        assertTrue(shortfall.isEmpty());
        assertEquals(4, single.contents, "shortfall leaves the single storage untouched");

        List<Integer> ok = CompositeStorageOps.extractAcross(
                List.of(single), 4, CompositeStorageOpsTest::take, Integer::intValue, CompositeStorageOpsTest::reinsert);
        assertEquals(4, ok.stream().mapToInt(Integer::intValue).sum());
        assertEquals(0, single.contents);
    }
}
