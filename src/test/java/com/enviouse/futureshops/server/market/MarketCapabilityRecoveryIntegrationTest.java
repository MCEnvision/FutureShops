package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.client.market.MarketCapabilitiesSnapshot;
import com.enviouse.futureshops.client.market.MarketCapabilityResponseTracker;
import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.client.market.MarketModuleAvailability;
import com.enviouse.futureshops.network.packets.S2CMarketCapabilitiesPacket;
import com.enviouse.futureshops.server.escrow.claim.OpenClaimSourceCounts;
import com.enviouse.futureshops.server.market.control.MarketControlState;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketCapabilityRecoveryIntegrationTest {
    @Test
    void recoveryTransitionsAcrossProjectionCodecAndOverlappingRetry() {
        UUID ownerId = UUID.randomUUID();
        UUID recoveringRequest = UUID.randomUUID();
        UUID readyRequest = UUID.randomUUID();
        UUID overlappingRetry = UUID.randomUUID();
        MarketCapabilityRevisionTracker revisions =
                new MarketCapabilityRevisionTracker(8);
        MarketCapabilityResponseTracker responses =
                new MarketCapabilityResponseTracker(8);
        OpenClaimSourceCounts claims = new OpenClaimSourceCounts(
                0L, Map.of("auction.", 0L, "bazaar.", 0L));

        responses.begin(recoveringRequest);
        MarketCapabilitiesSnapshot recovering = roundTrip(
                MarketCapabilityProjectionService.project(
                        projection(recoveringRequest, ownerId, false),
                        claims, revisions));
        assertEquals(MarketCapabilityResponseTracker.Decision.ACCEPT,
                responses.accept(recovering));
        assertFalse(recovering.escrowReady());
        assertTrue(recovering.walletBalanceKnown());
        assertEquals(4250L, recovering.walletBalanceMinorUnits());
        assertEquals(MarketModuleAvailability.RECOVERING,
                recovering.byModule().get(MarketModule.BAZAAR)
                        .availability());
        assertTrue(recovering.byModule().get(MarketModule.BAZAAR)
                .availability().visible());

        responses.begin(readyRequest);
        responses.begin(overlappingRetry);
        MarketCapabilitiesSnapshot ready = roundTrip(
                MarketCapabilityProjectionService.project(
                        projection(readyRequest, ownerId, true),
                        claims, revisions));
        assertEquals(MarketCapabilityResponseTracker.Decision.ACCEPT,
                responses.accept(ready));
        assertTrue(ready.revision() > recovering.revision());
        assertTrue(responses.latest().orElseThrow().escrowReady());
        assertEquals(MarketModuleAvailability.ENABLED,
                ready.byModule().get(MarketModule.BAZAAR)
                        .availability());
        assertEquals(MarketModuleAvailability.ENABLED,
                ready.byModule().get(MarketModule.AUCTION_HOUSE)
                        .availability());
    }

    private static MarketCapabilitiesSnapshot roundTrip(
            MarketCapabilitiesSnapshot snapshot
    ) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(
                Unpooled.buffer());
        S2CMarketCapabilitiesPacket.encode(
                new S2CMarketCapabilitiesPacket(snapshot), buffer);
        return S2CMarketCapabilitiesPacket.decode(buffer).snapshot();
    }

    private static MarketCapabilityProjectionService.Projection projection(
            UUID requestId,
            UUID ownerId,
            boolean escrowReady
    ) {
        return new MarketCapabilityProjectionService.Projection(
                requestId, ownerId, true, MarketModule.BAZAAR,
                escrowReady, true, true, 4250L, true,
                "Credits", 2,
                new MarketCapabilityProjector.Branding("Shop",
                        "#9184D9"),
                new MarketCapabilityProjector.Branding("Bazaar",
                        "#48B978"),
                new MarketCapabilityProjector.Branding("Auction House",
                        "#D85B68"),
                Optional.of(MarketControlState.initial(0L)));
    }
}
