package com.enviouse.futureshopsp.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyCommandUtilTest {
    @Test
    void formatsLongMinimumWithoutAbsoluteValueOverflow() {
        assertEquals("-92233720368547758.08", EconomyCommandUtil.formatMinorUnits(Long.MIN_VALUE, 2));
    }

    @Test
    void formatsMinorUnitsExactlyWithoutFloatingPointRounding() {
        assertEquals("1234567890123.45", EconomyCommandUtil.formatMinorUnits(123456789012345L, 2));
        assertEquals("1.000000", EconomyCommandUtil.formatMinorUnits(1_000_000L, 6));
    }
}
