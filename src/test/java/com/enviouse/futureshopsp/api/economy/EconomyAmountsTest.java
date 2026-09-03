package com.enviouse.futureshopsp.api.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EconomyAmountsTest {
    @Test
    void arithmeticUsesCheckedLongOperations() {
        assertEquals(3L, EconomyAmounts.addExact(1L, 2L));
        assertEquals(-1L, EconomyAmounts.subtractExact(1L, 2L));
        assertEquals(6L, EconomyAmounts.multiplyExact(2L, 3L));
        assertThrows(ArithmeticException.class, () -> EconomyAmounts.addExact(Long.MAX_VALUE, 1L));
        assertThrows(ArithmeticException.class, () -> EconomyAmounts.subtractExact(Long.MIN_VALUE, 1L));
        assertThrows(ArithmeticException.class, () -> EconomyAmounts.multiplyExact(Long.MAX_VALUE, 2L));
    }

    @Test
    void decimalParsingRejectsLossyValues() {
        assertEquals(1234L, EconomyAmounts.parseDecimal("12.34", 2));
        assertEquals(-5L, EconomyAmounts.parseDecimal("-0.5", 1));
        assertThrows(IllegalArgumentException.class, () -> EconomyAmounts.parseDecimal("1.001", 2));
        assertThrows(IllegalArgumentException.class, () -> EconomyAmounts.parseDecimal("nan", 2));
    }
}
