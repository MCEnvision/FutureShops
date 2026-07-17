package com.enviouse.futureshops.server.economy.migration;

import java.util.Objects;
import java.util.UUID;

public record WalletInitializationRequest(UUID requestId,
                                          UUID playerId,
                                          long balanceMinorUnits,
                                          WalletInitializationSource source) {
    public WalletInitializationRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(source, "source");
        if (balanceMinorUnits < 0L
                && source != WalletInitializationSource.LEGACY_BALANCE) {
            throw new IllegalArgumentException("Wallet initialization balance cannot be negative");
        }
    }
}
