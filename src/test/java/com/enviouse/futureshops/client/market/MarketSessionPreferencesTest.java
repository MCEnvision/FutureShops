package com.enviouse.futureshops.client.market;

import com.enviouse.futureshops.money.PaymentSource;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSessionPreferencesTest {
    @Test
    void remembersModuleViewsFiltersSortsAndOneSessionPaymentSource() {
        MarketSessionPreferences preferences = new MarketSessionPreferences(
            presentation(true, true, true, true));
        MarketRoute bazaar = route(MarketModule.BAZAAR, "orders",
            "ores", "search", "open", "volume_highest", 4, 52,
            "order one");

        preferences.rememberRoute(bazaar, true);
        preferences.rememberPaymentSource(PaymentSource.PHYSICAL);

        assertTrue(preferences.hasRememberedTab(MarketModule.BAZAAR));
        MarketRoute restored = preferences.entryRoute(
            MarketModule.BAZAAR, UUID.randomUUID());
        assertEquals("orders", restored.viewId());
        assertEquals("ores", restored.categoryId());
        assertEquals("open", restored.filterId());
        assertEquals("volume_highest", restored.sortId());
        assertEquals("", restored.query());
        assertEquals(0, restored.page());
        assertEquals(0, restored.scrollOffset());
        assertEquals("", restored.selectionId());
        assertEquals(PaymentSource.PHYSICAL,
            preferences.paymentSource().orElseThrow());

        MarketRoute auction = preferences.entryRoute(
            MarketModule.AUCTION_HOUSE, UUID.randomUUID());
        assertEquals(MarketModule.AUCTION_HOUSE.rootView(),
            auction.viewId());
        assertEquals("", auction.categoryId());
        assertEquals("", auction.filterId());
        assertEquals("", auction.sortId());
    }

    @Test
    void disabledRememberSettingsDoNotRetainValues() {
        MarketSessionPreferences preferences = new MarketSessionPreferences(
            presentation(false, false, false, false));
        preferences.rememberRoute(route(MarketModule.AUCTION_HOUSE,
            "mine", "weapons", "query", "active", "newest",
            8, 77, "listing"), true);
        preferences.rememberPaymentSource(PaymentSource.WALLET);

        MarketRoute restored = preferences.entryRoute(
            MarketModule.AUCTION_HOUSE, UUID.randomUUID());
        assertEquals(MarketModule.AUCTION_HOUSE.rootView(),
            restored.viewId());
        assertEquals("", restored.categoryId());
        assertEquals("", restored.filterId());
        assertEquals("", restored.sortId());
        assertTrue(preferences.paymentSource().isEmpty());
        assertTrue(!preferences.hasRememberedTab(
            MarketModule.AUCTION_HOUSE));
    }

    @Test
    void policyReloadClearsPreferencesThatWereDisabled() {
        MarketSessionPreferences preferences = new MarketSessionPreferences(
            presentation(true, true, true, true));
        preferences.rememberRoute(route(MarketModule.BAZAAR,
            "portfolio", "farm", "", "filled", "trend_highest",
            0, 0, ""), true);
        preferences.rememberPaymentSource(PaymentSource.PHYSICAL);

        preferences.updatePolicy(presentation(false, true, false, false));

        MarketRoute restored = preferences.entryRoute(
            MarketModule.BAZAAR, UUID.randomUUID());
        assertEquals(MarketModule.BAZAAR.rootView(), restored.viewId());
        assertEquals("farm", restored.categoryId());
        assertEquals("filled", restored.filterId());
        assertEquals("", restored.sortId());
        assertTrue(preferences.paymentSource().isEmpty());
        assertTrue(!preferences.hasRememberedTab(MarketModule.BAZAAR));
    }

    @Test
    void preferenceInstancesAreIndependentClientSessions() {
        MarketSessionPreferences first = new MarketSessionPreferences(
            presentation(true, true, true, true));
        MarketSessionPreferences second = new MarketSessionPreferences(
            presentation(true, true, true, true));
        first.rememberTab(MarketModule.BAZAAR, "orders");
        first.rememberPaymentSource(PaymentSource.WALLET);

        assertEquals("orders", first.preference(
            MarketModule.BAZAAR).viewId());
        assertEquals(MarketModule.BAZAAR.rootView(), second.preference(
            MarketModule.BAZAAR).viewId());
        assertTrue(second.paymentSource().isEmpty());
    }

    private static MarketSessionPreferences.Policy presentation(
        boolean tab,
        boolean filter,
        boolean sort,
        boolean payment
    ) {
        return new MarketSessionPreferences.Policy(
            tab, filter, sort, payment);
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
}
