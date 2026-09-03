package com.enviouse.futureshopsp.money;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyConsumeOutcomeTest {
    @Test
    void acceptedValueUsesCheckedMinorUnitMultiplication() {
        MoneyValidationService.ConsumeOutcome outcome = new MoneyValidationService.ConsumeOutcome(
                3, 0, 25L, "mint", "");

        assertEquals(75L, outcome.acceptedValueMinor());
    }

    @Test
    void acceptedValueRejectsOverflowInsteadOfWrapping() {
        MoneyValidationService.ConsumeOutcome outcome = new MoneyValidationService.ConsumeOutcome(
                2, 0, Long.MAX_VALUE, "mint", "");

        assertThrows(ArithmeticException.class, outcome::acceptedValueMinor);
    }
}
