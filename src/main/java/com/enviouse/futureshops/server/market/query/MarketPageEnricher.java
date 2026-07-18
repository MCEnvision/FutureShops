package com.enviouse.futureshops.server.market.query;

import com.enviouse.futureshops.server.market.auction.AuctionHouseSnapshot;
import com.enviouse.futureshops.server.market.auction.AuctionListing;
import com.enviouse.futureshops.server.market.auction.AuctionListingState;
import com.enviouse.futureshops.server.market.bazaar.BazaarFill;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrder;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderBookSnapshot;
import com.enviouse.futureshops.server.market.bazaar.BazaarOrderSide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class MarketPageEnricher {
    private static final long HOUR_MILLIS = 3_600_000L;
    private static final long DAY_MILLIS = 86_400_000L;

    private MarketPageEnricher() {
    }

    public static MarketPageSnapshot auction(
            MarketPageSnapshot page,
            AuctionHouseSnapshot snapshot,
            Function<UUID, String> names,
            Map<UUID, String> itemSnapshots
    ) {
        Map<UUID, AuctionListing> listings = snapshot.listings();
        Map<String, AuctionActivity> activity = auctionActivity(snapshot,
                page.serverTimeMillis());
        List<MarketPageCard> cards = new ArrayList<>(page.cards().size());
        for (MarketPageCard card : page.cards()) {
            AuctionListing listing = parseId(card.identity())
                    .map(listings::get).orElse(null);
            if (listing == null
                    || card.kind() != MarketPageCardKind.AUCTION) {
                cards.add(card);
                continue;
            }
            AuctionActivity stats = activity.getOrDefault(
                    listing.itemLot().registryId(), AuctionActivity.EMPTY);
            Optional<UUID> participant = listing.sale()
                    .map(value -> value.buyerId())
                    .or(() -> listing.highestBid().map(
                            value -> value.bidderId()));
            String role = listing.sale().isPresent()
                    ? "BUYER" : listing.highestBid().isPresent()
                    ? "TOP_BIDDER" : "";
            String stack = itemSnapshots.getOrDefault(
                    listing.listingId(), "");
            cards.add(card.withInsights(new MarketCardInsights(
                    safeName(names, listing.sellerId()), participant,
                    participant.map(value -> safeName(names, value))
                            .orElse(""), role, stack,
                    stack.contains("tag:"), stats.activeListings(), 0L,
                    stats.salesLastHour(), stats.salesLastDay(),
                    stats.unitsLastDay(), stats.averagePriceMinor(),
                    stats.priceHistoryMinor())));
        }
        return copy(page, cards);
    }

    public static MarketPageSnapshot bazaar(
            MarketPageSnapshot page,
            BazaarOrderBookSnapshot snapshot,
            Function<UUID, String> names
    ) {
        Map<UUID, BazaarOrder> orders = new HashMap<>();
        for (BazaarOrder order : snapshot.orders()) {
            orders.put(order.orderId(), order);
        }
        Map<UUID, BazaarFill> fills = new HashMap<>();
        for (BazaarFill fill : snapshot.fills()) {
            fills.put(fill.fillId(), fill);
        }
        Map<ProductKey, BazaarActivity> activity = bazaarActivity(snapshot,
                page.serverTimeMillis());
        List<MarketPageCard> cards = new ArrayList<>(page.cards().size());
        for (MarketPageCard card : page.cards()) {
            ProductKey product = productKey(card, orders, fills);
            BazaarActivity stats = activity.getOrDefault(product,
                    BazaarActivity.EMPTY);
            String ownerName = card.kind() == MarketPageCardKind.BAZAAR_PRODUCT
                    ? "" : card.ownerId().map(value -> safeName(names, value))
                    .orElse("");
            Optional<UUID> participant = Optional.empty();
            String participantName = "";
            String participantRole = "";
            BazaarFill fill = parseId(card.identity()).map(fills::get)
                    .orElse(null);
            if (fill != null && card.ownerId().isPresent()) {
                BazaarOrder buy = orders.get(fill.buyOrderId());
                BazaarOrder sell = orders.get(fill.sellOrderId());
                UUID viewer = card.ownerId().orElseThrow();
                BazaarOrder opposite = buy != null
                        && buy.ownerId().equals(viewer) ? sell : buy;
                if (opposite != null) {
                    participant = Optional.of(opposite.ownerId());
                    participantName = safeName(names, opposite.ownerId());
                    participantRole = opposite.side() == BazaarOrderSide.SELL
                            ? "SELLER" : "BUYER";
                }
            }
            cards.add(card.withInsights(new MarketCardInsights(ownerName,
                    participant, participantName, participantRole, "",
                    false, stats.sellOrders(), stats.buyOrders(),
                    stats.tradesLastHour(), stats.tradesLastDay(),
                    stats.unitsLastDay(), stats.averagePriceMinor(),
                    stats.priceHistoryMinor())));
        }
        return copy(page, cards);
    }

    private static Map<String, AuctionActivity> auctionActivity(
            AuctionHouseSnapshot snapshot,
            long now
    ) {
        Map<String, MutableActivity> values = new HashMap<>();
        for (AuctionListing listing : snapshot.listings().values()) {
            MutableActivity stats = values.computeIfAbsent(
                    listing.itemLot().registryId(), ignored ->
                            new MutableActivity());
            if (listing.state() == AuctionListingState.ACTIVE) {
                stats.activeListings++;
            }
            listing.sale().ifPresent(sale -> {
                if (inside(now, sale.soldAtMillis(), DAY_MILLIS)) {
                    stats.dayTrades++;
                    stats.dayUnits = saturatedAdd(stats.dayUnits,
                            listing.itemLot().count());
                    stats.totalPrice = saturatedAdd(stats.totalPrice,
                            sale.priceMinor());
                    stats.prices.add(new PricePoint(sale.soldAtMillis(),
                            sale.priceMinor()));
                    if (inside(now, sale.soldAtMillis(), HOUR_MILLIS)) {
                        stats.hourTrades++;
                    }
                }
            });
        }
        Map<String, AuctionActivity> result = new HashMap<>();
        values.forEach((key, stats) -> result.put(key,
                new AuctionActivity(stats.activeListings,
                        stats.hourTrades, stats.dayTrades, stats.dayUnits,
                        average(stats.totalPrice, stats.dayTrades),
                        history(stats.prices))));
        return result;
    }

    private static Map<ProductKey, BazaarActivity> bazaarActivity(
            BazaarOrderBookSnapshot snapshot,
            long now
    ) {
        Map<ProductKey, MutableActivity> values = new HashMap<>();
        for (BazaarOrder order : snapshot.orders()) {
            if (!order.matchable()) {
                continue;
            }
            MutableActivity stats = values.computeIfAbsent(new ProductKey(
                    order.productId(), order.productVersion()), ignored ->
                    new MutableActivity());
            if (order.side() == BazaarOrderSide.SELL) {
                stats.activeListings++;
            } else {
                stats.buyOrders++;
            }
        }
        for (BazaarFill fill : snapshot.fills()) {
            if (!inside(now, fill.filledAtMillis(), DAY_MILLIS)) {
                continue;
            }
            MutableActivity stats = values.computeIfAbsent(new ProductKey(
                    fill.productId(), fill.productVersion()), ignored ->
                    new MutableActivity());
            stats.dayTrades++;
            stats.dayUnits = saturatedAdd(stats.dayUnits, fill.quantity());
            stats.totalPrice = saturatedAdd(stats.totalPrice,
                    fill.grossMinor());
            stats.prices.add(new PricePoint(fill.filledAtMillis(),
                    fill.priceMinor()));
            if (inside(now, fill.filledAtMillis(), HOUR_MILLIS)) {
                stats.hourTrades++;
            }
        }
        Map<ProductKey, BazaarActivity> result = new HashMap<>();
        values.forEach((key, stats) -> result.put(key,
                new BazaarActivity(stats.activeListings, stats.buyOrders,
                        stats.hourTrades, stats.dayTrades, stats.dayUnits,
                        average(stats.totalPrice, stats.dayUnits),
                        history(stats.prices))));
        return result;
    }

    private static ProductKey productKey(
            MarketPageCard card,
            Map<UUID, BazaarOrder> orders,
            Map<UUID, BazaarFill> fills
    ) {
        UUID identity = parseId(card.identity()).orElse(null);
        BazaarOrder order = identity == null ? null : orders.get(identity);
        if (order != null) {
            return new ProductKey(order.productId(), order.productVersion());
        }
        BazaarFill fill = identity == null ? null : fills.get(identity);
        if (fill != null) {
            return new ProductKey(fill.productId(), fill.productVersion());
        }
        int separator = card.identity().lastIndexOf('@');
        if (separator > 0) {
            try {
                return new ProductKey(card.identity().substring(0,
                        separator), Long.parseLong(card.identity().substring(
                        separator + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return new ProductKey(card.title(), Math.max(1L, card.revision()));
    }

    private static List<Long> history(List<PricePoint> points) {
        return points.stream().sorted(Comparator.comparingLong(
                        PricePoint::timeMillis))
                .skip(Math.max(0, points.size()
                        - MarketCardInsights.MAXIMUM_PRICE_POINTS))
                .map(PricePoint::priceMinor).toList();
    }

    private static MarketPageSnapshot copy(
            MarketPageSnapshot page,
            List<MarketPageCard> cards
    ) {
        return new MarketPageSnapshot(page.requestId(), page.routeNonce(),
                page.module(), page.view(), page.pageIndex(), page.pageSize(),
                page.totalResults(), page.pageCount(), page.publicRevision(),
                page.profileRevision(), page.profileReplayEpoch(),
                page.serverTimeMillis(), page.unreadNotifications(),
                page.aggregatePrimaryMinor(), page.aggregateQuantity(),
                page.categories(), cards);
    }

    private static Optional<UUID> parseId(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String safeName(Function<UUID, String> names, UUID id) {
        String value = names.apply(id);
        if (value == null || value.isBlank()) {
            return "Unknown Player";
        }
        return value.length() <= 64 ? value : value.substring(0, 64);
    }

    private static boolean inside(long now, long then, long window) {
        return then <= now && then >= Math.max(0L, now - window);
    }

    private static long average(long total, long count) {
        return count == 0L ? 0L : total / count;
    }

    private static long saturatedAdd(long first, long second) {
        if (second > 0L && first > Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    private record ProductKey(String id, long version) {
    }

    private record PricePoint(long timeMillis, long priceMinor) {
    }

    private static final class MutableActivity {
        private long activeListings;
        private long buyOrders;
        private long hourTrades;
        private long dayTrades;
        private long dayUnits;
        private long totalPrice;
        private final List<PricePoint> prices = new ArrayList<>();
    }

    private record AuctionActivity(long activeListings,
                                   long salesLastHour,
                                   long salesLastDay,
                                   long unitsLastDay,
                                   long averagePriceMinor,
                                   List<Long> priceHistoryMinor) {
        private static final AuctionActivity EMPTY = new AuctionActivity(
                0L, 0L, 0L, 0L, 0L, List.of());
    }

    private record BazaarActivity(long sellOrders,
                                  long buyOrders,
                                  long tradesLastHour,
                                  long tradesLastDay,
                                  long unitsLastDay,
                                  long averagePriceMinor,
                                  List<Long> priceHistoryMinor) {
        private static final BazaarActivity EMPTY = new BazaarActivity(
                0L, 0L, 0L, 0L, 0L, 0L, List.of());
    }
}
