package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.client.market.MarketModuleAvailability;
import com.enviouse.futureshops.server.market.control.MarketControlActor;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketControlRepository;
import com.enviouse.futureshops.server.market.control.MarketControlState;
import com.enviouse.futureshops.server.market.control.MarketControlTransitionCommand;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import com.enviouse.futureshops.server.market.control.MarketModuleStatus;
import com.enviouse.futureshops.server.market.query.MarketPageCard;
import com.enviouse.futureshops.server.market.query.MarketPageCardKind;
import com.enviouse.futureshops.server.market.query.MarketPageSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketModuleAccessPolicyTest {
    @Test
    void lifecycleModesGateViewsWithoutBlockingClaims() {
        MarketModuleControl frozen = control(
                MarketControlModule.AUCTION_HOUSE,
                MarketModuleStatus.FROZEN);
        assertAllowed(MarketModule.AUCTION_HOUSE, "browse", frozen,
                MarketModuleAvailability.FROZEN);
        assertAllowed(MarketModule.AUCTION_HOUSE, "claims", frozen,
                MarketModuleAvailability.FROZEN);
        assertAllowed(MarketModule.AUCTION_HOUSE, "listing_detail",
                frozen, MarketModuleAvailability.FROZEN);
        assertDenied(MarketModule.AUCTION_HOUSE, "mine", frozen,
                "MODULE_FROZEN");
        assertDenied(MarketModule.AUCTION_HOUSE, "create", frozen,
                "MODULE_FROZEN");

        MarketModuleControl draining = control(
                MarketControlModule.AUCTION_HOUSE,
                MarketModuleStatus.DRAINING);
        assertAllowed(MarketModule.AUCTION_HOUSE, "browse", draining,
                MarketModuleAvailability.DRAINING);
        assertAllowed(MarketModule.AUCTION_HOUSE, "mine", draining,
                MarketModuleAvailability.DRAINING);
        assertAllowed(MarketModule.AUCTION_HOUSE, "listing_detail",
                draining, MarketModuleAvailability.DRAINING);
        assertDenied(MarketModule.AUCTION_HOUSE, "create", draining,
                "MODULE_DRAINING");

        MarketModuleControl cancelling = control(
                MarketControlModule.BAZAAR,
                MarketModuleStatus.CANCEL_AND_REFUND);
        assertAllowed(MarketModule.BAZAAR, "orders", cancelling,
                MarketModuleAvailability.CANCEL_AND_REFUND);
        assertAllowed(MarketModule.BAZAAR, "claims", cancelling,
                MarketModuleAvailability.CANCEL_AND_REFUND);
        assertDenied(MarketModule.BAZAAR, "product_detail",
                cancelling, "MODULE_CANCEL_AND_REFUND");
        assertDenied(MarketModule.BAZAAR, "products", cancelling,
                "MODULE_CANCEL_AND_REFUND");
    }

    @Test
    void unavailableRuntimeAndControlFailClosedExceptForClaims() {
        MarketModuleControl enabled = control(
                MarketControlModule.BAZAAR,
                MarketModuleStatus.ENABLED);
        MarketModuleAccessPolicy.PageAccess unavailableRuntime =
                MarketModuleAccessPolicy.pageAccess(
                        MarketModule.BAZAAR, "products", true, false,
                        Optional.of(enabled));
        assertFalse(unavailableRuntime.allowed());
        assertEquals("ESCROW_NOT_READY",
                unavailableRuntime.denialCode());
        assertTrue(MarketModuleAccessPolicy.pageAccess(
                MarketModule.BAZAAR, "claims", true, false,
                Optional.of(enabled)).allowed());
        assertFalse(MarketModuleAccessPolicy.pageAccess(
                MarketModule.BAZAAR, "product_detail", true, false,
                Optional.of(enabled)).allowed());

        MarketModuleAccessPolicy.PageAccess missingControl =
                MarketModuleAccessPolicy.pageAccess(
                        MarketModule.BAZAAR, "products", true, true,
                        Optional.empty());
        assertFalse(missingControl.allowed());
        assertEquals("MODULE_CONTROL_UNAVAILABLE",
                missingControl.denialCode());
        assertEquals(MarketModuleAvailability.CLAIMS_ONLY,
                MarketModuleAccessPolicy.capability(
                        MarketModule.BAZAAR, true, false,
                        Optional.of(enabled), 2L));
        assertEquals(MarketModuleAvailability.DISABLED,
                MarketModuleAccessPolicy.capability(
                        MarketModule.BAZAAR, true, true,
                        Optional.empty(), 0L));
    }

    @Test
    void disabledToggleBecomesFrozenWithoutErasingStrongerMode() {
        MarketModuleControl enabled = control(
                MarketControlModule.BAZAAR,
                MarketModuleStatus.ENABLED);
        assertEquals(MarketModuleAvailability.FROZEN,
                MarketModuleAccessPolicy.capability(
                        MarketModule.BAZAAR, false, true,
                        Optional.of(enabled), 0L));
        MarketModuleControl draining = control(
                MarketControlModule.BAZAAR,
                MarketModuleStatus.DRAINING);
        assertEquals(MarketModuleAvailability.DRAINING,
                MarketModuleAccessPolicy.capability(
                        MarketModule.BAZAAR, false, true,
                        Optional.of(draining), 0L));
        assertEquals("products",
                MarketModuleAccessPolicy.preferredView(
                        MarketModule.BAZAAR,
                        MarketModuleAvailability.FROZEN));
        assertEquals("mine",
                MarketModuleAccessPolicy.preferredView(
                        MarketModule.AUCTION_HOUSE,
                        MarketModuleAvailability.CANCEL_AND_REFUND));
        assertEquals("claims",
                MarketModuleAccessPolicy.preferredView(
                        MarketModule.BAZAAR,
                        MarketModuleAvailability.CLAIMS_ONLY));
    }

    @Test
    void readOnlyPagesStripValueActionsAndKeepOnlyCancellation() {
        MarketPageSnapshot auction = page(
                MarketModule.AUCTION_HOUSE, "mine",
                new MarketPageCard(MarketPageCardKind.AUCTION,
                        "auction", Optional.of(UUID.randomUUID()),
                        "minecraft:diamond", 1, "Diamond", "items",
                        "ACTIVE", 1L, 10L, 11L, 1L, 100L, false,
                        true, true));
        MarketPageSnapshot draining = MarketModuleAccessPolicy
                .applyPageActions(auction,
                        MarketModuleAvailability.DRAINING);
        assertFalse(draining.cards().get(0).primaryAction());
        assertTrue(draining.cards().get(0).secondaryAction());

        MarketPageSnapshot frozen = MarketModuleAccessPolicy
                .applyPageActions(page(MarketModule.AUCTION_HOUSE,
                        "browse", auction.cards().get(0)),
                        MarketModuleAvailability.FROZEN);
        assertFalse(frozen.cards().get(0).primaryAction());
        assertFalse(frozen.cards().get(0).secondaryAction());

        MarketPageSnapshot bazaar = page(MarketModule.BAZAAR,
                "orders", new MarketPageCard(
                        MarketPageCardKind.BAZAAR_ORDER, "order",
                        Optional.of(UUID.randomUUID()), "", 0,
                        "Diamond", "", "OPEN", 1L, 10L, 10L,
                        4L, 100L, false, true, true));
        MarketPageSnapshot cancelling = MarketModuleAccessPolicy
                .applyPageActions(bazaar,
                        MarketModuleAvailability.CANCEL_AND_REFUND);
        assertTrue(cancelling.cards().get(0).primaryAction());
        assertFalse(cancelling.cards().get(0).secondaryAction());
        assertSame(auction, MarketModuleAccessPolicy.applyPageActions(
                auction, MarketModuleAvailability.ENABLED));
    }

    @Test
    void everyClaimCapableModePreservesClaimCollectionActions() {
        MarketPageSnapshot claims = page(
                MarketModule.AUCTION_HOUSE, "claims",
                new MarketPageCard(MarketPageCardKind.CLAIM,
                        "claim", Optional.of(UUID.randomUUID()), "",
                        0, "Refund", "auction.refund", "PENDING",
                        1L, 10L, 0L, 0L, 0L, false, true, false));

        for (MarketModuleAvailability availability : List.of(
                MarketModuleAvailability.FROZEN,
                MarketModuleAvailability.DRAINING,
                MarketModuleAvailability.CLAIMS_ONLY,
                MarketModuleAvailability.CANCEL_AND_REFUND)) {
            MarketPageSnapshot projected = MarketModuleAccessPolicy
                    .applyPageActions(claims, availability);
            assertTrue(projected.cards().get(0).primaryAction(),
                    availability.name());
        }
        MarketPageSnapshot disabled = MarketModuleAccessPolicy
                .applyPageActions(claims,
                        MarketModuleAvailability.DISABLED);
        assertFalse(disabled.cards().get(0).primaryAction());
    }

    private static void assertAllowed(
            MarketModule module,
            String view,
            MarketModuleControl control,
            MarketModuleAvailability availability
    ) {
        MarketModuleAccessPolicy.PageAccess access =
                MarketModuleAccessPolicy.pageAccess(module, view, true,
                        true, Optional.of(control));
        assertTrue(access.allowed());
        assertEquals(availability, access.availability());
    }

    private static void assertDenied(
            MarketModule module,
            String view,
            MarketModuleControl control,
            String code
    ) {
        MarketModuleAccessPolicy.PageAccess access =
                MarketModuleAccessPolicy.pageAccess(module, view, true,
                        true, Optional.of(control));
        assertFalse(access.allowed());
        assertEquals(code, access.denialCode());
    }

    private static MarketPageSnapshot page(
            MarketModule module,
            String view,
            MarketPageCard card
    ) {
        return new MarketPageSnapshot(UUID.randomUUID(),
                UUID.randomUUID(), module, view, 0, 10, 1, 1, 1L,
                100L, 0, card.primaryMinor(), card.quantity(),
                List.of(), List.of(card));
    }

    private static MarketModuleControl control(
            MarketControlModule module,
            MarketModuleStatus status
    ) {
        MarketControlState state = MarketControlState.initial(0L);
        if (status == MarketModuleStatus.ENABLED) {
            return state.module(module);
        }
        Optional<UUID> batch = status
                == MarketModuleStatus.CANCEL_AND_REFUND
                ? Optional.of(UUID.randomUUID()) : Optional.empty();
        MarketControlTransitionCommand command =
                new MarketControlTransitionCommand(UUID.randomUUID(),
                        module, 0L, status,
                        new MarketControlActor(UUID.randomUUID(),
                                "Operator"), "Policy test", 10L, 11L,
                        batch, Optional.empty());
        return MarketControlRepository.transition(state, command)
                .state().module(module);
    }
}
