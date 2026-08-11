package com.enviouse.futureshops.server.market;

import com.enviouse.futureshops.client.market.MarketModule;
import com.enviouse.futureshops.client.market.MarketModuleAvailability;
import com.enviouse.futureshops.client.market.MarketRoute;
import com.enviouse.futureshops.server.market.control.MarketModuleControl;
import com.enviouse.futureshops.server.market.control.MarketModuleStatus;
import com.enviouse.futureshops.server.market.query.MarketPageCard;
import com.enviouse.futureshops.server.market.query.MarketPageCardKind;
import com.enviouse.futureshops.server.market.query.MarketPageSnapshot;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class MarketModuleAccessPolicy {
    private MarketModuleAccessPolicy() {
    }

    public static MarketModuleAvailability capability(
            MarketModule module,
            boolean configuredEnabled,
            boolean escrowReady,
            Optional<MarketModuleControl> control,
            long openClaims
    ) {
        Objects.requireNonNull(module, "module");
        Optional<MarketModuleControl> persisted = Objects.requireNonNull(
                control, "control");
        if (openClaims < 0L) {
            throw new IllegalArgumentException(
                    "Market module claim count is invalid");
        }
        if (!configuredEnabled) {
            return openClaims > 0L
                    ? MarketModuleAvailability.CLAIMS_ONLY
                    : MarketModuleAvailability.HIDDEN;
        }
        if (persisted.isEmpty()
                || module != MarketModule.SHOP && !escrowReady) {
            return MarketModuleAvailability.RECOVERING;
        }
        return availability(effectiveStatus(configuredEnabled,
                persisted.orElseThrow().status()));
    }

    public static PageAccess pageAccess(
            MarketModule module,
            String view,
            boolean configuredEnabled,
            boolean escrowReady,
            Optional<MarketModuleControl> control
    ) {
        Objects.requireNonNull(module, "module");
        String requestedView = Objects.requireNonNull(view, "view");
        Optional<MarketModuleControl> persisted = Objects.requireNonNull(
                control, "control");
        if (!configuredEnabled) {
            return "claims".equals(requestedView)
                    ? PageAccess.allowed(
                    MarketModuleAvailability.CLAIMS_ONLY)
                    : PageAccess.denied(MarketModuleAvailability.HIDDEN,
                    "MODULE_DISABLED");
        }
        if ("claims".equals(requestedView)
                || MarketRoute.isDetailView(module, requestedView)) {
            MarketModuleAvailability availability = persisted.isEmpty()
                    || module != MarketModule.SHOP && !escrowReady
                    ? MarketModuleAvailability.RECOVERING
                    : availability(effectiveStatus(configuredEnabled,
                    persisted.orElseThrow().status()));
            if ("claims".equals(requestedView)
                    || availability.canOpenView(module, requestedView)) {
                return PageAccess.allowed(availability);
            }
            return PageAccess.denied(availability,
                    denialCode(availability));
        }
        if (persisted.isEmpty()) {
            return PageAccess.denied(
                    MarketModuleAvailability.RECOVERING,
                    "MODULE_CONTROL_UNAVAILABLE");
        }
        if (module != MarketModule.SHOP && !escrowReady) {
            return PageAccess.denied(
                    MarketModuleAvailability.RECOVERING,
                    "ESCROW_NOT_READY");
        }
        MarketModuleAvailability availability = availability(
                effectiveStatus(configuredEnabled,
                        persisted.orElseThrow().status()));
        if (!availability.canOpenView(module, requestedView)) {
            return PageAccess.denied(availability,
                    denialCode(availability));
        }
        return PageAccess.allowed(availability);
    }

    public static MarketPageSnapshot applyPageActions(
            MarketPageSnapshot page,
            MarketModuleAvailability availability
    ) {
        MarketPageSnapshot snapshot = Objects.requireNonNull(
                page, "page");
        MarketModuleAvailability mode = Objects.requireNonNull(
                availability, "availability");
        if (mode == MarketModuleAvailability.ENABLED) {
            return snapshot;
        }
        boolean cancellationRoute = mode
                .allowsOwnershipCancellationRoutes()
                && MarketModuleAvailability
                .ownershipCancellationView(snapshot.module(),
                        snapshot.view());
        boolean claimRoute = mode.allowsClaims()
                && "claims".equals(snapshot.view());
        List<MarketPageCard> cards = snapshot.cards().stream()
                .map(card -> actions(card, cancellationRoute,
                        claimRoute))
                .toList();
        return new MarketPageSnapshot(snapshot.requestId(),
                snapshot.routeNonce(), snapshot.module(),
                snapshot.view(), snapshot.pageIndex(),
                snapshot.pageSize(), snapshot.totalResults(),
                snapshot.pageCount(), snapshot.publicRevision(),
                snapshot.profileRevision(), snapshot.profileReplayEpoch(),
                snapshot.serverTimeMillis(),
                snapshot.unreadNotifications(),
                snapshot.aggregatePrimaryMinor(),
                snapshot.aggregateQuantity(), snapshot.categories(),
                snapshot.categoryCounts(), cards);
    }

    public static String preferredView(
            MarketModule module,
            MarketModuleAvailability availability
    ) {
        MarketModule market = Objects.requireNonNull(module, "module");
        return switch (Objects.requireNonNull(
                availability, "availability")) {
            case ENABLED, FROZEN, DRAINING -> market.rootView();
            case CANCEL_AND_REFUND -> market == MarketModule.BAZAAR
                    ? "orders" : market == MarketModule.AUCTION_HOUSE
                    ? "mine" : "claims";
            case CLAIMS_ONLY, DISABLED, HIDDEN, RECOVERING -> "claims";
        };
    }

    private static MarketPageCard actions(
            MarketPageCard card,
            boolean cancellationRoute,
            boolean claimRoute
    ) {
        boolean claim = claimRoute
                && card.kind() == MarketPageCardKind.CLAIM;
        boolean primary = claim && card.primaryAction()
                || cancellationRoute
                && card.kind() == MarketPageCardKind.BAZAAR_ORDER
                && card.primaryAction();
        boolean secondary = claim && card.secondaryAction()
                || cancellationRoute
                && card.kind() == MarketPageCardKind.AUCTION
                && card.secondaryAction();
        return new MarketPageCard(card.kind(), card.identity(),
                card.ownerId(), card.registryId(), card.itemCount(),
                card.title(), card.category(), card.state(),
                card.revision(), card.primaryMinor(),
                card.secondaryMinor(), card.quantity(),
                card.remainingMillis(), card.watched(), primary,
                secondary, card.tertiaryMinor(), card.insights());
    }

    private static MarketModuleStatus effectiveStatus(
            boolean configuredEnabled,
            MarketModuleStatus status
    ) {
        if (!configuredEnabled) {
            throw new IllegalArgumentException(
                    "Disabled module status must not be projected");
        }
        return Objects.requireNonNull(status, "status");
    }

    private static MarketModuleAvailability availability(
            MarketModuleStatus status
    ) {
        return switch (status) {
            case ENABLED -> MarketModuleAvailability.ENABLED;
            case FROZEN -> MarketModuleAvailability.FROZEN;
            case DRAINING -> MarketModuleAvailability.DRAINING;
            case CANCEL_AND_REFUND ->
                    MarketModuleAvailability.CANCEL_AND_REFUND;
        };
    }

    private static String denialCode(
            MarketModuleAvailability availability
    ) {
        return switch (availability) {
            case FROZEN -> "MODULE_FROZEN";
            case DRAINING -> "MODULE_DRAINING";
            case CANCEL_AND_REFUND -> "MODULE_CANCEL_AND_REFUND";
            case CLAIMS_ONLY -> "CLAIMS_ONLY";
            case DISABLED, HIDDEN -> "MODULE_DISABLED";
            case RECOVERING -> "ESCROW_NOT_READY";
            case ENABLED -> "VIEW_DISABLED";
        };
    }

    public record PageAccess(
            boolean allowed,
            MarketModuleAvailability availability,
            String denialCode
    ) {
        public PageAccess {
            availability = Objects.requireNonNull(
                    availability, "availability");
            denialCode = Objects.requireNonNull(
                    denialCode, "denialCode");
            if (allowed != denialCode.isEmpty()) {
                throw new IllegalArgumentException(
                        "Market page access result is inconsistent");
            }
        }

        private static PageAccess allowed(
                MarketModuleAvailability availability
        ) {
            return new PageAccess(true, availability, "");
        }

        private static PageAccess denied(
                MarketModuleAvailability availability,
                String denialCode
        ) {
            return new PageAccess(false, availability, denialCode);
        }
    }
}
