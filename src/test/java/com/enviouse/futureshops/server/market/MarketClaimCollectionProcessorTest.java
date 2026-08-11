package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.server.escrow.claim.ClaimKind;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionCode;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionCommand;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionResult;
import com.enviouse.futureshops.server.market.claim.MarketClaimDeliveryOutcome;
import com.enviouse.futureshops.server.market.claim.MarketClaimPresentationKind;
import com.enviouse.futureshops.server.market.control.MarketControlActor;
import com.enviouse.futureshops.server.market.control.MarketControlModule;
import com.enviouse.futureshops.server.market.control.MarketControlRepository;
import com.enviouse.futureshops.server.market.control.MarketControlState;
import com.enviouse.futureshops.server.market.control.MarketControlTransitionCommand;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import com.enviouse.futureshops.server.market.control.MarketModuleStatus;
import com.enviouse.futureshops.server.market.session.MarketServerSessionRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketClaimCollectionProcessorTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void everyPublicKindUsesItsExactCollector() {
        Fixture fixture = fixture(MarketModule.BAZAAR, "claims");
        List<EscrowClaim> claims = List.of(
                claim(ClaimKind.MONEY, new byte[0], "bazaar.money"),
                claim(ClaimKind.ITEM, new byte[]{1}, "bazaar.item"),
                claim(ClaimKind.PROTECTED_CASH, new byte[]{1},
                        "bazaar.protected"),
                claim(ClaimKind.FOREIGN_CASH, new byte[]{1},
                        "bazaar.foreign"),
                claim(ClaimKind.BARTER_ITEM, new byte[]{1},
                        "bazaar.barter"),
                claim(ClaimKind.REFUND, new byte[0],
                        "bazaar.money.refund"),
                claim(ClaimKind.REFUND, new byte[]{1},
                        "bazaar.item.refund"));
        claims.forEach(fixture.backend()::put);

        List<MarketClaimPresentationKind> kinds = new ArrayList<>();
        for (EscrowClaim claim : claims) {
            MarketClaimCollectionResult result = fixture.process(
                    command(fixture.route(), MarketModule.BAZAAR,
                            claim.claimId()));
            assertEquals(MarketClaimCollectionCode.COLLECTED,
                    result.code());
            kinds.add(result.kind());
        }

        assertEquals(2, fixture.backend().moneyCalls);
        assertEquals(3, fixture.backend().itemCalls);
        assertEquals(2, fixture.backend().cashCalls);
        assertEquals(List.of(MarketClaimPresentationKind.MONEY,
                MarketClaimPresentationKind.ITEM,
                MarketClaimPresentationKind.PROTECTED_CASH,
                MarketClaimPresentationKind.FOREIGN_CASH,
                MarketClaimPresentationKind.BARTER_ITEM,
                MarketClaimPresentationKind.MONEY_REFUND,
                MarketClaimPresentationKind.ITEM_REFUND), kinds);
    }

    @Test
    void ownerSourceInternalAndQuarantineChecksRunBeforeDispatch() {
        Fixture fixture = fixture(MarketModule.BAZAAR, "claims");
        EscrowClaim otherOwner = claim(UUID.randomUUID(),
                ClaimKind.MONEY, new byte[0], "bazaar.other",
                ClaimStatus.PENDING, 5L, 5L);
        EscrowClaim wrongSource = claim(ClaimKind.MONEY,
                new byte[0], "auction.refund");
        EscrowClaim internal = claim(ClaimKind.INTERNAL_ESCROW_MONEY,
                new byte[0], "bazaar.internal");
        EscrowClaim quarantined = claim(PLAYER, ClaimKind.ITEM,
                new byte[]{1}, "bazaar.quarantine",
                ClaimStatus.QUARANTINED, 5L, 5L);
        List.of(otherOwner, wrongSource, internal, quarantined)
                .forEach(fixture.backend()::put);

        for (EscrowClaim hidden : List.of(otherOwner, wrongSource,
                internal)) {
            MarketClaimCollectionResult result = fixture.process(
                    command(fixture.route(), MarketModule.BAZAAR,
                            hidden.claimId()));
            assertEquals(MarketClaimCollectionCode.NOT_FOUND,
                    result.code());
            assertEquals(MarketClaimPresentationKind.UNKNOWN,
                    result.kind());
        }
        MarketClaimCollectionResult recovery = fixture.process(
                command(fixture.route(), MarketModule.BAZAAR,
                        quarantined.claimId()));
        assertEquals(MarketClaimCollectionCode.RECOVERY_REQUIRED,
                recovery.code());
        assertEquals(5L, recovery.remainingUnits());
        assertTrue(recovery.refreshClaims());
        assertEquals(0, fixture.backend().totalCalls());
    }

    @Test
    void exactReplayRerunsIdempotentCollectorAndConflictDoesNot() {
        Fixture fixture = fixture(MarketModule.BAZAAR, "claims");
        EscrowClaim first = claim(ClaimKind.ITEM, new byte[]{1},
                "bazaar.first");
        EscrowClaim second = claim(ClaimKind.ITEM, new byte[]{1},
                "bazaar.second");
        fixture.backend().put(first);
        fixture.backend().put(second);
        fixture.backend().outcome = MarketClaimDeliveryOutcome.failure(
                MarketClaimCollectionCode.INVENTORY_FULL, 5L);
        MarketClaimCollectionCommand command = command(fixture.route(),
                MarketModule.BAZAAR, first.claimId());

        assertEquals(MarketClaimCollectionCode.INVENTORY_FULL,
                fixture.process(command).code());
        assertEquals(MarketClaimCollectionCode.INVENTORY_FULL,
                fixture.process(command).code());
        assertEquals(2, fixture.backend().itemCalls);

        MarketClaimCollectionCommand conflict =
                new MarketClaimCollectionCommand(command.requestId(),
                        command.routeNonce(), command.module(),
                        command.view(), second.claimId());
        assertEquals(MarketClaimCollectionCode.REQUEST_CONFLICT,
                fixture.process(conflict).code());
        assertEquals(2, fixture.backend().itemCalls);
    }

    @Test
    void staleRouteWrongViewAndMissingSessionNeverDispatch() {
        FakeBackend backend = new FakeBackend();
        EscrowClaim claim = claim(ClaimKind.MONEY, new byte[0],
                "bazaar.money");
        backend.put(claim);
        MarketServerSessionRegistry sessions = sessions();
        MarketClaimCollectionProcessor processor =
                new MarketClaimCollectionProcessor(sessions, backend);
        UUID oldRoute = UUID.randomUUID();
        sessions.open(PLAYER, MarketModule.BAZAAR, "claims",
                oldRoute, 0L);
        UUID currentRoute = UUID.randomUUID();
        sessions.open(PLAYER, MarketModule.BAZAAR, "claims",
                currentRoute, 1L);
        assertEquals(MarketClaimCollectionCode.STALE_ROUTE,
                processor.process(PLAYER, command(oldRoute,
                                MarketModule.BAZAAR, claim.claimId()),
                        2L, access()).code());

        sessions.open(PLAYER, MarketModule.BAZAAR, "products",
                currentRoute, 3L);
        assertEquals(MarketClaimCollectionCode.WRONG_VIEW,
                processor.process(PLAYER, command(currentRoute,
                                MarketModule.BAZAAR, claim.claimId()),
                        4L, access()).code());

        sessions.clear();
        assertEquals(MarketClaimCollectionCode.MISSING_SESSION,
                processor.process(PLAYER, command(UUID.randomUUID(),
                                MarketModule.BAZAAR, claim.claimId()),
                        5L, access()).code());
        assertEquals(0, backend.totalCalls());
    }

    @Test
    void everyLifecycleModeKeepsOwnedClaimsCollectible() {
        for (MarketModuleStatus status : MarketModuleStatus.values()) {
            Fixture fixture = fixture(MarketModule.BAZAAR, "claims");
            EscrowClaim claim = claim(ClaimKind.MONEY, new byte[0],
                    "bazaar.lifecycle");
            fixture.backend().put(claim);
            fixture.backend().outcome =
                    MarketClaimDeliveryOutcome.failure(
                            MarketClaimCollectionCode.WALLET_FULL, 5L);
            MarketClaimCollectionResult result = fixture.processor()
                    .process(PLAYER, command(fixture.route(),
                                    MarketModule.BAZAAR,
                                    claim.claimId()), 1L,
                            new MarketClaimCollectionProcessor.AccessState(
                                    true, true,
                                    Optional.of(control(status))));
            assertEquals(MarketClaimCollectionCode.WALLET_FULL,
                    result.code(), status.name());
        }
    }

    @Test
    void moduleSourceIsolationIncludesTheShopRemainder() {
        assertTrue(MarketClaimCollectionProcessor.sourceAllowed(
                MarketModule.BAZAAR, "bazaar.order.refund"));
        assertFalse(MarketClaimCollectionProcessor.sourceAllowed(
                MarketModule.BAZAAR, "auction.sale.refund"));
        assertTrue(MarketClaimCollectionProcessor.sourceAllowed(
                MarketModule.AUCTION_HOUSE,
                "auction.sale.refund"));
        assertFalse(MarketClaimCollectionProcessor.sourceAllowed(
                MarketModule.AUCTION_HOUSE,
                "server.shop.purchase"));
        assertTrue(MarketClaimCollectionProcessor.sourceAllowed(
                MarketModule.SHOP, "server.shop.purchase"));
        assertFalse(MarketClaimCollectionProcessor.sourceAllowed(
                MarketModule.SHOP, "bazaar.order.refund"));
        assertFalse(MarketClaimCollectionProcessor.sourceAllowed(
                MarketModule.SHOP, "auction.sale.refund"));
    }

    private static Fixture fixture(
            MarketModule module,
            String view
    ) {
        MarketServerSessionRegistry sessions = sessions();
        UUID route = UUID.randomUUID();
        sessions.open(PLAYER, module, view, route, 0L);
        FakeBackend backend = new FakeBackend();
        return new Fixture(route,
                new MarketClaimCollectionProcessor(sessions, backend),
                backend);
    }

    private static MarketServerSessionRegistry sessions() {
        return new MarketServerSessionRegistry(Duration.ofMinutes(5),
                64, Duration.ofSeconds(1));
    }

    private static MarketClaimCollectionProcessor.AccessState access() {
        return new MarketClaimCollectionProcessor.AccessState(
                true, true, Optional.empty());
    }

    private static MarketClaimCollectionCommand command(
            UUID route,
            MarketModule module,
            UUID claimId
    ) {
        return new MarketClaimCollectionCommand(UUID.randomUUID(), route,
                module, "claims", claimId);
    }

    private static EscrowClaim claim(
            ClaimKind kind,
            byte[] payload,
            String source
    ) {
        return claim(PLAYER, kind, payload, source,
                ClaimStatus.PENDING, 5L, 5L);
    }

    private static EscrowClaim claim(
            UUID owner,
            ClaimKind kind,
            byte[] payload,
            String source,
            ClaimStatus status,
            long original,
            long remaining
    ) {
        Instant now = Instant.ofEpochSecond(10L);
        return new EscrowClaim(UUID.randomUUID(), UUID.randomUUID(),
                owner, source, kind, original, remaining, payload,
                status, "Claim", now, now);
    }

    private static MarketModuleControl control(
            MarketModuleStatus status
    ) {
        MarketControlModule module = MarketControlModule.BAZAAR;
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
                                "Operator"), "Claim test", 10L, 11L,
                        batch, Optional.empty());
        return MarketControlRepository.transition(state, command)
                .state().module(module);
    }

    private record Fixture(
            UUID route,
            MarketClaimCollectionProcessor processor,
            FakeBackend backend
    ) {
        private MarketClaimCollectionResult process(
                MarketClaimCollectionCommand command
        ) {
            return processor.process(PLAYER, command, 1L, access());
        }
    }

    private static final class FakeBackend implements
            MarketClaimCollectionProcessor.CollectionBackend {
        private final Map<UUID, EscrowClaim> claims =
                new LinkedHashMap<>();
        private MarketClaimDeliveryOutcome outcome =
                new MarketClaimDeliveryOutcome(
                        MarketClaimCollectionCode.COLLECTED, 1L, 0L,
                        OptionalLong.empty(), false);
        private int moneyCalls;
        private int itemCalls;
        private int cashCalls;

        private void put(EscrowClaim claim) {
            claims.put(claim.claimId(), claim);
        }

        private int totalCalls() {
            return moneyCalls + itemCalls + cashCalls;
        }

        @Override
        public EscrowClaim claim(UUID claimId) {
            return claims.get(claimId);
        }

        @Override
        public MarketClaimDeliveryOutcome collectMoney(
                UUID playerId,
                EscrowClaim claim,
                UUID requestId
        ) {
            moneyCalls++;
            return outcome;
        }

        @Override
        public MarketClaimDeliveryOutcome collectItem(
                UUID playerId,
                EscrowClaim claim,
                UUID requestId
        ) {
            itemCalls++;
            return outcome;
        }

        @Override
        public MarketClaimDeliveryOutcome collectCash(
                UUID playerId,
                EscrowClaim claim,
                UUID requestId
        ) {
            cashCalls++;
            return outcome;
        }
    }
}
