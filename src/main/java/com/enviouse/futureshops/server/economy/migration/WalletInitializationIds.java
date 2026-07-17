package com.enviouse.futureshops.server.economy.migration;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class WalletInitializationIds {
    private static final String LEGACY_DOMAIN =
            "futureshops.wallet.initialization.legacy.v1.";
    private static final String STARTING_GRANT_DOMAIN =
            "futureshops.wallet.initialization.starting.grant.v1.";

    private WalletInitializationIds() {
    }

    public static UUID legacyBalance(UUID playerId) {
        return deterministic(LEGACY_DOMAIN, playerId);
    }

    public static UUID startingGrant(UUID playerId) {
        return deterministic(STARTING_GRANT_DOMAIN, playerId);
    }

    public static WalletInitializationRequest legacyRequest(
            LegacyBalanceEntry entry
    ) {
        Objects.requireNonNull(entry, "entry");
        return new WalletInitializationRequest(
                legacyBalance(entry.playerId()),
                entry.playerId(),
                entry.balanceMinorUnits(),
                WalletInitializationSource.LEGACY_BALANCE);
    }

    public static WalletInitializationRequest startingGrantRequest(
            UUID playerId,
            long startingBalanceMinorUnits
    ) {
        return new WalletInitializationRequest(
                startingGrant(playerId),
                playerId,
                startingBalanceMinorUnits,
                WalletInitializationSource.STARTING_GRANT);
    }

    private static UUID deterministic(String domain, UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return UUID.nameUUIDFromBytes(
                (domain + playerId).getBytes(StandardCharsets.UTF_8));
    }
}
