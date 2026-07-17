package com.enviouse.futureshops.server.economy.migration;

public record LegacyBalanceMigrationPolicy(boolean allowNegativeLegacyBalances) {
    public static LegacyBalanceMigrationPolicy rejectNegativeBalances() {
        return new LegacyBalanceMigrationPolicy(false);
    }
}
