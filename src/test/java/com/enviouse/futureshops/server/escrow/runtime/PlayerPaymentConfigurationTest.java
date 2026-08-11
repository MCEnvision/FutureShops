package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPaymentConfigurationTest {
    @Test
    void sameGenerationCannotHideChangedPaymentSemantics() {
        PlayerPaymentService.PaymentConfiguration before =
                configuration(4L, 100L, "Coins", 2);

        assertFalse(before.sameSemantics(
                configuration(4L, 101L, "Coins", 2)));
        assertFalse(before.sameSemantics(
                configuration(4L, 100L, "Credits", 2)));
        assertFalse(before.sameSemantics(
                configuration(4L, 100L, "Coins", 0)));
    }

    @Test
    void generationChangeWithExactSemanticsIsStable() {
        PlayerPaymentService.PaymentConfiguration before =
                configuration(4L, 100L, "Coins", 2);

        assertTrue(before.sameSemantics(
                configuration(5L, 100L, "Coins", 2)));
    }

    private static PlayerPaymentService.PaymentConfiguration configuration(
            long generation,
            long limit,
            String name,
            int decimals
    ) {
        return new PlayerPaymentService.PaymentConfiguration(
                generation, new PlayerPaymentService.FreshSettings(
                limit, name, decimals));
    }
}
