package com.enviouse.futureshops.server.market.bazaar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BazaarProductBrowseIndex {
    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_PAGE_INDEX = 1000000;
    public static final int MAX_QUERY_LENGTH = 128;

    private BazaarProductBrowseIndex() {
    }

    public static Page query(
            BazaarOrderBookSnapshot snapshot,
            Query query,
            Set<BazaarProductVersionKey> watchedProducts
    ) {
        return query(snapshot, query, watchedProducts, false);
    }

    public static Page queryWatched(
            BazaarOrderBookSnapshot snapshot,
            Query query,
            Set<BazaarProductVersionKey> watchedProducts
    ) {
        return query(snapshot, query, watchedProducts, true);
    }

    private static Page query(
            BazaarOrderBookSnapshot snapshot,
            Query query,
            Set<BazaarProductVersionKey> watchedProducts,
            boolean watchedOnly
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(query, "query");
        watchedProducts = Set.copyOf(Objects.requireNonNull(
                watchedProducts, "watchedProducts"));
        Map<String, BazaarProduct> latest = new LinkedHashMap<>();
        for (BazaarProduct product : snapshot.products()) {
            BazaarProduct previous = latest.get(product.productId());
            if (previous == null || product.version() > previous.version()) {
                latest.put(product.productId(), product);
            }
        }
        List<Card> matches = new ArrayList<>();
        for (BazaarProduct product : latest.values()) {
            if (!visible(product, query)) {
                continue;
            }
            BazaarProductVersionKey key =
                    BazaarProductVersionKey.of(product);
            if (watchedOnly && !watchedProducts.contains(key)) {
                continue;
            }
            BazaarMarketAnalytics.ProductSummary summary =
                    BazaarMarketAnalytics.summarize(snapshot, key,
                            query.nowMillis(),
                            query.recentWindowMillis(),
                            query.depthLevels());
            matches.add(Card.from(product, summary,
                    watchedProducts.contains(key)));
        }
        matches.sort(comparator(query.sort()));
        int total = matches.size();
        long firstLong = Math.multiplyExact(
                (long) query.pageIndex(), query.pageSize());
        int first = firstLong >= total ? total : (int) firstLong;
        int last = Math.min(total, Math.addExact(
                first, query.pageSize()));
        int pageCount = total == 0 ? 0
                : Math.toIntExact(Math.floorDiv(
                Math.addExact((long) total, query.pageSize() - 1L),
                query.pageSize()));
        return new Page(query.pageIndex(), query.pageSize(), total,
                pageCount, matches.subList(first, last));
    }

    private static boolean visible(
            BazaarProduct product,
            Query query
    ) {
        if (!query.statuses().contains(product.status())
                || !query.categoryId().isEmpty()
                && !query.categoryId().equals(product.categoryId())) {
            return false;
        }
        if (query.tokens().isEmpty()) {
            return true;
        }
        String document = (product.productId() + " "
                + product.registryId() + " " + product.categoryId())
                .toLowerCase(Locale.ROOT);
        return query.tokens().stream().allMatch(document::contains);
    }

    private static Comparator<Card> comparator(Sort sort) {
        Comparator<Card> identity = Comparator.comparing(card ->
                card.product().productId());
        Comparator<Card> selected = switch (sort) {
            case NAME -> identity;
            case INSTANT_BUY_LOWEST -> optionalPrice(
                    card -> card.summary().bestAskMinor()
                            .orElse(Long.MAX_VALUE), false);
            case INSTANT_SELL_HIGHEST -> optionalPrice(
                    card -> card.summary().bestBidMinor()
                            .orElse(Long.MIN_VALUE), true);
            case SPREAD_LOWEST -> optionalPrice(card ->
                    card.summary().spreadMinor()
                            .orElse(Long.MAX_VALUE), false);
            case VOLUME_HIGHEST -> Comparator.comparingLong((Card card) ->
                    card.summary().recentNotionalMinor()).reversed();
            case TREND_HIGHEST -> Comparator.comparingLong((Card card) ->
                    card.summary().trendBasisPoints()).reversed();
        };
        return selected.thenComparing(identity);
    }

    private static Comparator<Card> optionalPrice(
            java.util.function.ToLongFunction<Card> value,
            boolean reversed
    ) {
        Comparator<Card> comparator = Comparator.comparingLong(value);
        return reversed ? comparator.reversed() : comparator;
    }

    public record Query(
            String search,
            String categoryId,
            Set<BazaarProductStatus> statuses,
            Sort sort,
            int pageIndex,
            int pageSize,
            long nowMillis,
            long recentWindowMillis,
            int depthLevels
    ) {
        public Query {
            search = normalize(search, MAX_QUERY_LENGTH, "search");
            categoryId = normalize(categoryId, 96, "categoryId")
                    .toLowerCase(Locale.ROOT);
            statuses = Set.copyOf(Objects.requireNonNull(
                    statuses, "statuses"));
            sort = Objects.requireNonNull(sort, "sort");
            if (statuses.isEmpty() || pageIndex < 0
                    || pageIndex > MAX_PAGE_INDEX || pageSize <= 0
                    || pageSize > MAX_PAGE_SIZE || nowMillis < 0L
                    || recentWindowMillis <= 0L || depthLevels <= 0
                    || depthLevels
                    > BazaarMarketAnalytics.MAX_DEPTH_LEVELS) {
                throw new IllegalArgumentException(
                        "Bazaar product browse query is invalid");
            }
        }

        public static Query products(
                String search,
                Sort sort,
                int pageIndex,
                int pageSize,
                long nowMillis,
                long recentWindowMillis,
                int depthLevels
        ) {
            return new Query(search, "",
                    Set.of(BazaarProductStatus.ACTIVE,
                            BazaarProductStatus.HALTED),
                    sort, pageIndex, pageSize, nowMillis,
                    recentWindowMillis, depthLevels);
        }

        List<String> tokens() {
            return search.isEmpty() ? List.of()
                    : List.of(search.toLowerCase(Locale.ROOT)
                    .split("\\s+"));
        }
    }

    public record Card(
            BazaarProductVersionKey product,
            String registryId,
            String exactIdentity,
            String categoryId,
            int lotSize,
            long priceTickMinor,
            BazaarProductStatus status,
            BazaarMarketAnalytics.ProductSummary summary,
            boolean watched
    ) {
        public Card {
            product = Objects.requireNonNull(product, "product");
            registryId = normalize(registryId, 256, "registryId");
            exactIdentity = Objects.requireNonNull(
                    exactIdentity, "exactIdentity");
            categoryId = normalize(categoryId, 96, "categoryId");
            status = Objects.requireNonNull(status, "status");
            summary = Objects.requireNonNull(summary, "summary");
            if (!summary.product().equals(product)
                    || exactIdentity.length() > 256 || lotSize <= 0
                    || priceTickMinor <= 0L) {
                throw new IllegalArgumentException(
                        "Bazaar product browse card is invalid");
            }
        }

        static Card from(
                BazaarProduct product,
                BazaarMarketAnalytics.ProductSummary summary,
                boolean watched
        ) {
            return new Card(BazaarProductVersionKey.of(product),
                    product.registryId(), product.exactIdentity(),
                    product.categoryId(), product.lotSize(),
                    product.priceTickMinor(), product.status(), summary,
                    watched);
        }
    }

    public record Page(
            int pageIndex,
            int pageSize,
            int totalResults,
            int pageCount,
            List<Card> cards
    ) {
        public Page {
            cards = List.copyOf(cards);
            if (pageIndex < 0 || pageSize <= 0 || totalResults < 0
                    || pageCount < 0 || cards.size() > pageSize) {
                throw new IllegalArgumentException(
                        "Bazaar product browse page is invalid");
            }
        }
    }

    public enum Sort {
        NAME,
        INSTANT_BUY_LOWEST,
        INSTANT_SELL_HIGHEST,
        SPREAD_LOWEST,
        VOLUME_HIGHEST,
        TREND_HIGHEST
    }

    private static String normalize(
            String value,
            int maximum,
            String label
    ) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.length() > maximum) {
            throw new IllegalArgumentException(
                    "Bazaar product browse " + label + " is invalid");
        }
        return normalized;
    }
}
