package com.enviouse.futureshops.server.economy;

import java.util.UUID;

public record BalanceEntry(UUID playerUUID, long balanceMinorUnits) {
}

