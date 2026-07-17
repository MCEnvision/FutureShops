package com.enviouse.futureshops.server.market.session;

import com.enviouse.futureshops.client.market.MarketModule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketServerSessionRegistryTest {
    private static final String FIRST = "a".repeat(64);
    private static final String SECOND = "b".repeat(64);

    @Test
    void requestReplayAndConflictAreSeparated() {
        MarketServerSessionRegistry registry =
                MarketServerSessionRegistry.defaults();
        UUID player = UUID.randomUUID();
        UUID route = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        registry.open(player, MarketModule.BAZAAR, "products",
                route, 100L);

        assertEquals(MarketSessionDecision.ACCEPT, registry.accept(
                player, route, MarketModule.BAZAAR, "products",
                request, FIRST, 101L));
        assertEquals(MarketSessionDecision.REPLAY, registry.accept(
                player, route, MarketModule.BAZAAR, "products",
                request, FIRST, 102L));
        assertEquals(MarketSessionDecision.CONFLICT, registry.accept(
                player, route, MarketModule.BAZAAR, "products",
                request, SECOND, 103L));
    }

    @Test
    void replacedAndExpiredRoutesFailClosed() {
        MarketServerSessionRegistry registry =
                new MarketServerSessionRegistry(Duration.ofMillis(10),
                        8, Duration.ofSeconds(1));
        UUID player = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        registry.open(player, MarketModule.AUCTION_HOUSE, "browse",
                first, 100L);
        registry.open(player, MarketModule.AUCTION_HOUSE, "mine",
                second, 101L);

        assertEquals(MarketSessionDecision.STALE_ROUTE, registry.accept(
                player, first, MarketModule.AUCTION_HOUSE, "mine",
                UUID.randomUUID(), FIRST, 102L));
        assertEquals(MarketSessionDecision.EXPIRED, registry.accept(
                player, second, MarketModule.AUCTION_HOUSE, "mine",
                UUID.randomUUID(), FIRST, 112L));
        assertThrows(IllegalArgumentException.class, () ->
                registry.open(player, MarketModule.AUCTION_HOUSE,
                        "browse", first, 113L));
    }

    @Test
    void moduleViewAndRateLimitsAreIndependent() {
        MarketServerSessionRegistry registry =
                new MarketServerSessionRegistry(Duration.ofMinutes(1),
                        1, Duration.ofSeconds(1));
        UUID player = UUID.randomUUID();
        UUID route = UUID.randomUUID();
        registry.open(player, MarketModule.BAZAAR, "products",
                route, 100L);

        assertEquals(MarketSessionDecision.WRONG_MODULE, registry.accept(
                player, route, MarketModule.AUCTION_HOUSE, "products",
                UUID.randomUUID(), FIRST, 100L));
        assertEquals(MarketSessionDecision.WRONG_VIEW, registry.accept(
                player, route, MarketModule.BAZAAR, "orders",
                UUID.randomUUID(), FIRST, 100L));
        assertEquals(MarketSessionDecision.ACCEPT, registry.accept(
                player, route, MarketModule.BAZAAR, "products",
                UUID.randomUUID(), FIRST, 100L));
        assertEquals(MarketSessionDecision.RATE_LIMITED, registry.accept(
                player, route, MarketModule.BAZAAR, "products",
                UUID.randomUUID(), FIRST, 100L));
        assertEquals(MarketSessionDecision.ACCEPT, registry.accept(
                player, route, MarketModule.BAZAAR, "products",
                UUID.randomUUID(), FIRST, 1100L));
    }

    @Test
    void staleRouteCannotCloseReplacementSession() {
        MarketServerSessionRegistry registry =
                MarketServerSessionRegistry.defaults();
        UUID player = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        registry.open(player, MarketModule.BAZAAR, "products",
                first, 100L);
        registry.open(player, MarketModule.BAZAAR, "orders",
                second, 101L);

        assertFalse(registry.close(player, first));
        assertTrue(registry.session(player).isPresent());
        assertEquals(second, registry.session(player).orElseThrow()
                .routeNonce());
        assertTrue(registry.close(player, second));
        assertTrue(registry.session(player).isEmpty());
    }
}
