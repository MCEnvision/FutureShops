package com.enviouse.futureshops.client.market;

import com.enviouse.futureshops.money.PaymentSource;
import com.enviouse.futureshops.server.market.query.MarketPageCard;
import com.enviouse.futureshops.server.market.query.MarketPageCardKind;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketClientNavigationCoordinatorTest {
    @Test
    void backRestoresEveryExactPriorRouteField() {
        MarketRoute initial = route(MarketModule.AUCTION_HOUSE,
            "browse", "tools", "pick axe", "buy_now",
            "lowest_price", 3, 91, "listing zero");
        MarketClientNavigationCoordinator coordinator = coordinator(
            initial, false, preferences());
        MarketRoute latestListState = new MarketRoute(initial.module(),
            initial.viewId(), "weapons", "diamond", "auction_only",
            "ending_soon", 7, 143, "listing selected",
            initial.routeNonce());
        coordinator.updateCurrent(latestListState);
        MarketRoute detail = route(MarketModule.AUCTION_HOUSE,
            "listing_detail", "weapons", "diamond", "auction_only",
            "ending_soon", 0, 18, "listing forty two");
        UUID detailRequest = UUID.randomUUID();
        UUID detailRoute = UUID.randomUUID();

        coordinator.beginDetail(detailRequest, detail);
        assertEquals(MarketClientNavigationCoordinator.OpenDecision.ACCEPT,
            coordinator.acceptOpenResponse(detailRequest,
                MarketModule.AUCTION_HOUSE, "listing_detail",
                detailRoute).decision());

        UUID backRequest = UUID.randomUUID();
        MarketClientNavigationCoordinator.Command command =
            coordinator.escape(backRequest);
        MarketRoute desiredBack = command.openRequest().orElseThrow()
            .desiredRoute();
        assertRouteStateEquals(latestListState, desiredBack);
        UUID returnedRoute = UUID.randomUUID();
        MarketClientNavigationCoordinator.OpenResponse response =
            coordinator.acceptOpenResponse(backRequest,
                MarketModule.AUCTION_HOUSE, "browse", returnedRoute);

        assertEquals(MarketClientNavigationCoordinator.OpenDecision.ACCEPT,
            response.decision());
        assertEquals(MarketNavigationState.Action.RETURN,
            response.transition().orElseThrow().action());
        assertEquals(latestListState.withNonce(returnedRoute),
            coordinator.current());
        assertEquals(0, coordinator.historyDepth());
    }

    @Test
    void xClosesEverythingAndDelayedResponsesCannotReopenIt() {
        MarketRoute initial = MarketRoute.root(
            MarketModule.BAZAAR, UUID.randomUUID());
        MarketClientNavigationCoordinator coordinator = coordinator(
            initial, true, preferences());
        UUID pageRequest = UUID.randomUUID();
        coordinator.beginResponseRequest(pageRequest,
            MarketResponseFamily.CONTENT);
        UUID openRequest = UUID.randomUUID();
        coordinator.beginTab(openRequest, "orders");

        MarketNavigationState.Transition close = coordinator.close();

        assertEquals(MarketNavigationState.Action.CLOSE, close.action());
        assertTrue(close.closeBoundShopSession());
        assertFalse(coordinator.isOpen());
        assertEquals(MarketClientNavigationCoordinator.OpenDecision.CLOSED,
            coordinator.acceptOpenResponse(openRequest,
                MarketModule.BAZAAR, "orders", UUID.randomUUID())
                .decision());
        assertEquals(MarketRequestGate.Decision.CLOSED,
            coordinator.acceptResponse(pageRequest, MarketModule.BAZAAR,
                initial.routeNonce(), MarketResponseFamily.CONTENT));
        assertEquals(close, coordinator.close());
    }

    @Test
    void escapeAtRootClosesWithoutStartingAnOpenRequest() {
        MarketClientNavigationCoordinator coordinator = coordinator(
            MarketRoute.root(MarketModule.BAZAAR, UUID.randomUUID()),
            false, preferences());

        MarketClientNavigationCoordinator.Command command =
            coordinator.escape(UUID.randomUUID());

        assertTrue(command.openRequest().isEmpty());
        assertEquals(MarketNavigationState.Action.CLOSE,
            command.transition().orElseThrow().action());
        assertFalse(coordinator.isOpen());
    }

    @Test
    void moduleSwitchUsesRememberedEntryAndClearsHistory() {
        MarketSessionPreferences preferences = preferences();
        MarketRoute remembered = route(MarketModule.BAZAAR,
            "orders", "ores", "ignored", "open",
            "volume_highest", 5, 55, "order");
        preferences.rememberRoute(remembered, true);
        MarketRoute shop = MarketRoute.root(
            MarketModule.SHOP, UUID.randomUUID());
        MarketClientNavigationCoordinator coordinator = coordinator(
            shop, true, preferences);
        UUID detailRequest = UUID.randomUUID();
        coordinator.beginDetail(detailRequest,
            shop.withSelection("shop listing", UUID.randomUUID()));
        coordinator.acceptOpenResponse(detailRequest, MarketModule.SHOP,
            shop.viewId(), UUID.randomUUID());
        UUID switchRequest = UUID.randomUUID();

        MarketClientNavigationCoordinator.OpenRequest request =
            coordinator.beginSwitchModule(switchRequest,
                MarketModule.BAZAAR);

        assertEquals("orders", request.viewId());
        assertEquals("ores", request.desiredRoute().categoryId());
        assertEquals("open", request.desiredRoute().filterId());
        assertEquals("volume_highest", request.desiredRoute().sortId());
        UUID bazaarRoute = UUID.randomUUID();
        MarketNavigationState.Transition transition = coordinator
            .acceptOpenResponse(switchRequest, MarketModule.BAZAAR,
                "orders", bazaarRoute).transition().orElseThrow();
        assertEquals(MarketNavigationState.Action.SWITCH_MODULE,
            transition.action());
        assertTrue(transition.closeBoundShopSession());
        assertEquals(0, coordinator.historyDepth());
        assertEquals(request.desiredRoute().withNonce(bazaarRoute),
            coordinator.current());
    }

    @Test
    void onlyLatestOpenResponseMayChangeTheRoute() {
        MarketRoute initial = MarketRoute.root(
            MarketModule.BAZAAR, UUID.randomUUID());
        MarketClientNavigationCoordinator coordinator = coordinator(
            initial, false, preferences());
        UUID older = UUID.randomUUID();
        UUID latest = UUID.randomUUID();
        coordinator.beginTab(older, "claims");
        coordinator.beginTab(latest, "history");

        assertEquals(MarketClientNavigationCoordinator.OpenDecision.STALE_REQUEST,
            coordinator.acceptOpenResponse(older, MarketModule.BAZAAR,
                "claims", UUID.randomUUID()).decision());
        assertEquals(initial, coordinator.current());
        UUID historyRoute = UUID.randomUUID();
        assertEquals(MarketClientNavigationCoordinator.OpenDecision.ACCEPT,
            coordinator.acceptOpenResponse(latest, MarketModule.BAZAAR,
                "history", historyRoute).decision());
        assertEquals("history", coordinator.current().viewId());
        assertEquals(MarketClientNavigationCoordinator.OpenDecision.DUPLICATE_RESPONSE,
            coordinator.acceptOpenResponse(latest, MarketModule.BAZAAR,
                "history", historyRoute).decision());
    }

    @Test
    void wrongDestinationAndRetiredRouteResponsesFailClosed() {
        MarketRoute initial = MarketRoute.root(
            MarketModule.AUCTION_HOUSE, UUID.randomUUID());
        MarketClientNavigationCoordinator coordinator = coordinator(
            initial, false, preferences());
        UUID wrongView = UUID.randomUUID();
        coordinator.beginTab(wrongView, "claims");
        assertEquals(MarketClientNavigationCoordinator.OpenDecision.WRONG_VIEW,
            coordinator.acceptOpenResponse(wrongView,
                MarketModule.AUCTION_HOUSE, "history",
                UUID.randomUUID()).decision());
        assertEquals(initial, coordinator.current());

        UUID reusedRoute = UUID.randomUUID();
        coordinator.beginTab(reusedRoute, "claims");
        assertEquals(MarketClientNavigationCoordinator.OpenDecision.STALE_ROUTE,
            coordinator.acceptOpenResponse(reusedRoute,
                MarketModule.AUCTION_HOUSE, "claims",
                initial.routeNonce()).decision());
        assertEquals(initial, coordinator.current());
    }

    @Test
    void routeChangeMakesOldContentResponsesStale() {
        MarketRoute initial = MarketRoute.root(
            MarketModule.BAZAAR, UUID.randomUUID());
        MarketClientNavigationCoordinator coordinator = coordinator(
            initial, false, preferences());
        UUID contentRequest = UUID.randomUUID();
        coordinator.beginResponseRequest(contentRequest,
            MarketResponseFamily.CONTENT);
        UUID openRequest = UUID.randomUUID();
        coordinator.beginTab(openRequest, "orders");
        coordinator.acceptOpenResponse(openRequest, MarketModule.BAZAAR,
            "orders", UUID.randomUUID());

        assertEquals(MarketRequestGate.Decision.STALE_ROUTE,
            coordinator.acceptResponse(contentRequest,
                MarketModule.BAZAAR, initial.routeNonce(),
                MarketResponseFamily.CONTENT));
    }

    @Test
    void detailRouteCarriesExactIdentityAndRetiresListResponses() {
        MarketRoute initial = route(MarketModule.BAZAAR,
                "products", "metals", "iron ingot", "tradable",
                "instant_buy_lowest", 4, 87, "");
        MarketClientNavigationCoordinator coordinator = coordinator(
                initial, false, preferences());
        String identity = "x".repeat(192);
        MarketPageCard card = card(identity);
        UUID listRequest = UUID.randomUUID();
        coordinator.beginResponseRequest(listRequest,
                MarketResponseFamily.CONTENT);
        coordinator.rememberDetail(card);
        MarketRoute listSelection = new MarketRoute(initial.module(),
                initial.viewId(), initial.categoryId(), initial.query(),
                initial.filterId(), initial.sortId(), initial.page(),
                initial.scrollOffset(), identity, initial.routeNonce());
        coordinator.updateCurrent(listSelection);
        UUID detailRequest = UUID.randomUUID();
        MarketRoute desired = listSelection.toDetail(identity,
                detailRequest);

        coordinator.beginDetail(detailRequest, desired);
        UUID detailNonce = UUID.randomUUID();
        assertEquals(MarketClientNavigationCoordinator.OpenDecision.ACCEPT,
                coordinator.acceptOpenResponse(detailRequest,
                        MarketModule.BAZAAR, "product_detail",
                        detailNonce).decision());

        MarketRoute active = coordinator.current();
        assertEquals("product_detail", active.viewId());
        assertEquals(identity, active.selectionId());
        assertEquals(initial.categoryId(), active.categoryId());
        assertEquals(initial.query(), active.query());
        assertEquals(initial.filterId(), active.filterId());
        assertEquals(initial.sortId(), active.sortId());
        assertEquals(initial.page(), active.page());
        assertEquals(initial.scrollOffset(), active.scrollOffset());
        assertEquals(card, coordinator.detailSelection(
                MarketModule.BAZAAR, identity).orElseThrow().card());
        assertEquals(MarketRequestGate.Decision.STALE_ROUTE,
                coordinator.acceptResponse(listRequest,
                        MarketModule.BAZAAR, initial.routeNonce(),
                        MarketResponseFamily.CONTENT));
    }

    @Test
    void detailSelectionCacheIsBoundedAndSessionScoped() {
        MarketClientNavigationCoordinator coordinator = coordinator(
                MarketRoute.root(MarketModule.BAZAAR,
                        UUID.randomUUID()), false, preferences());
        for (int index = 0; index < 65; index++) {
            coordinator.rememberDetail(card("product " + index));
        }

        assertTrue(coordinator.detailSelection(MarketModule.BAZAAR,
                "product 0").isEmpty());
        assertEquals("product 64", coordinator.detailSelection(
                MarketModule.BAZAAR, "product 64").orElseThrow()
                .identity());
        coordinator.close();
        assertThrows(IllegalStateException.class,
                () -> coordinator.detailSelection(MarketModule.BAZAAR,
                        "product 64"));
    }

    @Test
    void paymentSourceUsesTheSharedClientSessionPreference() {
        MarketSessionPreferences preferences = preferences();
        MarketClientNavigationCoordinator first = coordinator(
            MarketRoute.root(MarketModule.BAZAAR, UUID.randomUUID()),
            false, preferences);
        first.rememberPaymentSource(PaymentSource.PHYSICAL);
        first.close();
        MarketClientNavigationCoordinator second = coordinator(
            MarketRoute.root(MarketModule.AUCTION_HOUSE,
                UUID.randomUUID()), false, preferences);

        assertEquals(PaymentSource.PHYSICAL,
            second.paymentSource().orElseThrow());
    }

    @Test
    void reopeningAtServerRootDoesNotOverwriteSessionPreferences() {
        MarketSessionPreferences preferences = preferences();
        preferences.rememberRoute(route(MarketModule.BAZAAR,
            "orders", "ores", "", "open", "volume_highest",
            0, 0, ""), true);

        coordinator(MarketRoute.root(MarketModule.BAZAAR,
            UUID.randomUUID()), false, preferences);

        MarketRoute restored = preferences.entryRoute(
            MarketModule.BAZAAR, UUID.randomUUID());
        assertEquals("orders", restored.viewId());
        assertEquals("ores", restored.categoryId());
        assertEquals("open", restored.filterId());
        assertEquals("volume_highest", restored.sortId());
    }

    @Test
    void routeStateCannotReplaceTheActiveRouteIdentity() {
        MarketRoute initial = MarketRoute.root(
            MarketModule.BAZAAR, UUID.randomUUID());
        MarketClientNavigationCoordinator coordinator = coordinator(
            initial, false, preferences());

        assertThrows(IllegalArgumentException.class,
            () -> coordinator.updateCurrent(initial.withNonce(
                UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class,
            () -> coordinator.beginSwitchModule(UUID.randomUUID(),
                MarketModule.BAZAAR));
    }

    private static MarketClientNavigationCoordinator coordinator(
        MarketRoute route,
        boolean boundShopSession,
        MarketSessionPreferences preferences
    ) {
        return new MarketClientNavigationCoordinator(route, 32, 64,
            boundShopSession, preferences);
    }

    private static MarketSessionPreferences preferences() {
        return new MarketSessionPreferences(
            new MarketSessionPreferences.Policy(true, true, true, true));
    }

    private static MarketPageCard card(String identity) {
        return new MarketPageCard(MarketPageCardKind.BAZAAR_PRODUCT,
                identity, Optional.empty(), "minecraft:iron_ingot", 1,
                "Iron", "metals", "ACTIVE", 1L, 10L, 9L,
                4L, 1000L, false, true, true);
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
        return new MarketRoute(module, view, category, query, filter,
            sort, page, scroll, selection, UUID.randomUUID());
    }

    private static void assertRouteStateEquals(MarketRoute expected,
                                               MarketRoute actual) {
        assertEquals(expected.module(), actual.module());
        assertEquals(expected.viewId(), actual.viewId());
        assertEquals(expected.categoryId(), actual.categoryId());
        assertEquals(expected.query(), actual.query());
        assertEquals(expected.filterId(), actual.filterId());
        assertEquals(expected.sortId(), actual.sortId());
        assertEquals(expected.page(), actual.page());
        assertEquals(expected.scrollOffset(), actual.scrollOffset());
        assertEquals(expected.selectionId(), actual.selectionId());
    }
}
