package com.enviouse.futureshops.server.economy.migration;

@FunctionalInterface
public interface MigrationDurabilityBarrier {
    void flush();
}
