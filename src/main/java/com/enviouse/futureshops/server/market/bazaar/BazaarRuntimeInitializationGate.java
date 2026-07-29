package com.enviouse.futureshops.server.market.bazaar;

import java.util.Objects;

public final class BazaarRuntimeInitializationGate {
    private boolean initialized;

    public synchronized boolean initializeIfReady(
            boolean ready,
            Runnable initializer
    ) {
        Objects.requireNonNull(initializer, "initializer");
        if (!ready || initialized) {
            return false;
        }
        initializer.run();
        initialized = true;
        return true;
    }

    public synchronized void reset() {
        initialized = false;
    }
}
