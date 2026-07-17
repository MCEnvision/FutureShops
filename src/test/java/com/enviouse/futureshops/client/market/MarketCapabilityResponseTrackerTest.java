package com.enviouse.futureshops.client.market;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketCapabilityResponseTrackerTest {
    @Test
    void newestCorrelatedResponseWinsAndOlderResponseIsStale() {
        MarketCapabilityResponseTracker tracker =
                new MarketCapabilityResponseTracker(8);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        tracker.begin(first);
        tracker.begin(second);

        assertEquals(
                MarketCapabilityResponseTracker.Decision.STALE_REQUEST,
                tracker.accept(snapshot(first, 1L, "Bazaar")));
        assertEquals(MarketCapabilityResponseTracker.Decision.ACCEPT,
                tracker.accept(snapshot(second, 2L, "Bazaar")));
        assertEquals(second,
                tracker.latest().orElseThrow().requestId());
    }

    @Test
    void duplicateUnknownAndClosedResponsesCannotReplaceState() {
        MarketCapabilityResponseTracker tracker =
                new MarketCapabilityResponseTracker(8);
        UUID requestId = UUID.randomUUID();
        MarketCapabilitiesSnapshot response = snapshot(
                requestId, 1L, "Bazaar");
        tracker.begin(requestId);

        assertEquals(MarketCapabilityResponseTracker.Decision.ACCEPT,
                tracker.accept(response));
        assertEquals(
                MarketCapabilityResponseTracker.Decision.DUPLICATE_RESPONSE,
                tracker.accept(response));
        assertEquals(
                MarketCapabilityResponseTracker.Decision.UNKNOWN_REQUEST,
                tracker.accept(snapshot(UUID.randomUUID(), 2L,
                        "Bazaar")));
        tracker.close();
        assertEquals(MarketCapabilityResponseTracker.Decision.CLOSED,
                tracker.accept(response));
        assertTrue(tracker.latest().isEmpty());
    }

    @Test
    void staleAndConflictingRevisionsFailClosed() {
        MarketCapabilityResponseTracker tracker =
                new MarketCapabilityResponseTracker(8);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        tracker.begin(first);
        assertEquals(MarketCapabilityResponseTracker.Decision.ACCEPT,
                tracker.accept(snapshot(first, 5L, "Bazaar")));

        tracker.begin(second);
        assertEquals(
                MarketCapabilityResponseTracker.Decision.STALE_REVISION,
                tracker.accept(snapshot(second, 4L, "Bazaar")));
        tracker.begin(third);
        assertEquals(
                MarketCapabilityResponseTracker.Decision.REVISION_CONFLICT,
                tracker.accept(snapshot(third, 5L, "Green Bazaar")));
        assertEquals("Bazaar", tracker.latest().orElseThrow()
                .byModule().get(MarketModule.BAZAAR).displayName());
    }

    @Test
    void walletPresentationRejectsStaleAndConflictingResponses() {
        MarketCapabilityResponseTracker tracker =
                new MarketCapabilityResponseTracker(8);
        UUID accepted = UUID.randomUUID();
        tracker.begin(accepted);
        assertEquals(MarketCapabilityResponseTracker.Decision.ACCEPT,
                tracker.accept(snapshot(accepted, 5L, "Bazaar",
                        1250L, "Credits", 2)));

        UUID stale = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        tracker.begin(stale);
        tracker.begin(current);
        assertEquals(
                MarketCapabilityResponseTracker.Decision.STALE_REQUEST,
                tracker.accept(snapshot(stale, 6L, "Bazaar",
                        9999L, "Credits", 2)));
        assertEquals(1250L, tracker.latest().orElseThrow()
                .walletBalanceMinorUnits());
        assertEquals(
                MarketCapabilityResponseTracker.Decision.REVISION_CONFLICT,
                tracker.accept(snapshot(current, 5L, "Bazaar",
                        1251L, "Credits", 2)));
        assertEquals(1250L, tracker.latest().orElseThrow()
                .walletBalanceMinorUnits());
    }

    @Test
    void trackingIsBoundedAndConsumedIdentityCannotBeReused() {
        MarketCapabilityResponseTracker tracker =
                new MarketCapabilityResponseTracker(2);
        UUID evicted = UUID.randomUUID();
        UUID consumed = UUID.randomUUID();
        tracker.begin(evicted);
        tracker.begin(consumed);
        tracker.begin(UUID.randomUUID());

        assertEquals(2, tracker.trackedRequestCount());
        assertEquals(
                MarketCapabilityResponseTracker.Decision.UNKNOWN_REQUEST,
                tracker.accept(snapshot(evicted, 1L, "Bazaar")));
        assertEquals(
                MarketCapabilityResponseTracker.Decision.STALE_REQUEST,
                tracker.accept(snapshot(consumed, 1L, "Bazaar")));
        assertThrows(IllegalArgumentException.class,
                () -> tracker.begin(consumed));
    }

    @Test
    void zeroIdentityAndUnsafeLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new MarketCapabilityResponseTracker(0));
        MarketCapabilityResponseTracker tracker =
                new MarketCapabilityResponseTracker(1);
        assertThrows(IllegalArgumentException.class,
                () -> tracker.begin(new UUID(0L, 0L)));
    }

    private static MarketCapabilitiesSnapshot snapshot(
            UUID requestId,
            long revision,
            String bazaarName
    ) {
        return new MarketCapabilitiesSnapshot(requestId, revision,
                true, MarketModule.SHOP, List.of(
                capability(MarketModule.SHOP,
                        MarketModuleAvailability.ENABLED, "Shop",
                        revision),
                capability(MarketModule.BAZAAR,
                        MarketModuleAvailability.ENABLED, bazaarName,
                        revision),
                capability(MarketModule.AUCTION_HOUSE,
                        MarketModuleAvailability.ENABLED,
                        "Auction House", revision)));
    }

    private static MarketCapabilitiesSnapshot snapshot(
            UUID requestId,
            long revision,
            String bazaarName,
            long balance,
            String currency,
            int decimals
    ) {
        MarketCapabilitiesSnapshot base = snapshot(requestId, revision,
                bazaarName);
        return new MarketCapabilitiesSnapshot(requestId, revision,
                base.showNavigation(), base.defaultModule(), balance,
                true, currency, decimals, base.modules());
    }

    private static MarketModuleCapability capability(
            MarketModule module,
            MarketModuleAvailability availability,
            String displayName,
            long revision
    ) {
        return new MarketModuleCapability(module, availability,
                displayName, module.defaultAccent(), 0L, revision);
    }
}
