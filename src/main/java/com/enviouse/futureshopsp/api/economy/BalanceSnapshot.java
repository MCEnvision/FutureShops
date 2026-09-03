package com.enviouse.futureshopsp.api.economy;

import java.util.Objects;
import java.util.UUID;

/** Authoritative provider balance expressed in signed integer minor units. */
public record BalanceSnapshot(UUID playerId, long balanceMinorUnits) {
    public BalanceSnapshot {
        Objects.requireNonNull(playerId, "playerId");
    }
}
