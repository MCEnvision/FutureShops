package com.enviouse.futureshops.server.market.bazaar.escrow;

import java.util.UUID;

public record BazaarEscrowWalletSnapshot(
        UUID ownerId,
        long walletMinor,
        long configurationGeneration
) {
    public BazaarEscrowWalletSnapshot {
        ownerId = BazaarEscrowIds.requireId(ownerId, "ownerId");
        if (walletMinor < 0L || configurationGeneration < 0L) {
            throw new IllegalArgumentException(
                    "Bazaar wallet snapshot is invalid");
        }
    }

    public void requireAvailable(long requiredMinor) {
        if (requiredMinor <= 0L || walletMinor < requiredMinor) {
            throw new IllegalArgumentException(
                    "Bazaar wallet funding is insufficient");
        }
    }
}
