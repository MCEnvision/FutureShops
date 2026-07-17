package com.enviouse.futureshops.server.economy.migration;

import java.util.Objects;
import java.util.UUID;

public record LegacyBalanceEntry(UUID playerId, long balanceMinorUnits) {
    public LegacyBalanceEntry {
        Objects.requireNonNull(playerId, "playerId");
    }
}
