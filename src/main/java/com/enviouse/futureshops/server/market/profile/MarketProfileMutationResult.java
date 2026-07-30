package com.enviouse.futureshops.server.market.profile;

import com.enviouse.futureshops.client.market.MarketModule;

import java.util.Objects;
import java.util.UUID;

public record MarketProfileMutationResult(
        UUID requestId,
        UUID routeNonce,
        MarketModule module,
        MarketProfileMutationType type,
        MarketProfileMutationResultCode resultCode,
        long profileRevision,
        long replayEpoch,
        int watchedAuctionCount,
        int favoriteProductCount,
        int priceAlertCount,
        int notificationCount,
        int unreadNotificationCount,
        int affectedCount,
        boolean changed,
        boolean replayed
) {
    private static final UUID ZERO = new UUID(0L, 0L);

    public MarketProfileMutationResult {
        requestId = requireId(requestId);
        routeNonce = requireId(routeNonce);
        module = Objects.requireNonNull(module, "module");
        type = Objects.requireNonNull(type, "type");
        resultCode = Objects.requireNonNull(resultCode, "resultCode");
        if (module == MarketModule.SHOP || profileRevision < 0L
                || replayEpoch < 0L
                || type == MarketProfileMutationType.AUCTION_WATCH
                && module != MarketModule.AUCTION_HOUSE
                || type != MarketProfileMutationType.AUCTION_WATCH
                && type != MarketProfileMutationType.NOTIFICATIONS_READ
                && module != MarketModule.BAZAAR
                || watchedAuctionCount < 0
                || watchedAuctionCount
                > MarketProfileSavedData.MAX_WATCHED_AUCTIONS
                || favoriteProductCount < 0
                || favoriteProductCount
                > MarketProfileSavedData.MAX_FAVORITE_PRODUCTS
                || priceAlertCount < 0
                || priceAlertCount
                > MarketProfileSavedData.MAX_PRICE_ALERTS
                || notificationCount < 0
                || notificationCount
                > MarketProfileSavedData.MAX_NOTIFICATIONS
                || unreadNotificationCount < 0
                || unreadNotificationCount > notificationCount
                || affectedCount < 0
                || affectedCount
                > MarketProfileSavedData.MAX_WATCHED_AUCTIONS
                || type != MarketProfileMutationType.NOTIFICATIONS_READ
                && affectedCount > 1
                || resultCode == MarketProfileMutationResultCode.SUCCESS
                && affectedCount == 0
                || resultCode != MarketProfileMutationResultCode.SUCCESS
                && affectedCount != 0
                || changed != (resultCode
                == MarketProfileMutationResultCode.SUCCESS)) {
            throw new IllegalArgumentException(
                    "Market profile mutation result is invalid");
        }
    }

    public MarketProfileMutationResult(
            UUID requestId, UUID routeNonce, MarketModule module,
            MarketProfileMutationType type,
            MarketProfileMutationResultCode resultCode,
            long profileRevision, int watchedAuctionCount,
            int favoriteProductCount, int priceAlertCount,
            int notificationCount, int unreadNotificationCount,
            int affectedCount, boolean changed, boolean replayed) {
        this(requestId, routeNonce, module, type, resultCode,
                profileRevision, 0L, watchedAuctionCount,
                favoriteProductCount, priceAlertCount,
                notificationCount, unreadNotificationCount,
                affectedCount, changed, replayed);
    }

    public static MarketProfileMutationResult from(
            MarketProfileMutationCommand command,
            MarketProfileMutationResultCode resultCode,
            MarketProfileSavedData.Snapshot snapshot,
            int affectedCount,
            boolean replayed
    ) {
        Objects.requireNonNull(command, "command");
        MarketProfileSavedData.Snapshot state = Objects.requireNonNull(
                snapshot, "snapshot");
        return new MarketProfileMutationResult(command.requestId(),
                command.routeNonce(), command.module(),
                command.mutation().type(), resultCode,
                state.revision(), state.replayEpoch(),
                state.watchedAuctions().size(),
                state.favoriteProducts().size(),
                state.priceAlerts().size(),
                state.notifications().size(),
                state.unreadNotifications(), affectedCount,
                resultCode == MarketProfileMutationResultCode.SUCCESS,
                replayed);
    }

    public MarketProfileMutationResult asReplay() {
        return replayed ? this : new MarketProfileMutationResult(
                requestId, routeNonce, module, type, resultCode,
                profileRevision, replayEpoch, watchedAuctionCount,
                favoriteProductCount, priceAlertCount,
                notificationCount, unreadNotificationCount,
                affectedCount, changed, true);
    }

    private static UUID requireId(UUID value) {
        UUID result = Objects.requireNonNull(value, "identity");
        if (ZERO.equals(result)) {
            throw new IllegalArgumentException(
                    "Market profile mutation result identity is invalid");
        }
        return result;
    }
}
