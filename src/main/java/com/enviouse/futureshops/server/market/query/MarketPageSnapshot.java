package com.enviouse.futureshops.server.market.query;

import com.enviouse.futureshops.client.market.MarketModule;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MarketPageSnapshot(
        UUID requestId,
        UUID routeNonce,
        MarketModule module,
        String view,
        int pageIndex,
        int pageSize,
        int totalResults,
        int pageCount,
        long publicRevision,
        long profileRevision,
        long profileReplayEpoch,
        long serverTimeMillis,
        int unreadNotifications,
        long aggregatePrimaryMinor,
        long aggregateQuantity,
        List<String> categories,
        List<MarketPageCard> cards
) {
    public MarketPageSnapshot {
        requestId = Objects.requireNonNull(requestId, "requestId");
        routeNonce = Objects.requireNonNull(routeNonce, "routeNonce");
        module = Objects.requireNonNull(module, "module");
        view = Objects.requireNonNull(view, "view");
        categories = List.copyOf(Objects.requireNonNull(
                categories, "categories"));
        cards = List.copyOf(Objects.requireNonNull(cards, "cards"));
        if (requestId.equals(new UUID(0L, 0L))
                || routeNonce.equals(new UUID(0L, 0L))
                || module == MarketModule.SHOP
                || view.isEmpty() || view.length() > 32
                || pageIndex < 0 || pageSize <= 0
                || pageSize > MarketPageQuery.MAXIMUM_PAGE_SIZE
                || totalResults < 0 || pageCount < 0
                || publicRevision < 0L || profileRevision < 0L
                || profileReplayEpoch < 0L || serverTimeMillis < 0L
                || unreadNotifications < 0
                || aggregatePrimaryMinor < 0L
                || aggregateQuantity < 0L
                || cards.size() > pageSize
                || categories.size() > 256
                || categories.stream().anyMatch(value -> value == null
                || value.length() > 128
                || !value.equals(value.strip()))) {
            throw new IllegalArgumentException(
                    "Market page snapshot is invalid");
        }
    }

    public MarketPageSnapshot(
            UUID requestId, UUID routeNonce, MarketModule module,
            String view, int pageIndex, int pageSize, int totalResults,
            int pageCount, long publicRevision, long serverTimeMillis,
            int unreadNotifications, long aggregatePrimaryMinor,
            long aggregateQuantity, List<String> categories,
            List<MarketPageCard> cards) {
        this(requestId, routeNonce, module, view, pageIndex, pageSize,
                totalResults, pageCount, publicRevision, 0L, 0L,
                serverTimeMillis, unreadNotifications,
                aggregatePrimaryMinor, aggregateQuantity,
                categories, cards);
    }
}
