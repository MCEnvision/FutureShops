package com.enviouse.futureshops.server.economy.migration;

public enum LegacyBalanceMigrationFailure {
    NONE,
    NEGATIVE_LEGACY_BALANCE,
    SNAPSHOT_CHANGED,
    SNAPSHOT_CORRUPT,
    SOURCE_SEAL_CONFLICT,
    WALLET_CONFLICT,
    ARCHIVE_CONFLICT
}
