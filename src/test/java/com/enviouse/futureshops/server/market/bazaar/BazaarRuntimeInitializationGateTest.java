package com.enviouse.futureshops.server.market.bazaar;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarRuntimeInitializationGateTest {
    @Test
    void recoveryToReadyTransitionInitializesExactlyOnce() {
        BazaarRuntimeInitializationGate gate =
                new BazaarRuntimeInitializationGate();
        AtomicInteger calls = new AtomicInteger();

        assertFalse(gate.initializeIfReady(false, calls::incrementAndGet));
        assertTrue(gate.initializeIfReady(true, calls::incrementAndGet));
        assertFalse(gate.initializeIfReady(true, calls::incrementAndGet));
        assertEquals(1, calls.get());
    }

    @Test
    void failedInitializationCanBeRetried() {
        BazaarRuntimeInitializationGate gate =
                new BazaarRuntimeInitializationGate();

        assertThrows(IllegalStateException.class, () ->
                gate.initializeIfReady(true, () -> {
                    throw new IllegalStateException("failed");
                }));
        assertTrue(gate.initializeIfReady(true, () -> {
        }));
    }

    @Test
    void resetAllowsTheNextServerLifecycleToInitialize() {
        BazaarRuntimeInitializationGate gate =
                new BazaarRuntimeInitializationGate();

        assertTrue(gate.initializeIfReady(true, () -> {
        }));
        gate.reset();
        assertTrue(gate.initializeIfReady(true, () -> {
        }));
    }
}
