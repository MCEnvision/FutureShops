package com.enviouse.futureshops.server.escrow.runtime;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmWithdrawalOutcomeTest {
    private static final UUID REQUEST_ID = UUID.fromString(
            "79000000-0000-0000-0000-000000000001");
    private static final String SIGNATURE = "e".repeat(64);

    @Test
    void rateLimitOutcomeCarriesOnlyBoundedPositiveDelay() {
        AtmWithdrawalOutcome result = AtmWithdrawalOutcome.failure(
                REQUEST_ID, AtmWithdrawalStatus.RATE_LIMITED,
                true, false, false, 0L, 0L, 0,
                SIGNATURE, 1_501L);

        assertTrue(result.retryable());
        assertEquals(1_501L, result.retryAfterMillis());
        assertThrows(IllegalArgumentException.class,
                () -> AtmWithdrawalOutcome.failure(
                        REQUEST_ID, AtmWithdrawalStatus.RATE_LIMITED,
                        true, false, false, 0L, 0L, 0,
                        SIGNATURE, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> AtmWithdrawalOutcome.failure(
                        REQUEST_ID, AtmWithdrawalStatus.RATE_LIMITED,
                        true, false, false, 0L, 0L, 0,
                        SIGNATURE,
                        AtmWithdrawalOutcome.MAX_RETRY_AFTER_MILLIS + 1L));
        assertThrows(IllegalArgumentException.class,
                () -> AtmWithdrawalOutcome.failure(
                        REQUEST_ID, AtmWithdrawalStatus.SERVER_ERROR,
                        true, false, false, 0L, 0L, 0,
                        SIGNATURE, 1L));
    }
}
