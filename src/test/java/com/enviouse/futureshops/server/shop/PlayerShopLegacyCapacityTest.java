package com.enviouse.futureshops.server.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerShopLegacyCapacityTest {
    @Test
    void partiallyConsumedLegacyCapacityPreservesRemainingUnits() {
        assertEquals(6L,
                PlayerShopEscrowTransactionService.remainingCapacity(
                        10L, 4L));
    }

    @Test
    void exhaustedLegacyCapacityRemainsExhausted() {
        assertEquals(0L,
                PlayerShopEscrowTransactionService.remainingCapacity(
                        10L, 10L));
        assertEquals(0L,
                PlayerShopEscrowTransactionService.remainingCapacity(
                        10L, 14L));
    }

    @Test
    void unlimitedCapacityIgnoresLegacyCounter() {
        assertEquals(0L,
                PlayerShopEscrowTransactionService.remainingCapacity(
                        0L, 14L));
    }
}
