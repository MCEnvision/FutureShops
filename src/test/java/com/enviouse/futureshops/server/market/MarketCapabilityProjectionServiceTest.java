package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.client.market.MarketCapabilitiesSnapshot;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.client.market.MarketModuleAvailability;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.escrow.claim.OpenClaimSourceCounts;
import com.enviouse.futureshops.server.market.control.MarketControlActor;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketControlRepository;
import com.enviouse.futureshops.server.market.control.MarketControlState;
import com.enviouse.futureshops.server.market.control.MarketControlTransitionCommand;
import com.enviouse.futureshops.server.market.control.MarketModuleStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketCapabilityProjectionServiceTest {
    @Test
    void aggregateProjectionPreservesAllClaimCounts() {
        UUID owner = UUID.randomUUID();
        MarketCapabilityRevisionTracker revisions =
                new MarketCapabilityRevisionTracker(8);
        MarketCapabilitiesSnapshot snapshot =
                MarketCapabilityProjectionService.project(
                        projection(owner, false, false, true),
                        new OpenClaimSourceCounts(309L, Map.of(
                                MarketCapabilityProjectionService
                                        .AUCTION_CLAIM_PREFIX, 300L,
                                MarketCapabilityProjectionService
                                        .BAZAAR_CLAIM_PREFIX, 8L)),
                        revisions);

        assertEquals(300L, snapshot.byModule()
                .get(MarketModule.AUCTION_HOUSE).openClaims());
        assertEquals(8L, snapshot.byModule()
                .get(MarketModule.BAZAAR).openClaims());
        assertEquals(1L, snapshot.byModule()
                .get(MarketModule.SHOP).openClaims());
        assertEquals(MarketModuleAvailability.FROZEN,
                snapshot.byModule().get(MarketModule.AUCTION_HOUSE)
                        .availability());
    }

    @Test
    void listProjectionDefensivelyFiltersClaimsByOwner() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        MarketCapabilitiesSnapshot snapshot =
                MarketCapabilityProjectionService.project(
                        projection(owner, false, false, true),
                        List.of(claim(owner, "bazaar.owned"),
                                claim(other, "auction.foreign")),
                        new MarketCapabilityRevisionTracker(8));

        assertEquals(1L, snapshot.byModule().get(MarketModule.BAZAAR)
                .openClaims());
        assertEquals(0L, snapshot.byModule()
                .get(MarketModule.AUCTION_HOUSE).openClaims());
    }

    @Test
    void internalEscrowMoneyNeverAppearsInModuleClaimCounts() {
        UUID owner = UUID.randomUUID();
        MarketCapabilitiesSnapshot snapshot =
                MarketCapabilityProjectionService.project(
                        projection(owner, false, false, true),
                        List.of(claim(owner, "bazaar.public"),
                                claim(owner, "bazaar.internal",
                                        ClaimKind.INTERNAL_ESCROW_MONEY)),
                        new MarketCapabilityRevisionTracker(8));

        assertEquals(1L, snapshot.byModule().get(MarketModule.BAZAAR)
                .openClaims());
        assertEquals(0L, snapshot.byModule().get(MarketModule.SHOP)
                .openClaims());
    }

    @Test
    void revisionIsStableUntilProjectedStateChanges() {
        UUID owner = UUID.randomUUID();
        MarketCapabilityRevisionTracker revisions =
                new MarketCapabilityRevisionTracker(8);
        MarketCapabilitiesSnapshot first =
                MarketCapabilityProjectionService.project(
                        projection(owner, true, true, true),
                        new OpenClaimSourceCounts(0L, Map.of(
                                "auction.", 0L, "bazaar.", 0L)),
                        revisions);
        MarketCapabilitiesSnapshot repeated =
                MarketCapabilityProjectionService.project(
                        projection(owner, true, true, true),
                        new OpenClaimSourceCounts(0L, Map.of(
                                "auction.", 0L, "bazaar.", 0L)),
                        revisions);
        MarketCapabilitiesSnapshot changed =
                MarketCapabilityProjectionService.project(
                        projection(owner, true, true, true),
                        new OpenClaimSourceCounts(1L, Map.of(
                                "auction.", 1L, "bazaar.", 0L)),
                        revisions);

        assertEquals(first.revision(), repeated.revision());
        assertTrue(changed.revision() > first.revision());
    }

    @Test
    void escrowReadinessAdvancesTheProjectionRevision() {
        UUID owner = UUID.randomUUID();
        MarketCapabilityRevisionTracker revisions =
                new MarketCapabilityRevisionTracker(8);
        OpenClaimSourceCounts claims = new OpenClaimSourceCounts(
                0L, Map.of("auction.", 0L, "bazaar.", 0L));
        MarketCapabilitiesSnapshot recovering =
                MarketCapabilityProjectionService.project(
                        projection(owner, false, false, false),
                        claims, revisions);
        MarketCapabilitiesSnapshot ready =
                MarketCapabilityProjectionService.project(
                        projection(owner, false, false, true),
                        claims, revisions);

        assertFalse(recovering.escrowReady());
        assertTrue(ready.escrowReady());
        assertTrue(ready.revision() > recovering.revision());
    }

    @Test
    void walletAndCurrencyPresentationAdvanceTheProjectionRevision() {
        UUID owner = UUID.randomUUID();
        MarketCapabilityRevisionTracker revisions =
                new MarketCapabilityRevisionTracker(8);
        OpenClaimSourceCounts claims = new OpenClaimSourceCounts(
                0L, Map.of("auction.", 0L, "bazaar.", 0L));
        MarketCapabilitiesSnapshot first =
                MarketCapabilityProjectionService.project(
                        projectionWithWallet(owner, 4250L,
                                "Emerald Credits", 2), claims,
                        revisions);
        MarketCapabilitiesSnapshot repeated =
                MarketCapabilityProjectionService.project(
                        projectionWithWallet(owner, 4250L,
                                "Emerald Credits", 2), claims,
                        revisions);
        MarketCapabilitiesSnapshot changed =
                MarketCapabilityProjectionService.project(
                        projectionWithWallet(owner, 4249L,
                                "Emerald Credits", 2), claims,
                        revisions);

        assertEquals(4250L, first.walletBalanceMinorUnits());
        assertTrue(first.walletBalanceKnown());
        assertEquals("Emerald Credits", first.currencyName());
        assertEquals(2, first.currencyDecimals());
        assertEquals(first.revision(), repeated.revision());
        assertTrue(changed.revision() > repeated.revision());
    }

    @Test
    void recoveringEscrowStillAllowsSafeShopBrowsing() {
        MarketCapabilitiesSnapshot snapshot =
                MarketCapabilityProjectionService.project(
                        projection(UUID.randomUUID(), true, true,
                                false),
                        new OpenClaimSourceCounts(0L, Map.of(
                                "auction.", 0L, "bazaar.", 0L)),
                        new MarketCapabilityRevisionTracker(8));

        assertEquals(MarketModuleAvailability.ENABLED,
                snapshot.byModule().get(MarketModule.SHOP)
                        .availability());
        assertEquals(MarketModuleAvailability.DISABLED,
                snapshot.byModule().get(MarketModule.BAZAAR)
                        .availability());
        assertEquals(MarketModule.SHOP, snapshot.defaultModule());
    }

    @Test
    void categorizedClaimCounterOverflowFailsClosed() {
        assertThrows(ArithmeticException.class, () ->
                new MarketCapabilityProjector.ClaimCounts(
                        Long.MAX_VALUE, Long.MAX_VALUE, 1L));
    }

    @Test
    void durableLifecycleModesAreProjectedAndAdvanceRevision() {
        UUID owner = UUID.randomUUID();
        MarketCapabilityRevisionTracker revisions =
                new MarketCapabilityRevisionTracker(8);
        MarketControlState initial = MarketControlState.initial(0L);
        MarketCapabilitiesSnapshot enabled =
                MarketCapabilityProjectionService.project(
                        projection(owner, true, true, true, initial),
                        new OpenClaimSourceCounts(0L, Map.of(
                                "auction.", 0L, "bazaar.", 0L)),
                        revisions);
        MarketControlState controlled = transition(initial,
                MarketControlModule.SHOP,
                MarketModuleStatus.FROZEN, "shop.freeze", 10L,
                Optional.empty());
        controlled = transition(controlled,
                MarketControlModule.BAZAAR,
                MarketModuleStatus.DRAINING, "bazaar.drain", 20L,
                Optional.empty());
        controlled = transition(controlled,
                MarketControlModule.AUCTION_HOUSE,
                MarketModuleStatus.CANCEL_AND_REFUND,
                "auction.cancel", 30L,
                Optional.of(UUID.randomUUID()));
        MarketCapabilitiesSnapshot projected =
                MarketCapabilityProjectionService.project(
                        projection(owner, true, true, true,
                                controlled),
                        new OpenClaimSourceCounts(0L, Map.of(
                                "auction.", 0L, "bazaar.", 0L)),
                        revisions);

        assertEquals(MarketModuleAvailability.FROZEN,
                projected.byModule().get(MarketModule.SHOP)
                        .availability());
        assertEquals(MarketModuleAvailability.DRAINING,
                projected.byModule().get(MarketModule.BAZAAR)
                        .availability());
        assertEquals(MarketModuleAvailability.CANCEL_AND_REFUND,
                projected.byModule().get(MarketModule.AUCTION_HOUSE)
                        .availability());
        assertTrue(projected.revision() > enabled.revision());
        assertEquals(MarketModule.SHOP, projected.defaultModule());
    }

    @Test
    void disabledToggleFreezesAnOtherwiseEnabledModule() {
        MarketCapabilitiesSnapshot snapshot =
                MarketCapabilityProjectionService.project(
                        projection(UUID.randomUUID(), false, true,
                                true, MarketControlState.initial(0L)),
                        new OpenClaimSourceCounts(0L, Map.of(
                                "auction.", 0L, "bazaar.", 0L)),
                        new MarketCapabilityRevisionTracker(8));

        assertEquals(MarketModuleAvailability.FROZEN,
                snapshot.byModule().get(MarketModule.BAZAAR)
                        .availability());
    }

    private static MarketCapabilityProjectionService.Projection projection(
            UUID owner,
            boolean bazaarEnabled,
            boolean auctionEnabled,
            boolean escrowReady
    ) {
        return new MarketCapabilityProjectionService.Projection(
                UUID.randomUUID(), owner, true,
                MarketModule.AUCTION_HOUSE, escrowReady,
                bazaarEnabled, auctionEnabled,
                new MarketCapabilityProjector.Branding("Shop",
                        "#9184D9"),
                new MarketCapabilityProjector.Branding("Bazaar",
                        "#48B978"),
                new MarketCapabilityProjector.Branding("Auction House",
                        "#D85B68"));
    }

    private static MarketCapabilityProjectionService.Projection
            projectionWithWallet(
            UUID owner,
            long balance,
            String currencyName,
            int currencyDecimals
    ) {
        return new MarketCapabilityProjectionService.Projection(
                UUID.randomUUID(), owner, true,
                MarketModule.AUCTION_HOUSE, true, true, true,
                balance, true, currencyName, currencyDecimals,
                new MarketCapabilityProjector.Branding("Shop",
                        "#9184D9"),
                new MarketCapabilityProjector.Branding("Bazaar",
                        "#48B978"),
                new MarketCapabilityProjector.Branding("Auction House",
                        "#D85B68"),
                Optional.of(MarketControlState.initial(0L)));
    }

    private static MarketCapabilityProjectionService.Projection projection(
            UUID owner,
            boolean bazaarEnabled,
            boolean auctionEnabled,
            boolean escrowReady,
            MarketControlState control
    ) {
        return new MarketCapabilityProjectionService.Projection(
                UUID.randomUUID(), owner, true,
                MarketModule.AUCTION_HOUSE, escrowReady,
                bazaarEnabled, auctionEnabled,
                new MarketCapabilityProjector.Branding("Shop",
                        "#9184D9"),
                new MarketCapabilityProjector.Branding("Bazaar",
                        "#48B978"),
                new MarketCapabilityProjector.Branding("Auction House",
                        "#D85B68"), Optional.of(control));
    }

    private static MarketControlState transition(
            MarketControlState state,
            MarketControlModule module,
            MarketModuleStatus status,
            String key,
            long time,
            Optional<UUID> cancellationBatch
    ) {
        long revision = state.module(module).revision();
        MarketControlTransitionCommand command =
                new MarketControlTransitionCommand(
                        UUID.nameUUIDFromBytes(key.getBytes(
                                java.nio.charset.StandardCharsets.UTF_8)),
                        module, revision, status,
                        new MarketControlActor(UUID.randomUUID(),
                                "Operator"), key, time, time + 1L,
                        cancellationBatch, Optional.empty());
        return MarketControlRepository.transition(state, command)
                .state();
    }

    private static EscrowClaim claim(UUID owner, String source) {
        return claim(owner, source, ClaimKind.MONEY);
    }

    private static EscrowClaim claim(
            UUID owner,
            String source,
            ClaimKind kind
    ) {
        return new EscrowClaim(UUID.randomUUID(), UUID.randomUUID(),
                owner, source, kind, 1L, 1L,
                new byte[0], ClaimStatus.PENDING, "Claim",
                Instant.EPOCH, Instant.EPOCH);
    }
}
