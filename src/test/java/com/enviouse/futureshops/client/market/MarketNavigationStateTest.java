package com.enviouse.futureshops.client.market;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketNavigationStateTest {
    @Test
    void backRestoresExactRouteState() {
        MarketRoute root = route(MarketModule.SHOP, "browse", "tools", "pick", "all", "price", 3, 91, "");
        MarketRoute detail = root.withSelection("listing one", UUID.randomUUID());
        MarketNavigationState navigation = new MarketNavigationState(root, 16, true);

        navigation.navigate(detail);
        MarketNavigationState.Transition transition = navigation.back();

        assertEquals(MarketNavigationState.Action.RETURN, transition.action());
        MarketRoute returned = transition.route().orElseThrow();
        assertEquals(root.withNonce(returned.routeNonce()), returned);
        assertFalse(root.routeNonce().equals(returned.routeNonce()));
        assertEquals(0, navigation.historyDepth());
    }

    @Test
    void switchingModuleClearsHistoryAndClosesBoundShopSession() {
        MarketRoute shop = MarketRoute.root(MarketModule.SHOP, UUID.randomUUID());
        MarketNavigationState navigation = new MarketNavigationState(shop, 16, true);
        navigation.navigate(shop.withSelection("listing", UUID.randomUUID()));

        MarketNavigationState.Transition transition = navigation.switchModule(
            MarketRoute.root(MarketModule.BAZAAR, UUID.randomUUID()));

        assertEquals(MarketNavigationState.Action.SWITCH_MODULE, transition.action());
        assertTrue(transition.closeBoundShopSession());
        assertEquals(0, navigation.historyDepth());
        assertEquals(MarketModule.BAZAAR, navigation.current().module());
    }

    @Test
    void closeAlwaysClosesTheWholeInterface() {
        MarketNavigationState navigation = new MarketNavigationState(
            MarketRoute.root(MarketModule.AUCTION_HOUSE, UUID.randomUUID()), 8, false);

        MarketNavigationState.Transition transition = navigation.close();

        assertEquals(MarketNavigationState.Action.CLOSE, transition.action());
        assertTrue(transition.route().isEmpty());
        assertFalse(navigation.isOpen());
        assertThrows(IllegalStateException.class, navigation::current);
    }

    @Test
    void escapeAtRootClosesAndBackFromDetailDoesNot() {
        MarketRoute root = MarketRoute.root(MarketModule.BAZAAR, UUID.randomUUID());
        MarketNavigationState navigation = new MarketNavigationState(root, 8, false);
        navigation.navigate(root.withSelection("product", UUID.randomUUID()));

        assertEquals(MarketNavigationState.Action.RETURN, navigation.escape().action());
        assertEquals(MarketNavigationState.Action.CLOSE, navigation.escape().action());
    }

    @Test
    void sameModuleResetClosesBoundSessionAndNonceReuseFailsClosed() {
        MarketRoute root = MarketRoute.root(MarketModule.SHOP, UUID.randomUUID());
        MarketNavigationState navigation = new MarketNavigationState(root, 8, true);
        MarketRoute reset = MarketRoute.root(MarketModule.SHOP, UUID.randomUUID());

        assertTrue(navigation.switchModule(reset).closeBoundShopSession());
        assertThrows(IllegalArgumentException.class,
            () -> navigation.switchModule(reset));
    }

    @Test
    void routeActivationLimitClosesWithoutAPartialBackTransition() {
        MarketRoute root = MarketRoute.root(MarketModule.SHOP, UUID.randomUUID());
        MarketNavigationState navigation = new MarketNavigationState(root, 8, false);
        for (int index = 1; index < 4096; index++) {
            navigation.navigate(root.withView("view" + index, UUID.randomUUID()));
        }

        MarketNavigationState.Transition transition = navigation.back();

        assertEquals(MarketNavigationState.Action.CLOSE, transition.action());
        assertFalse(navigation.isOpen());
        assertEquals(0, navigation.historyDepth());
    }

    private static MarketRoute route(
        MarketModule module,
        String view,
        String category,
        String query,
        String filter,
        String sort,
        int page,
        int scroll,
        String selection
    ) {
        return new MarketRoute(
            module, view, category, query, filter, sort, page, scroll, selection, UUID.randomUUID());
    }
}
