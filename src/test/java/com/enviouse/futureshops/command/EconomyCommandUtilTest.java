package com.enviouse.futureshops.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EconomyCommandUtilTest {
    @Test
    void displayedMajorUnitsParseToExactMinorUnits() {
        assertEquals(100L,
                EconomyCommandUtil.parseAmountToMinorUnits("1.00", 2));
        assertEquals(125L,
                EconomyCommandUtil.parseAmountToMinorUnits("1.25", 2));
        assertEquals(100L,
                EconomyCommandUtil.parseAmountToMinorUnits("1", 2));
    }

    @Test
    void excessPrecisionIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> EconomyCommandUtil.parseAmountToMinorUnits(
                        "1.001", 2));
    }
}
