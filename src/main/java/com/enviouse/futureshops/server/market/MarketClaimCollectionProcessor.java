package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.server.escrow.claim.ClaimStatus;
import com.enviouse.futureshops.server.escrow.claim.EscrowClaim;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionCode;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionCommand;
import com.enviouse.futureshops.server.market.claim.MarketClaimCollectionResult;
import com.enviouse.futureshops.server.market.claim.MarketClaimDeliveryOutcome;
import com.enviouse.futureshops.server.market.claim.MarketClaimPresentationKind;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import com.enviouse.futureshops.server.market.session.MarketServerSessionRegistry;
import com.enviouse.futureshops.server.market.session.MarketSessionDecision;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MarketClaimCollectionProcessor {
    private final MarketServerSessionRegistry sessions;
    private final CollectionBackend backend;

    public MarketClaimCollectionProcessor(
            MarketServerSessionRegistry sessions,
            CollectionBackend backend
    ) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    public synchronized MarketClaimCollectionResult process(
            UUID playerId,
            MarketClaimCollectionCommand command,
            long nowMillis,
            AccessState accessState
    ) {
        UUID player = Objects.requireNonNull(playerId, "playerId");
        MarketClaimCollectionCommand request = Objects.requireNonNull(
                command, "command");
        AccessState access = Objects.requireNonNull(
                accessState, "accessState");
        if (nowMillis < 0L) {
            throw new IllegalArgumentException(
                    "Market claim collection time is invalid");
        }
        MarketSessionDecision session = sessions.accept(player,
                request.routeNonce(), request.module(), request.view(),
                request.requestId(), request.fingerprint(), nowMillis);
        if (session != MarketSessionDecision.ACCEPT
                && session != MarketSessionDecision.REPLAY) {
            return MarketClaimCollectionResult.failure(request,
                    sessionCode(session));
        }
        MarketModuleAccessPolicy.PageAccess pageAccess =
                MarketModuleAccessPolicy.pageAccess(request.module(),
                        request.view(), access.configuredEnabled(),
                        access.escrowReady(), access.control());
        if (!pageAccess.allowed()
                || !pageAccess.availability().allowsClaims()) {
            return MarketClaimCollectionResult.failure(request,
                    MarketClaimCollectionCode.MODULE_UNAVAILABLE);
        }
        EscrowClaim claim = backend.claim(request.claimId());
        if (!collectibleBy(player, request.module(), claim)) {
            return MarketClaimCollectionResult.failure(request,
                    MarketClaimCollectionCode.NOT_FOUND);
        }
        MarketClaimPresentationKind kind =
                MarketClaimPresentationKind.from(claim);
        if (!kind.collectible()) {
            return MarketClaimCollectionResult.failure(request,
                    MarketClaimCollectionCode.NOT_FOUND);
        }
        if (claim.status() == ClaimStatus.QUARANTINED) {
            return result(request, kind,
                    MarketClaimDeliveryOutcome.failure(
                            MarketClaimCollectionCode.RECOVERY_REQUIRED,
                            claim.remainingUnits()));
        }
        try {
            MarketClaimDeliveryOutcome outcome = dispatch(
                    player, request, claim);
            return result(request, kind, outcome);
        } catch (RuntimeException exception) {
            return result(request, kind,
                    MarketClaimDeliveryOutcome.failure(
                            MarketClaimCollectionCode.SERVER_ERROR,
                            claim.remainingUnits()));
        }
    }

    public static boolean sourceAllowed(
            MarketModule module,
            String sourceKey
    ) {
        MarketModule market = Objects.requireNonNull(module, "module");
        String source = Objects.requireNonNull(sourceKey, "sourceKey");
        boolean bazaar = source.startsWith("bazaar.");
        boolean auction = source.startsWith("auction.");
        return switch (market) {
            case BAZAAR -> bazaar;
            case AUCTION_HOUSE -> auction;
            case SHOP -> !bazaar && !auction;
        };
    }

    private MarketClaimDeliveryOutcome dispatch(
            UUID playerId,
            MarketClaimCollectionCommand command,
            EscrowClaim claim
    ) {
        return switch (claim.kind()) {
            case MONEY -> backend.collectMoney(playerId, claim,
                    command.requestId());
            case ITEM, BARTER_ITEM -> backend.collectItem(playerId,
                    claim, command.requestId());
            case PROTECTED_CASH, FOREIGN_CASH ->
                    backend.collectCash(playerId, claim,
                            command.requestId());
            case REFUND -> claim.payload().length == 0
                    ? backend.collectMoney(playerId, claim,
                    command.requestId())
                    : backend.collectItem(playerId, claim,
                    command.requestId());
            case INTERNAL_ESCROW_MONEY ->
                    MarketClaimDeliveryOutcome.failure(
                            MarketClaimCollectionCode.NOT_FOUND, 0L);
        };
    }

    private static boolean collectibleBy(
            UUID playerId,
            MarketModule module,
            EscrowClaim claim
    ) {
        return claim != null
                && claim.ownerId().equals(playerId)
                && claim.kind().publiclyVisible()
                && sourceAllowed(module, claim.sourceKey());
    }

    private static MarketClaimCollectionResult result(
            MarketClaimCollectionCommand command,
            MarketClaimPresentationKind kind,
            MarketClaimDeliveryOutcome outcome
    ) {
        return new MarketClaimCollectionResult(command.requestId(),
                command.routeNonce(), command.module(), command.view(),
                command.claimId(), kind, outcome.code(),
                outcome.deliveredUnits(), outcome.remainingUnits(),
                outcome.resultingBalanceMinor(), outcome.replayed(),
                outcome.code().refreshClaims());
    }

    private static MarketClaimCollectionCode sessionCode(
            MarketSessionDecision decision
    ) {
        return switch (decision) {
            case CONFLICT ->
                    MarketClaimCollectionCode.REQUEST_CONFLICT;
            case MISSING ->
                    MarketClaimCollectionCode.MISSING_SESSION;
            case STALE_ROUTE ->
                    MarketClaimCollectionCode.STALE_ROUTE;
            case WRONG_MODULE ->
                    MarketClaimCollectionCode.WRONG_MODULE;
            case WRONG_VIEW ->
                    MarketClaimCollectionCode.WRONG_VIEW;
            case EXPIRED ->
                    MarketClaimCollectionCode.SESSION_EXPIRED;
            case RATE_LIMITED ->
                    MarketClaimCollectionCode.RATE_LIMITED;
            case ACCEPT, REPLAY -> throw new IllegalArgumentException(
                    "Accepted market claim session has no error code");
        };
    }

    public interface CollectionBackend {
        EscrowClaim claim(UUID claimId);

        MarketClaimDeliveryOutcome collectMoney(
                UUID playerId,
                EscrowClaim claim,
                UUID requestId);

        MarketClaimDeliveryOutcome collectItem(
                UUID playerId,
                EscrowClaim claim,
                UUID requestId);

        MarketClaimDeliveryOutcome collectCash(
                UUID playerId,
                EscrowClaim claim,
                UUID requestId);
    }

    public record AccessState(
            boolean configuredEnabled,
            boolean escrowReady,
            Optional<MarketModuleControl> control
    ) {
        public AccessState {
            control = Objects.requireNonNull(control, "control");
        }
    }
}
