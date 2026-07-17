package com.enviouse.futureshops.client.market;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketCapabilitiesSnapshotTest {
    @Test
    void disabledModuleWithClaimsBecomesClaimsOnlyAndVisible() {
        MarketModuleCapability capability = new MarketModuleCapability(
            MarketModule.AUCTION_HOUSE,
            MarketModuleAvailability.CLAIMS_ONLY,
            "Auction House",
            "#D85B68",
            2L,
            4L
        );

        assertTrue(capability.availability().visible());
        assertTrue(capability.canOpenView("claims"));
        assertFalse(capability.canOpenView("browse"));
    }

    @Test
    void invalidDefaultFallsBackToAnEnabledModule() {
        MarketModuleCapability shop = capability(MarketModule.SHOP, MarketModuleAvailability.ENABLED, 0L);
        MarketModuleCapability bazaar = capability(MarketModule.BAZAAR, MarketModuleAvailability.DISABLED, 0L);
        MarketCapabilitiesSnapshot snapshot = new MarketCapabilitiesSnapshot(
            UUID.randomUUID(), 8L, true, MarketModule.BAZAAR, List.of(shop, bazaar));

        assertEquals(MarketModule.SHOP, snapshot.defaultModule());
    }

    @Test
    void duplicateModulesAndHiddenClaimsFailClosed() {
        MarketModuleCapability shop = capability(MarketModule.SHOP, MarketModuleAvailability.ENABLED, 0L);
        assertThrows(IllegalArgumentException.class, () -> new MarketCapabilitiesSnapshot(
            UUID.randomUUID(), 0L, true, MarketModule.SHOP, List.of(shop, shop)));
        assertThrows(IllegalArgumentException.class, () -> new MarketModuleCapability(
            MarketModule.BAZAAR, MarketModuleAvailability.HIDDEN, "Bazaar", "#48B978", 1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> new MarketModuleCapability(
            MarketModule.BAZAAR, MarketModuleAvailability.DISABLED, "Bazaar", "#48B978", 1L, 0L));
    }

    private static MarketModuleCapability capability(
        MarketModule module,
        MarketModuleAvailability availability,
        long claims
    ) {
        return new MarketModuleCapability(
            module, availability, module.defaultDisplayName(), module.defaultAccent(), claims, 0L);
    }
}
