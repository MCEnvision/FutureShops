package com.enviouse.futureshops.server.escrow.checkpoint;

import java.io.IOException;

@FunctionalInterface
public interface EscrowPersistenceFaultInjector {
    EscrowPersistenceFaultInjector NONE = phase -> {
    };

    void at(EscrowPersistencePhase phase) throws IOException;
}
