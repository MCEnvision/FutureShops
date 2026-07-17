package com.enviouse.futureshops.server.escrow.runtime;

@FunctionalInterface
public interface EscrowPreparedCheckpointRestore {
    void apply();
}
