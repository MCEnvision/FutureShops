package com.enviouse.futureshops.server.economy.migration;

public enum LegacyBalanceMigrationStage {
    UNINITIALIZED,
    SNAPSHOT_PENDING,
    IMPORTING,
    IMPORTS_COMPLETE,
    COMPLETE,
    FAILED
}
