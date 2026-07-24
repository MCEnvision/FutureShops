package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.client.market.MarketCapabilitiesSnapshot;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.client.market.MarketModuleAvailability;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MarketCapabilityProjectorTest {
    @Test
    void enabledReadyModulesExposeBrowseCapabilities() {
        MarketCapabilitiesSnapshot snapshot = project(true, true, true,
                List.of());

        assertEquals(MarketModuleAvailability.ENABLED,
                snapshot.byModule().get(MarketModule.SHOP).availability());
        assertEquals(MarketModuleAvailability.ENABLED,
                snapshot.byModule().get(MarketModule.BAZAAR).availability());
        assertEquals(MarketModuleAvailability.ENABLED,
                snapshot.byModule().get(MarketModule.AUCTION_HOUSE)
                        .availability());
    }

    @Test
    void disabledModulesWithAssetsRemainAvailableForClaims() {
        EscrowClaim auction = claim("auction.sale.proceeds",
                ClaimStatus.PENDING);
        EscrowClaim bazaar = claim("bazaar.order.refund",
                ClaimStatus.QUARANTINED);

        MarketCapabilitiesSnapshot snapshot = project(false, false,
                true, List.of(auction, bazaar));

        assertEquals(MarketModuleAvailability.CLAIMS_ONLY,
                snapshot.byModule().get(MarketModule.BAZAAR)
                        .availability());
        assertEquals(1L, snapshot.byModule().get(MarketModule.BAZAAR)
                .openClaims());
        assertEquals(MarketModuleAvailability.CLAIMS_ONLY,
                snapshot.byModule().get(MarketModule.AUCTION_HOUSE)
                        .availability());
    }

    @Test
    void recoveringRuntimeRemovesBrowseWithoutHidingClaims() {
        MarketCapabilitiesSnapshot snapshot = project(true, true,
                false, List.of(claim("auction.item.refund",
                        ClaimStatus.PARTIALLY_DELIVERED)));

        assertEquals(MarketModuleAvailability.ENABLED,
                snapshot.byModule().get(MarketModule.SHOP)
                        .availability());
        assertEquals(MarketModuleAvailability.DISABLED,
                snapshot.byModule().get(MarketModule.BAZAAR)
                        .availability());
        assertEquals(MarketModuleAvailability.CLAIMS_ONLY,
                snapshot.byModule().get(MarketModule.AUCTION_HOUSE)
                        .availability());
        assertEquals(MarketModule.AUCTION_HOUSE,
                snapshot.defaultModule());
        assertFalse(snapshot.escrowReady());
    }

    @Test
    void recoveringRuntimeWithoutClaimsKeepsShopBrowsingAvailable() {
        MarketCapabilitiesSnapshot snapshot = project(true, true,
                false, List.of());

        assertEquals(MarketModuleAvailability.ENABLED,
                snapshot.byModule().get(MarketModule.SHOP)
                        .availability());
        assertEquals(MarketModule.SHOP, snapshot.defaultModule());
    }

    @Test
    void completedClaimsDoNotKeepDisabledModulesVisible() {
        MarketCapabilitiesSnapshot snapshot = project(false, false,
                true, List.of(claim("bazaar.order.refund",
                        ClaimStatus.COMPLETED)));

        assertEquals(MarketModuleAvailability.DISABLED,
                snapshot.byModule().get(MarketModule.BAZAAR)
                        .availability());
        assertEquals(0L, snapshot.byModule().get(MarketModule.BAZAAR)
                .openClaims());
    }

    @Test
    void nonMarketClaimsBelongToTheSharedShopClaimCenter() {
        MarketCapabilitiesSnapshot snapshot = project(false, false,
                true, List.of(claim("player.payment.refund",
                        ClaimStatus.PENDING)));

        assertEquals(1L, snapshot.byModule().get(MarketModule.SHOP)
                .openClaims());
        assertEquals(0L, snapshot.byModule().get(MarketModule.BAZAAR)
                .openClaims());
        assertEquals(0L, snapshot.byModule()
                .get(MarketModule.AUCTION_HOUSE).openClaims());
    }

    private static MarketCapabilitiesSnapshot project(
            boolean bazaarEnabled,
            boolean auctionEnabled,
            boolean escrowReady,
            List<EscrowClaim> claims
    ) {
        return MarketCapabilityProjector.project(UUID.randomUUID(), 7L,
                true, MarketModule.AUCTION_HOUSE, escrowReady,
                bazaarEnabled, auctionEnabled,
                new MarketCapabilityProjector.Branding("Shop",
                        "#9184D9"),
                new MarketCapabilityProjector.Branding("Bazaar",
                        "#48B978"),
                new MarketCapabilityProjector.Branding("Auction House",
                        "#D85B68"), claims);
    }

    private static EscrowClaim claim(
            String source,
            ClaimStatus status
    ) {
        long original = status == ClaimStatus.PARTIALLY_DELIVERED ? 2L
                : 1L;
        long remaining = status == ClaimStatus.COMPLETED ? 0L : 1L;
        return new EscrowClaim(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), source, ClaimKind.MONEY, original,
                remaining, new byte[0], status, "Market claim",
                Instant.EPOCH, Instant.EPOCH);
    }
}
