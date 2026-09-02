package com.enviouse.futureshops.server.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageBoundsTest {
    @Test
    void acceptsOnlyBoundedPositivePages() {
        assertTrue(PageBounds.isValid(1, 1));
        assertTrue(PageBounds.isValid(PageBounds.MAX_PAGE_INDEX, PageBounds.MAX_PAGE_SIZE));
        assertFalse(PageBounds.isValid(0, 1));
        assertFalse(PageBounds.isValid(PageBounds.MAX_PAGE_INDEX + 1, 1));
        assertFalse(PageBounds.isValid(1, PageBounds.MAX_PAGE_SIZE + 1));
    }

    @Test
    void offsetUsesLongArithmetic() {
        assertEquals(99_999_900L,
                PageBounds.offset(PageBounds.MAX_PAGE_INDEX, PageBounds.MAX_PAGE_SIZE));
        assertEquals(99_999_900L,
                PageBounds.offset(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }
}
