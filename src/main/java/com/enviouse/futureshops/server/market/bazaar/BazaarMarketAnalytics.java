package com.enviouse.futureshops.server.market.bazaar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

public final class BazaarMarketAnalytics {
    public static final int MAX_DEPTH_LEVELS = 100;
    public static final int MAX_CHART_POINTS = 4096;

    private BazaarMarketAnalytics() {
    }

    public static ProductSummary summarize(
            BazaarOrderBookSnapshot snapshot,
            BazaarProductVersionKey product,
            long nowMillis,
            long recentWindowMillis,
            int depthLevels
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(product, "product");
        if (nowMillis < 0L || recentWindowMillis <= 0L
                || depthLevels <= 0 || depthLevels > MAX_DEPTH_LEVELS) {
            throw new IllegalArgumentException(
                    "Bazaar analytics request is invalid");
        }
        List<BazaarOrder> orders = snapshot.orders().stream()
                .filter(BazaarOrder::matchable)
                .filter(order -> order.productId().equals(
                        product.productId())
                        && order.productVersion() == product.version())
                .toList();
        List<DepthLevel> bids = depth(orders, BazaarOrderSide.BUY,
                depthLevels);
        List<DepthLevel> asks = depth(orders, BazaarOrderSide.SELL,
                depthLevels);
        List<BazaarFill> fills = snapshot.fills().stream()
                .filter(fill -> fill.productId().equals(
                        product.productId())
                        && fill.productVersion() == product.version())
                .filter(fill -> fill.filledAtMillis() <= nowMillis)
                .sorted(Comparator.comparingLong(
                                BazaarFill::filledAtMillis)
                        .thenComparingLong(BazaarFill::sequence))
                .toList();
        long threshold = Math.max(0L,
                nowMillis - recentWindowMillis);
        List<BazaarFill> recent = fills.stream()
                .filter(fill -> fill.filledAtMillis() >= threshold)
                .toList();
        long volume = 0L;
        long notional = 0L;
        for (BazaarFill fill : recent) {
            volume = Math.addExact(volume, fill.quantity());
            notional = Math.addExact(notional, fill.grossMinor());
        }
        OptionalLong lastPrice = fills.isEmpty()
                ? OptionalLong.empty()
                : OptionalLong.of(fills.get(fills.size() - 1)
                .priceMinor());
        long trendBasisPoints = recent.size() < 2 ? 0L
                : trendBasisPoints(recent.get(0).priceMinor(),
                recent.get(recent.size() - 1).priceMinor());
        OptionalLong bestBid = bids.isEmpty() ? OptionalLong.empty()
                : OptionalLong.of(bids.get(0).priceMinor());
        OptionalLong bestAsk = asks.isEmpty() ? OptionalLong.empty()
                : OptionalLong.of(asks.get(0).priceMinor());
        OptionalLong spread = bestBid.isPresent() && bestAsk.isPresent()
                ? OptionalLong.of(Math.subtractExact(
                bestAsk.getAsLong(), bestBid.getAsLong()))
                : OptionalLong.empty();
        return new ProductSummary(product, bestBid, bestAsk, spread,
                lastPrice, volume, notional, trendBasisPoints,
                bids, asks);
    }

    public static List<OhlcvBucket> ohlcv(
            BazaarOrderBookSnapshot snapshot,
            BazaarProductVersionKey product,
            long fromMillis,
            long toMillis,
            long bucketMillis,
            int maximumPoints
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(product, "product");
        if (fromMillis < 0L || toMillis < fromMillis
                || bucketMillis <= 0L || maximumPoints <= 0
                || maximumPoints > MAX_CHART_POINTS) {
            throw new IllegalArgumentException(
                    "Bazaar chart request is invalid");
        }
        long requestedBuckets = Math.addExact(
                Math.floorDiv(toMillis - fromMillis, bucketMillis), 1L);
        if (requestedBuckets > maximumPoints) {
            throw new IllegalArgumentException(
                    "Bazaar chart point limit is exceeded");
        }
        Map<Long, MutableBucket> buckets = new LinkedHashMap<>();
        snapshot.fills().stream()
                .filter(fill -> fill.productId().equals(
                        product.productId())
                        && fill.productVersion() == product.version())
                .filter(fill -> fill.filledAtMillis() >= fromMillis
                        && fill.filledAtMillis() <= toMillis)
                .sorted(Comparator.comparingLong(
                                BazaarFill::filledAtMillis)
                        .thenComparingLong(BazaarFill::sequence))
                .forEach(fill -> {
                    long offset = fill.filledAtMillis() - fromMillis;
                    long start = Math.addExact(fromMillis,
                            Math.multiplyExact(
                                    Math.floorDiv(offset, bucketMillis),
                                    bucketMillis));
                    buckets.computeIfAbsent(start,
                                    ignored -> new MutableBucket(start,
                                            Math.addExact(start,
                                                    bucketMillis)))
                            .accept(fill);
                });
        return buckets.values().stream().map(MutableBucket::freeze)
                .toList();
    }

    public static List<VolumeLeader> volumeLeaders(
            BazaarOrderBookSnapshot snapshot,
            long fromMillis,
            long toMillis,
            int limit
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (fromMillis < 0L || toMillis < fromMillis
                || limit <= 0 || limit > 1000) {
            throw new IllegalArgumentException(
                    "Bazaar volume leader request is invalid");
        }
        Map<BazaarProductVersionKey, MutableVolume> totals =
                new LinkedHashMap<>();
        for (BazaarFill fill : snapshot.fills()) {
            if (fill.filledAtMillis() < fromMillis
                    || fill.filledAtMillis() > toMillis) {
                continue;
            }
            BazaarProductVersionKey key = new BazaarProductVersionKey(
                    fill.productId(), fill.productVersion());
            totals.computeIfAbsent(key, ignored -> new MutableVolume())
                    .accept(fill);
        }
        return totals.entrySet().stream().map(entry ->
                        new VolumeLeader(entry.getKey(),
                                entry.getValue().quantity,
                                entry.getValue().notional,
                                entry.getValue().trades))
                .sorted(Comparator.comparingLong(
                                VolumeLeader::notionalMinor).reversed()
                        .thenComparing(Comparator.comparingLong(
                                VolumeLeader::quantity).reversed())
                        .thenComparing(value ->
                                value.product().productId())
                        .thenComparingLong(value ->
                                value.product().version()))
                .limit(limit).toList();
    }

    public static long estimatedPortfolioValue(
            BazaarOrderBookSnapshot snapshot,
            Map<BazaarProductVersionKey, Integer> quantities
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(quantities, "quantities");
        long total = 0L;
        for (Map.Entry<BazaarProductVersionKey, Integer> entry
                : quantities.entrySet()) {
            BazaarProductVersionKey product = Objects.requireNonNull(
                    entry.getKey(), "product");
            int quantity = Objects.requireNonNull(
                    entry.getValue(), "quantity");
            if (quantity < 0) {
                throw new IllegalArgumentException(
                        "Bazaar portfolio quantity is invalid");
            }
            long price = referencePrice(snapshot, product);
            total = Math.addExact(total,
                    Math.multiplyExact(price, quantity));
        }
        return total;
    }

    private static long referencePrice(
            BazaarOrderBookSnapshot snapshot,
            BazaarProductVersionKey product
    ) {
        BazaarFill latest = snapshot.fills().stream()
                .filter(fill -> fill.productId().equals(
                        product.productId())
                        && fill.productVersion() == product.version())
                .max(Comparator.comparingLong(
                                BazaarFill::filledAtMillis)
                        .thenComparingLong(BazaarFill::sequence))
                .orElse(null);
        if (latest != null) {
            return latest.priceMinor();
        }
        Long configured = snapshot.referencePrices().get(
                product.productId());
        return configured == null ? 0L : configured;
    }

    private static List<DepthLevel> depth(
            List<BazaarOrder> orders,
            BazaarOrderSide side,
            int limit
    ) {
        Map<Long, MutableDepth> levels = new LinkedHashMap<>();
        orders.stream().filter(order -> order.side() == side)
                .sorted(side == BazaarOrderSide.BUY
                        ? Comparator.comparingLong(
                        BazaarOrder::limitPriceMinor).reversed()
                        .thenComparingLong(
                                BazaarOrder::acceptedSequence)
                        : Comparator.comparingLong(
                        BazaarOrder::limitPriceMinor)
                        .thenComparingLong(
                                BazaarOrder::acceptedSequence))
                .forEach(order -> levels.computeIfAbsent(
                                order.limitPriceMinor(),
                                ignored -> new MutableDepth())
                        .accept(order));
        return levels.entrySet().stream().limit(limit)
                .map(entry -> new DepthLevel(entry.getKey(),
                        entry.getValue().quantity,
                        entry.getValue().orders))
                .toList();
    }

    private static long trendBasisPoints(long first, long last) {
        long difference = Math.subtractExact(last, first);
        return Math.floorDiv(Math.multiplyExact(difference, 10000L),
                first);
    }

    public record ProductSummary(
            BazaarProductVersionKey product,
            OptionalLong bestBidMinor,
            OptionalLong bestAskMinor,
            OptionalLong spreadMinor,
            OptionalLong lastPriceMinor,
            long recentVolume,
            long recentNotionalMinor,
            long trendBasisPoints,
            List<DepthLevel> bidDepth,
            List<DepthLevel> askDepth
    ) {
        public ProductSummary {
            product = Objects.requireNonNull(product, "product");
            bestBidMinor = Objects.requireNonNull(
                    bestBidMinor, "bestBidMinor");
            bestAskMinor = Objects.requireNonNull(
                    bestAskMinor, "bestAskMinor");
            spreadMinor = Objects.requireNonNull(
                    spreadMinor, "spreadMinor");
            lastPriceMinor = Objects.requireNonNull(
                    lastPriceMinor, "lastPriceMinor");
            bidDepth = List.copyOf(bidDepth);
            askDepth = List.copyOf(askDepth);
            if (recentVolume < 0L || recentNotionalMinor < 0L) {
                throw new IllegalArgumentException(
                        "Bazaar product summary is invalid");
            }
        }
    }

    public record DepthLevel(
            long priceMinor,
            long quantity,
            int orderCount
    ) {
        public DepthLevel {
            if (priceMinor <= 0L || quantity <= 0L || orderCount <= 0) {
                throw new IllegalArgumentException(
                        "Bazaar depth level is invalid");
            }
        }
    }

    public record OhlcvBucket(
            long startMillis,
            long endMillis,
            long openMinor,
            long highMinor,
            long lowMinor,
            long closeMinor,
            long quantity,
            long notionalMinor,
            int trades
    ) {
        public OhlcvBucket {
            if (startMillis < 0L || endMillis <= startMillis
                    || openMinor <= 0L || highMinor <= 0L
                    || lowMinor <= 0L || closeMinor <= 0L
                    || highMinor < lowMinor || openMinor < lowMinor
                    || openMinor > highMinor || closeMinor < lowMinor
                    || closeMinor > highMinor || quantity <= 0L
                    || notionalMinor <= 0L || trades <= 0) {
                throw new IllegalArgumentException(
                        "Bazaar OHLCV bucket is invalid");
            }
        }
    }

    public record VolumeLeader(
            BazaarProductVersionKey product,
            long quantity,
            long notionalMinor,
            int trades
    ) {
        public VolumeLeader {
            product = Objects.requireNonNull(product, "product");
            if (quantity <= 0L || notionalMinor <= 0L || trades <= 0) {
                throw new IllegalArgumentException(
                        "Bazaar volume leader is invalid");
            }
        }
    }

    private static final class MutableDepth {
        private long quantity;
        private int orders;

        private void accept(BazaarOrder order) {
            quantity = Math.addExact(quantity,
                    order.remainingQuantity());
            orders = Math.addExact(orders, 1);
        }
    }

    private static final class MutableVolume {
        private long quantity;
        private long notional;
        private int trades;

        private void accept(BazaarFill fill) {
            quantity = Math.addExact(quantity, fill.quantity());
            notional = Math.addExact(notional, fill.grossMinor());
            trades = Math.addExact(trades, 1);
        }
    }

    private static final class MutableBucket {
        private final long start;
        private final long end;
        private long open;
        private long high;
        private long low;
        private long close;
        private long quantity;
        private long notional;
        private int trades;

        private MutableBucket(long start, long end) {
            this.start = start;
            this.end = end;
        }

        private void accept(BazaarFill fill) {
            long price = fill.priceMinor();
            if (trades == 0) {
                open = price;
                high = price;
                low = price;
            } else {
                high = Math.max(high, price);
                low = Math.min(low, price);
            }
            close = price;
            quantity = Math.addExact(quantity, fill.quantity());
            notional = Math.addExact(notional, fill.grossMinor());
            trades = Math.addExact(trades, 1);
        }

        private OhlcvBucket freeze() {
            return new OhlcvBucket(start, end, open, high, low, close,
                    quantity, notional, trades);
        }
    }
}
