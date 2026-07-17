package com.enviouse.futureshops.client.market;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketCompactPagerTest {
    @Test
    void everyLocalViewAndBoundedCategoryIsReachable() {
        assertAllReachable(7, 3);
        assertAllReachable(257, 1);
        assertAllReachable(257, 9);
    }

    @Test
    void ensureVisiblePreservesOrMovesTheSmallestWindow() {
        assertEquals(0, MarketCompactPager.ensureVisible(
                7, 0, 3, 2));
        assertEquals(3, MarketCompactPager.ensureVisible(
                7, 0, 3, 5));
        assertEquals(1, MarketCompactPager.ensureVisible(
                7, 4, 3, 1));
        assertThrows(IllegalArgumentException.class,
                () -> MarketCompactPager.ensureVisible(7, 0, 3, 7));
    }

    private static void assertAllReachable(int count, int capacity) {
        Set<Integer> reached = new HashSet<>();
        int offset = 0;
        while (true) {
            MarketCompactPager.Window window = MarketCompactPager.window(
                    count, offset, capacity);
            for (int index = window.offset(); index < window.end(); index++) {
                reached.add(index);
            }
            if (!window.hasNext()) {
                break;
            }
            offset = MarketCompactPager.nextOffset(
                    count, offset, capacity);
        }
        assertEquals(count, reached.size());
    }
}
