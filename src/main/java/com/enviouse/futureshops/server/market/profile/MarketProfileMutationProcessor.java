package com.enviouse.futureshops.server.market.profile;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.server.market.MarketModuleAccessPolicy;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import com.enviouse.futureshops.server.market.session.MarketServerSessionRegistry;
import com.enviouse.futureshops.server.market.session.MarketSessionDecision;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class MarketProfileMutationProcessor {
    private final MarketServerSessionRegistry sessions;

    public MarketProfileMutationProcessor(
            MarketServerSessionRegistry sessions
    ) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public synchronized MarketProfileMutationResult process(
            UUID playerId,
            MarketProfileMutationCommand command,
            long nowMillis,
            MarketProfileSavedData profiles,
            TargetCatalog targets,
            AccessState accessState
    ) {
        UUID player = Objects.requireNonNull(playerId, "playerId");
        MarketProfileMutationCommand request = Objects.requireNonNull(
                command, "command");
        MarketProfileSavedData data = Objects.requireNonNull(
                profiles, "profiles");
        TargetCatalog catalog = Objects.requireNonNull(
                targets, "targets");
        AccessState access = Objects.requireNonNull(
                accessState, "accessState");
        if (nowMillis < 0L) {
            throw new IllegalArgumentException(
                    "Market profile mutation time is invalid");
        }
        String fingerprint = request.fingerprint();
        Optional<MarketProfileMutationReceipt> persisted =
                data.mutationReceipt(player, request.requestId());
        if (persisted.isPresent()) {
            MarketProfileMutationReceipt receipt =
                    persisted.orElseThrow();
            if (!receipt.matches(player, request, fingerprint)) {
                return result(request,
                        MarketProfileMutationResultCode
                                .REQUEST_CONFLICT,
                        data.snapshot(player), 0, false);
            }
            return receipt.result().asReplay();
        }
        MarketProfileSavedData.RetiredMutationRequest retired =
                data.retiredMutationRequest(player,
                        request.requestId(), fingerprint);
        if (retired
                == MarketProfileSavedData.RetiredMutationRequest
                .CONFLICT) {
            return result(request,
                    MarketProfileMutationResultCode.REQUEST_CONFLICT,
                    data.snapshot(player), 0, false);
        }
        if (retired
                != MarketProfileSavedData.RetiredMutationRequest.NONE) {
            return result(request,
                    MarketProfileMutationResultCode
                            .REPLAY_UNAVAILABLE,
                    data.snapshot(player), 0, false);
        }
        MarketSessionDecision decision = sessions.accept(player,
                request.routeNonce(), request.module(), request.view(),
                request.requestId(), fingerprint, nowMillis);
        if (decision == MarketSessionDecision.REPLAY) {
            return result(request,
                    MarketProfileMutationResultCode
                            .REPLAY_UNAVAILABLE,
                    data.snapshot(player), 0, false);
        }
        if (decision != MarketSessionDecision.ACCEPT) {
            return result(request, sessionCode(decision),
                    data.snapshot(player), 0, false);
        }
        try {
            return accepted(player, request, fingerprint, nowMillis,
                    data, catalog, access);
        } catch (RuntimeException exception) {
            synchronized (data) {
                Optional<MarketProfileMutationReceipt> completed =
                        data.mutationReceipt(player,
                                request.requestId());
                if (completed.isPresent()) {
                    MarketProfileMutationReceipt receipt =
                            completed.orElseThrow();
                    return receipt.matches(player, request, fingerprint)
                            ? receipt.result().asReplay()
                            : result(request,
                            MarketProfileMutationResultCode
                                    .REQUEST_CONFLICT,
                            data.snapshot(player), 0, false);
                }
                return completeLocked(player, request, fingerprint,
                        MarketProfileMutationResultCode.SERVER_ERROR,
                        data.snapshot(player), 0, data);
            }
        }
    }

    private MarketProfileMutationResult accepted(
            UUID playerId,
            MarketProfileMutationCommand command,
            String fingerprint,
            long nowMillis,
            MarketProfileSavedData profiles,
            TargetCatalog targets,
            AccessState accessState
    ) {
        String requiredView = command.mutation().type()
                == MarketProfileMutationType.NOTIFICATIONS_READ
                ? "claims" : command.module().rootView();
        MarketModuleAccessPolicy.PageAccess access =
                MarketModuleAccessPolicy.pageAccess(command.module(),
                        requiredView, accessState.configuredEnabled(),
                        accessState.escrowReady(),
                        accessState.control());
        boolean allowed = access.allowed()
                && (command.mutation().type()
                == MarketProfileMutationType.NOTIFICATIONS_READ
                ? access.availability().allowsClaims()
                : access.availability().allowsBrowse());
        if (!allowed) {
            MarketProfileMutationResultCode code = access.allowed()
                    ? MarketProfileMutationResultCode.MODULE_DISABLED
                    : accessCode(access.denialCode());
            synchronized (profiles) {
                return completeLocked(playerId, command, fingerprint,
                        code, profiles.snapshot(playerId), 0,
                        profiles);
            }
        }
        synchronized (profiles) {
            MarketProfileSavedData.Snapshot before =
                    profiles.snapshot(playerId);
            if (before.revision()
                    != command.expectedProfileRevision()) {
                return completeLocked(playerId, command, fingerprint,
                        MarketProfileMutationResultCode.STALE_PROFILE,
                        before, 0, profiles);
            }
            Outcome outcome = mutate(playerId, command, nowMillis,
                    profiles, targets, before);
            MarketProfileSavedData.Snapshot after =
                    profiles.snapshot(playerId);
            long expectedRevision = outcome.code()
                    == MarketProfileMutationResultCode.SUCCESS
                    ? Math.incrementExact(before.revision())
                    : before.revision();
            if (after.revision() != expectedRevision) {
                throw new IllegalStateException(
                        "Market profile revision transition is invalid");
            }
            return completeLocked(playerId, command, fingerprint,
                    outcome.code(), after, outcome.affectedCount(),
                    profiles);
        }
    }

    private static Outcome mutate(
            UUID playerId,
            MarketProfileMutationCommand command,
            long nowMillis,
            MarketProfileSavedData profiles,
            TargetCatalog targets,
            MarketProfileSavedData.Snapshot before
    ) {
        MarketProfileMutation mutation = command.mutation();
        if (mutation instanceof MarketProfileMutation.AuctionWatch value) {
            boolean current = before.watchedAuctions().contains(
                    value.listingId());
            if (current == value.watched()) {
                return Outcome.noChange();
            }
            if (value.watched()
                    && !targets.auctionListingExists(value.listingId())) {
                return Outcome.failure(
                        MarketProfileMutationResultCode.TARGET_NOT_FOUND);
            }
            if (value.watched() && before.watchedAuctions().size()
                    >= MarketProfileSavedData.MAX_WATCHED_AUCTIONS) {
                return Outcome.failure(
                        MarketProfileMutationResultCode.LIMIT_REACHED);
            }
            return changed(profiles.setAuctionWatched(playerId,
                    value.listingId(), value.watched()), 1);
        }
        if (mutation instanceof
                MarketProfileMutation.BazaarFavorite value) {
            boolean current = before.favoriteProducts().contains(
                    value.product());
            if (current == value.favorite()) {
                return Outcome.noChange();
            }
            if (value.favorite()
                    && !targets.bazaarProductExists(value.product())) {
                return Outcome.failure(
                        MarketProfileMutationResultCode.TARGET_NOT_FOUND);
            }
            if (value.favorite() && before.favoriteProducts().size()
                    >= MarketProfileSavedData.MAX_FAVORITE_PRODUCTS) {
                return Outcome.failure(
                        MarketProfileMutationResultCode.LIMIT_REACHED);
            }
            return changed(profiles.setProductFavorite(playerId,
                    value.product(), value.favorite()), 1);
        }
        if (mutation instanceof
                MarketProfileMutation.PriceAlertAdd value) {
            if (!targets.bazaarProductExists(value.product())) {
                return Outcome.failure(
                        MarketProfileMutationResultCode.TARGET_NOT_FOUND);
            }
            Optional<MarketProfileSavedData.PriceAlert> existing =
                    before.priceAlerts().stream().filter(alert ->
                            alert.alertId().equals(value.alertId()))
                            .findFirst();
            if (existing.isPresent()) {
                MarketProfileSavedData.PriceAlert alert =
                        existing.orElseThrow();
                if (alert.product().equals(value.product())
                        && alert.direction() == value.direction()
                        && alert.thresholdMinor()
                        == value.thresholdMinor()
                        && alert.enabled()) {
                    return Outcome.noChange();
                }
                return Outcome.failure(
                        MarketProfileMutationResultCode.TARGET_CONFLICT);
            }
            if (before.priceAlerts().size()
                    >= MarketProfileSavedData.MAX_PRICE_ALERTS) {
                return Outcome.failure(
                        MarketProfileMutationResultCode.LIMIT_REACHED);
            }
            MarketProfileSavedData.PriceAlert alert =
                    new MarketProfileSavedData.PriceAlert(
                            value.alertId(), value.product(),
                            value.direction(), value.thresholdMinor(),
                            true, nowMillis, -1L);
            return changed(profiles.putPriceAlert(playerId, alert), 1);
        }
        if (mutation instanceof
                MarketProfileMutation.PriceAlertRemove value) {
            boolean exists = before.priceAlerts().stream().anyMatch(
                    alert -> alert.alertId().equals(value.alertId()));
            if (!exists) {
                return Outcome.noChange();
            }
            return changed(profiles.removePriceAlert(playerId,
                    value.alertId()), 1);
        }
        if (mutation instanceof
                MarketProfileMutation.NotificationsRead value) {
            int changed = profiles.markNotificationsRead(playerId,
                    profileModule(command.module()),
                    value.notificationIds());
            return changed == 0 ? Outcome.noChange()
                    : Outcome.success(changed);
        }
        throw new IllegalArgumentException(
                "Market profile mutation is invalid");
    }

    private static Outcome changed(boolean changed, int affectedCount) {
        if (!changed) {
            throw new IllegalStateException(
                    "Market profile mutation did not apply");
        }
        return Outcome.success(affectedCount);
    }

    private static MarketProfileSavedData.Module profileModule(
            MarketModule module
    ) {
        return switch (module) {
            case BAZAAR -> MarketProfileSavedData.Module.BAZAAR;
            case AUCTION_HOUSE ->
                    MarketProfileSavedData.Module.AUCTION_HOUSE;
            case SHOP -> throw new IllegalArgumentException(
                    "Shop has no market profile module");
        };
    }

    private static MarketProfileMutationResult result(
            MarketProfileMutationCommand command,
            MarketProfileMutationResultCode code,
            MarketProfileSavedData.Snapshot snapshot,
            int affectedCount,
            boolean replayed
    ) {
        return MarketProfileMutationResult.from(command, code,
                snapshot, affectedCount, replayed);
    }

    private static MarketProfileMutationResult completeLocked(
            UUID playerId,
            MarketProfileMutationCommand command,
            String fingerprint,
            MarketProfileMutationResultCode code,
            MarketProfileSavedData.Snapshot snapshot,
            int affectedCount,
            MarketProfileSavedData profiles
    ) {
        MarketProfileMutationResult response = result(command, code,
                snapshot, affectedCount, false);
        profiles.recordMutationReceipt(playerId,
                new MarketProfileMutationReceipt(playerId,
                        fingerprint, response));
        return response;
    }

    private static MarketProfileMutationResultCode sessionCode(
            MarketSessionDecision decision
    ) {
        return switch (decision) {
            case CONFLICT ->
                    MarketProfileMutationResultCode.REQUEST_CONFLICT;
            case MISSING ->
                    MarketProfileMutationResultCode.MISSING_SESSION;
            case STALE_ROUTE ->
                    MarketProfileMutationResultCode.STALE_ROUTE;
            case WRONG_MODULE ->
                    MarketProfileMutationResultCode.WRONG_MODULE;
            case WRONG_VIEW ->
                    MarketProfileMutationResultCode.WRONG_VIEW;
            case EXPIRED ->
                    MarketProfileMutationResultCode.SESSION_EXPIRED;
            case RATE_LIMITED ->
                    MarketProfileMutationResultCode.RATE_LIMITED;
            case ACCEPT, REPLAY -> throw new IllegalArgumentException(
                    "Accepted market session has no error code");
        };
    }

    private static MarketProfileMutationResultCode accessCode(
            String denialCode
    ) {
        return switch (denialCode) {
            case "MODULE_CONTROL_UNAVAILABLE" ->
                    MarketProfileMutationResultCode
                            .MODULE_CONTROL_UNAVAILABLE;
            case "ESCROW_NOT_READY" ->
                    MarketProfileMutationResultCode.ESCROW_NOT_READY;
            case "MODULE_FROZEN" ->
                    MarketProfileMutationResultCode.MODULE_FROZEN;
            case "MODULE_DRAINING" ->
                    MarketProfileMutationResultCode.MODULE_DRAINING;
            case "MODULE_CANCEL_AND_REFUND" ->
                    MarketProfileMutationResultCode
                            .MODULE_CANCEL_AND_REFUND;
            case "CLAIMS_ONLY" ->
                    MarketProfileMutationResultCode.CLAIMS_ONLY;
            case "MODULE_DISABLED" ->
                    MarketProfileMutationResultCode.MODULE_DISABLED;
            default -> MarketProfileMutationResultCode.VIEW_DISABLED;
        };
    }

    public interface TargetCatalog {
        boolean auctionListingExists(UUID listingId);

        boolean bazaarProductExists(
                MarketProfileSavedData.ProductKey product);
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

    private record Outcome(
            MarketProfileMutationResultCode code,
            int affectedCount
    ) {
        private Outcome {
            code = Objects.requireNonNull(code, "code");
            if (affectedCount < 0) {
                throw new IllegalArgumentException(
                        "Market profile affected count is invalid");
            }
        }

        private static Outcome success(int affectedCount) {
            return new Outcome(
                    MarketProfileMutationResultCode.SUCCESS,
                    affectedCount);
        }

        private static Outcome noChange() {
            return new Outcome(
                    MarketProfileMutationResultCode.NO_CHANGE, 0);
        }

        private static Outcome failure(
                MarketProfileMutationResultCode code
        ) {
            return new Outcome(code, 0);
        }
    }
}
