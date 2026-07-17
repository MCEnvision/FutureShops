package com.enviouse.futureshops.client.market;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MarketRequestGateTest {
    @Test
    void staleAndDuplicateResponsesCannotReopenAChangedRoute() {
        UUID firstRoute = UUID.randomUUID();
        UUID secondRoute = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        MarketRequestGate gate = new MarketRequestGate(firstRoute, 16);
        gate.begin(request, MarketModule.BAZAAR, firstRoute);
        gate.enterRoute(secondRoute);

        assertEquals(
            MarketRequestGate.Decision.STALE_ROUTE,
            gate.accept(request, MarketModule.BAZAAR, firstRoute));
        assertEquals(
            MarketRequestGate.Decision.DUPLICATE_RESPONSE,
            gate.accept(request, MarketModule.BAZAAR, firstRoute));
    }

    @Test
    void closedGateRejectsDelayedResponses() {
        UUID route = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        MarketRequestGate gate = new MarketRequestGate(route, 16);
        gate.begin(request, MarketModule.AUCTION_HOUSE, route);
        gate.close();

        assertEquals(
            MarketRequestGate.Decision.CLOSED,
            gate.accept(request, MarketModule.AUCTION_HOUSE, route));
        assertThrows(IllegalStateException.class, () -> gate.enterRoute(UUID.randomUUID()));
    }

    @Test
    void requestSemanticsCannotBeRebound() {
        UUID route = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        MarketRequestGate gate = new MarketRequestGate(route, 16);
        gate.begin(request, MarketModule.SHOP, route);

        assertThrows(IllegalArgumentException.class, () -> gate.begin(
            request, MarketModule.BAZAAR, route));
    }

    @Test
    void olderResponseOnTheSameRouteCannotReplaceTheLatestQuery() {
        UUID route = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        MarketRequestGate gate = new MarketRequestGate(route, 16);
        gate.begin(first, MarketModule.AUCTION_HOUSE, route);
        gate.begin(second, MarketModule.AUCTION_HOUSE, route);

        assertEquals(
            MarketRequestGate.Decision.STALE_REQUEST,
            gate.accept(first, MarketModule.AUCTION_HOUSE, route));
        assertEquals(
            MarketRequestGate.Decision.ACCEPT,
            gate.accept(second, MarketModule.AUCTION_HOUSE, route));
    }

    @Test
    void independentResponseFamiliesCanCompleteOnTheSameRoute() {
        UUID route = UUID.randomUUID();
        UUID content = UUID.randomUUID();
        UUID claims = UUID.randomUUID();
        MarketRequestGate gate = new MarketRequestGate(route, 16);
        gate.begin(content, MarketModule.BAZAAR, route, MarketResponseFamily.CONTENT);
        gate.begin(claims, MarketModule.BAZAAR, route, MarketResponseFamily.CLAIMS);

        assertEquals(MarketRequestGate.Decision.ACCEPT,
            gate.accept(content, MarketModule.BAZAAR, route, MarketResponseFamily.CONTENT));
        assertEquals(MarketRequestGate.Decision.ACCEPT,
            gate.accept(claims, MarketModule.BAZAAR, route, MarketResponseFamily.CLAIMS));
    }

    @Test
    void retiredRouteNonceCannotBeReactivated() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        MarketRequestGate gate = new MarketRequestGate(first, 16);

        gate.enterRoute(second);

        assertThrows(IllegalArgumentException.class,
            () -> gate.enterRoute(first));
    }
}
