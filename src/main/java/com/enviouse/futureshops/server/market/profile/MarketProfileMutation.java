package com.enviouse.futureshops.server.market.profile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public sealed interface MarketProfileMutation permits
        MarketProfileMutation.AuctionWatch,
        MarketProfileMutation.BazaarFavorite,
        MarketProfileMutation.PriceAlertAdd,
        MarketProfileMutation.PriceAlertRemove,
        MarketProfileMutation.NotificationsRead {
    UUID ZERO = new UUID(0L, 0L);

    MarketProfileMutationType type();

    record AuctionWatch(
            UUID listingId,
            boolean watched
    ) implements MarketProfileMutation {
        public AuctionWatch {
            listingId = requireId(listingId, "listingId");
        }

        @Override
        public MarketProfileMutationType type() {
            return MarketProfileMutationType.AUCTION_WATCH;
        }
    }

    record BazaarFavorite(
            MarketProfileSavedData.ProductKey product,
            boolean favorite
    ) implements MarketProfileMutation {
        public BazaarFavorite {
            product = Objects.requireNonNull(product, "product");
        }

        @Override
        public MarketProfileMutationType type() {
            return MarketProfileMutationType.BAZAAR_FAVORITE;
        }
    }

    record PriceAlertAdd(
            UUID alertId,
            MarketProfileSavedData.ProductKey product,
            MarketProfileSavedData.AlertDirection direction,
            long thresholdMinor
    ) implements MarketProfileMutation {
        public PriceAlertAdd {
            alertId = requireId(alertId, "alertId");
            product = Objects.requireNonNull(product, "product");
            direction = Objects.requireNonNull(direction, "direction");
            if (thresholdMinor <= 0L) {
                throw new IllegalArgumentException(
                        "Market profile alert threshold is invalid");
            }
        }

        @Override
        public MarketProfileMutationType type() {
            return MarketProfileMutationType.PRICE_ALERT_ADD;
        }
    }

    record PriceAlertRemove(
            UUID alertId
    ) implements MarketProfileMutation {
        public PriceAlertRemove {
            alertId = requireId(alertId, "alertId");
        }

        @Override
        public MarketProfileMutationType type() {
            return MarketProfileMutationType.PRICE_ALERT_REMOVE;
        }
    }

    record NotificationsRead(
            List<UUID> notificationIds
    ) implements MarketProfileMutation {
        public NotificationsRead {
            notificationIds = List.copyOf(Objects.requireNonNull(
                    notificationIds, "notificationIds"));
            if (notificationIds.isEmpty()
                    || notificationIds.size()
                    > MarketProfileSavedData.MAX_NOTIFICATIONS) {
                throw new IllegalArgumentException(
                        "Market profile notification request is invalid");
            }
            LinkedHashSet<UUID> unique = new LinkedHashSet<>();
            for (UUID notificationId : notificationIds) {
                if (!unique.add(requireId(notificationId,
                        "notificationId"))) {
                    throw new IllegalArgumentException(
                            "Market profile notification request is duplicated");
                }
            }
        }

        @Override
        public MarketProfileMutationType type() {
            return MarketProfileMutationType.NOTIFICATIONS_READ;
        }
    }

    private static UUID requireId(UUID value, String label) {
        UUID result = Objects.requireNonNull(value, label);
        if (ZERO.equals(result)) {
            throw new IllegalArgumentException(
                    "Market profile mutation identity is invalid");
        }
        return result;
    }
}
