package com.enviouse.futureshops.server.economy;

import com.enviouse.futureshops.api.economy.ProviderError;
import com.enviouse.futureshops.api.economy.ProviderLifecycle;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnavailablePublicEconomyProviderTest {
    @Test
    void unavailableProviderDoesNotTurnLeaderboardIntoEmptySuccess() {
        UnavailablePublicEconomyProvider provider = new UnavailablePublicEconomyProvider(
                "fixture", ProviderLifecycle.MISSING, "provider is not registered");

        var leaderboard = provider.leaderboard(1, 10);

        assertEquals(ProviderError.CAPABILITY_MISSING, leaderboard.error());
        assertTrue(leaderboard.value().isEmpty());
        assertEquals(ProviderError.NOT_READY,
                provider.balance(UUID.randomUUID()).error());
    }
}
